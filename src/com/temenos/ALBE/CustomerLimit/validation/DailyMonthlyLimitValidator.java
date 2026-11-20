package com.temenos.ALBE.CustomerLimit.validation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO.LimitBreach;
import com.temenos.ALBE.CustomerLimit.override.CustomerLimitOverride;
import com.temenos.ALBE.CustomerLimit.service.CustomerLimitReader;
import com.temenos.ALBE.CustomerLimit.service.CustomerLimitReader.Limits;
import com.temenos.ALBE.CustomerLimit.service.CustomerLimitUtilizationUpdater;
import com.temenos.ALBE.CustomerLimit.service.TxnDetailsLimitRecorder;
import com.temenos.api.TStructure;
import com.temenos.api.TValidationResponse;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;
import com.temenos.t24.api.complex.aa.activityhook.ArrangementContext;
import com.temenos.t24.api.complex.aa.activityhook.TransactionData;
import com.temenos.t24.api.hook.arrangement.ActivityLifecycle;
import com.temenos.t24.api.records.aaaccountdetails.AaAccountDetailsRecord;
import com.temenos.t24.api.records.aaarrangement.AaArrangementRecord;
import com.temenos.t24.api.records.aaarrangementactivity.AaArrangementActivityRecord;
import com.temenos.t24.api.records.aaproductcatalog.AaProductCatalogRecord;
import com.temenos.t24.api.records.ebalbeutilisedlimit.EbAlbeUtilisedLimitRecord;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;

/*-----------------------------------------------------------------------------
 * Attached as : ACTIVITY.API
 * Attached to : ACCOUNTS-CREDIT-ARRANGEMENT / ACCOUNTS-DEBIT-ARRANGEMENT
 * Description : Daily and monthly limit verification routine (HLSD 7.2.1 - 2).
 *
 *   validateRecord (input, activity status UNAUTH):
 *     1. Reads the customer's limits from EB.ALBE.CIF.LIMIT <customer id>.
 *        No record => override "Customer limit not defined for Customer <id>", no further checks.
 *     2. Daily   : EB.ALBE.UTILISED.LIMIT <customer>-YYYYMMDD
 *                  UTILISED.CREDIT/DEBIT.LIMIT + txn amount
 *                  vs DAILY.CREDIT/DEBIT.LIMIT.
 *     3. Monthly : EB.ALBE.UTILISED.LIMIT <customer>-YYYYMM
 *                  UTILISED.MIGRATED.CREDIT/DEBIT.LIMIT + UTILISED.CREDIT/DEBIT.LIMIT
 *                  + txn amount vs MONTHLY.CREDIT/DEBIT.LIMIT.
 *     4. Breach  : override "Daily/Monthly credit/debit exceed for Customer <id> by <difference>".
 *                  Both overrides are raised if both limits are breached.
 *     5. Blank limit field : override "Customer limit not defined for Customer <id>"
 *                  (raised once even if several fields are blank).
 *
 *   postCoreTableUpdate (utilisation is counted when the transaction is initiated):
 *     UNAUTH   : adds the txn amount to the daily and monthly utilisation and writes
 *                EB.ALBE.TXN.DETAILS.LIMIT <customer>-<YYYYMMDD>-<AAA id>.
 *                On re-input of the same AAA the earlier amount is subtracted first,
 *                so an amended transaction is counted once.
 *     DELETE   : unauthorised transaction rejected / deleted => amount reversed.
 *     AUTH     : no change (already counted at input).
 *     AUTH-REV : authorised transaction reversed => amount reversed from the
 *                original day and month.
 *     Reversals use EB.ALBE.TXN.DETAILS.LIMIT to find the original date and amount,
 *     then delete it. Accepted overrides are counted and reversed like any other.
 *
 *   Dates use the server date. Exchange rate is 1 (LCY only).
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class DailyMonthlyLimitValidator extends ActivityLifecycle {

    private static final String L3API = "L3API";
    private static final Logger logger = LoggerFactory.getLogger(L3API);
    private static final String LOG_PREFIX = "DailyMonthlyLimitValidator:";

    // AA activity statuses handled by this routine
    private static final String STATUS_UNAUTH = "UNAUTH";
    private static final String STATUS_AUTH = "AUTH";
    private static final String STATUS_DELETE = "DELETE";
    private static final String STATUS_AUTH_REV = "AUTH-REV";

    // Activity name fragments used to decide credit / debit
    private static final String CREDIT = "CREDIT";
    private static final String DEBIT = "DEBIT";

    /** Exchange rate is fixed at 1 until currency conversion is confirmed. */
    private static final BigDecimal EXCHANGE_RATE = BigDecimal.ONE;

    /** HLSD override texts (the EB.OVERRIDE records carry the same text with & placeholders). */
    public static final String MSG_DAILY_EXCEEDED = "Daily credit/debit exceed for Customer %s by %s";
    public static final String MSG_MONTHLY_EXCEEDED = "Monthly credit/debit exceed for Customer %s by %s";

    /**
     * Input validation. Runs only for UNAUTH; checks the daily and monthly limits and
     * raises override(s) on TXN.AMOUNT: limit not defined, or limit breached.
     */
    @Override
    public TValidationResponse validateRecord(AaAccountDetailsRecord accountDetailRecord,
            AaArrangementActivityRecord arrangementActivityRecord, ArrangementContext arrangementContext,
            AaArrangementRecord arrangementRecord, AaArrangementActivityRecord masterActivityRecord,
            TStructure productPropertyRecord, AaProductCatalogRecord productRecord, TStructure currRecord) {

        String activityStatus = "";
        long startTime = System.currentTimeMillis();
        try {
            activityStatus = arrangementContext.getActivityStatus();
            logger.info(LOG_PREFIX + "validateRecord START status=" + activityStatus);
            logActivity("validateRecord", arrangementActivityRecord, arrangementContext, arrangementRecord);

            // Validation is only needed when the activity is input; AUTH / REVERSE are handled elsewhere.
            if (!STATUS_UNAUTH.equalsIgnoreCase(activityStatus)) {
                logger.info(LOG_PREFIX + "validateRecord SKIP status=" + activityStatus + " (not UNAUTH)");
                return arrangementActivityRecord.getValidationResponse();
            }

            CustomerLimitDTO request = buildRequest(arrangementActivityRecord, arrangementRecord, arrangementContext);
            if (request == null) {
                logger.info(LOG_PREFIX + "validateRecord SKIP activity is neither credit nor debit");
                return arrangementActivityRecord.getValidationResponse();
            }
            logger.info(LOG_PREFIX + "validateRecord request=" + request);

            validate(request);
            logger.info(LOG_PREFIX + "validateRecord result=" + request);

            // No EB.ALBE.CIF.LIMIT record: "limit not defined" override; nothing else can be checked.
            if (request.isLimitMissing()) {
                logger.info(LOG_PREFIX + "validateRecord OVERRIDE id=" + CustomerLimitOverride.LIMIT_NOT_DEFINED
                        + " customer=" + request.getCustomerId() + " aaa=" + request.getTransactionId()
                        + " reason=no EB.ALBE.CIF.LIMIT record");
                CustomerLimitOverride.raise(arrangementActivityRecord.getTxnAmount(),
                        CustomerLimitOverride.LIMIT_NOT_DEFINED, request.getCustomerId());
                return arrangementActivityRecord.getValidationResponse();
            }

            // A blank limit field: one "limit not defined" override, whichever fields are blank.
            if (request.isLimitNotDefined()) {
                logger.info(LOG_PREFIX + "validateRecord OVERRIDE id=" + CustomerLimitOverride.LIMIT_NOT_DEFINED
                        + " customer=" + request.getCustomerId() + " blankFields=" + request.getUndefinedLimits());
                CustomerLimitOverride.raise(arrangementActivityRecord.getTxnAmount(),
                        CustomerLimitOverride.LIMIT_NOT_DEFINED, request.getCustomerId());
            }

            // One override per breached limit (daily and/or monthly).
            for (LimitBreach breach : request.getBreaches()) {
                logger.info(LOG_PREFIX + "validateRecord OVERRIDE id=" + breach.getOverrideId()
                        + " customer=" + request.getCustomerId() + " difference="
                        + breach.getDifference().toPlainString() + " message=" + breach.getMessage());
                CustomerLimitOverride.raise(arrangementActivityRecord.getTxnAmount(), breach.getOverrideId(),
                        request.getCustomerId(), breach.getDifference().toPlainString());
            }

            logger.info(LOG_PREFIX + "validateRecord END customer=" + request.getCustomerId()
                    + " aaa=" + request.getTransactionId() + " breaches=" + request.getBreaches().size()
                    + " limitNotDefined=" + request.isLimitNotDefined() + " elapsedMs="
                    + (System.currentTimeMillis() - startTime));

        } catch (Exception e) {
            logger.error(LOG_PREFIX + "validateRecord ERROR status=" + activityStatus + " " + e.getMessage());
        }

        return arrangementActivityRecord.getValidationResponse();
    }

    /**
     * Utilisation maintenance after the core tables are updated.
     * UNAUTH counts the transaction; DELETE and AUTH-REV reverse it; AUTH changes nothing.
     */
    @Override
    public void postCoreTableUpdate(AaAccountDetailsRecord accountDetailRecord,
            AaArrangementActivityRecord arrangementActivityRecord, ArrangementContext arrangementContext,
            AaArrangementRecord arrangementRecord, AaArrangementActivityRecord masterActivityRecord,
            TStructure productPropertyRecord, AaProductCatalogRecord productRecord, TStructure currRecord,
            List<TransactionData> transactionData, List<TStructure> transactionRecord) {

        String activityStatus = "";
        long startTime = System.currentTimeMillis();
        try {
            activityStatus = arrangementContext.getActivityStatus();
            logger.info(LOG_PREFIX + "postCoreTableUpdate START status=" + activityStatus);
            logActivity("postCoreTableUpdate", arrangementActivityRecord, arrangementContext, arrangementRecord);

            boolean isInput = STATUS_UNAUTH.equalsIgnoreCase(activityStatus);
            boolean isDelete = STATUS_DELETE.equalsIgnoreCase(activityStatus);
            boolean isReversal = STATUS_AUTH_REV.equalsIgnoreCase(activityStatus);

            if (STATUS_AUTH.equalsIgnoreCase(activityStatus)) {
                logger.info(LOG_PREFIX + "postCoreTableUpdate SKIP AUTH (utilisation already counted at input)");
                return;
            }
            if (!isInput && !isDelete && !isReversal) {
                logger.info(LOG_PREFIX + "postCoreTableUpdate SKIP status=" + activityStatus
                        + " (only UNAUTH / DELETE / AUTH-REV change utilisation)");
                return;
            }

            CustomerLimitDTO request = buildRequest(arrangementActivityRecord, arrangementRecord, arrangementContext);
            if (request == null) {
                logger.info(LOG_PREFIX + "postCoreTableUpdate SKIP activity is neither credit nor debit");
                return;
            }

            logger.info(LOG_PREFIX + "postCoreTableUpdate request=" + request + " action="
                    + (isInput ? "COUNT" : isDelete ? "REVERSE (rejected input)" : "REVERSE (reversal)"));

            CustomerLimitRepository repository = new CustomerLimitRepository(this);
            CustomerLimitUtilizationUpdater updater = new CustomerLimitUtilizationUpdater(repository);
            TxnDetailsLimitRecorder recorder = new TxnDetailsLimitRecorder(repository);

            // Whatever the status, first take out anything already counted for this AAA:
            //   UNAUTH   : earlier input of the same AAA (amend + re-input)
            //   DELETE   : the input being rejected / deleted
            //   AUTH-REV : the authorised transaction being reversed
            int reversed = reverseCounted(request, updater, recorder);

            if (isInput) {
                // Transaction initiated: count it and keep its details for a later reversal.
                updater.update(request);
                recorder.record(request, getAccountNo(arrangementRecord),
                        arrangementActivityRecord.getEffectiveDate().getValue());
                logger.info(LOG_PREFIX + "postCoreTableUpdate END UNAUTH counted customer=" + request.getCustomerId()
                        + " aaa=" + request.getTransactionId() + " earlierInputReversed=" + reversed
                        + " elapsedMs=" + (System.currentTimeMillis() - startTime));
                return;
            }

            if (isReversal && reversed == 0) {
                // Not recorded (e.g. counted before this routine went live): reverse on today's records.
                logger.info(LOG_PREFIX + "postCoreTableUpdate REVERSE no EB.ALBE.TXN.DETAILS.LIMIT for aaa="
                        + request.getTransactionId() + ", reversing on server date");
                updater.reverse(request);
                reversed = 1;
            }

            if (isDelete && reversed == 0) {
                logger.info(LOG_PREFIX + "postCoreTableUpdate DELETE nothing was counted for aaa="
                        + request.getTransactionId());
            }
            logger.info(LOG_PREFIX + "postCoreTableUpdate END " + activityStatus + " customer="
                    + request.getCustomerId() + " aaa=" + request.getTransactionId() + " reversed=" + reversed
                    + " elapsedMs=" + (System.currentTimeMillis() - startTime));

        } catch (Exception e) {
            logger.error(LOG_PREFIX + "postCoreTableUpdate ERROR status=" + activityStatus + " " + e.getMessage());
        }
    }

    /**
     * Reverses every amount already counted for this customer + AAA (found through
     * EB.ALBE.TXN.DETAILS.LIMIT) on its original day and month, and deletes those records.
     * @return number of records reversed (0 if nothing was counted yet)
     */
    private int reverseCounted(CustomerLimitDTO request, CustomerLimitUtilizationUpdater updater,
            TxnDetailsLimitRecorder recorder) {
        logger.info(LOG_PREFIX + "reverseCounted START customer=" + request.getCustomerId() + " aaa="
                + request.getTransactionId());
        List<CustomerLimitDTO> originals =
                recorder.findOriginal(request.getCustomerId(), request.getTransactionId());
        logger.info(LOG_PREFIX + "reverseCounted found=" + originals.size() + " record(s) to reverse");
        for (CustomerLimitDTO original : originals) {
            logger.info(LOG_PREFIX + "reverseCounted aaa=" + original.getTransactionId() + " originalDate="
                    + original.getTransactionDate() + " credit=" + original.getCreditAmount() + " debit="
                    + original.getDebitAmount());
            updater.reverse(original);
            recorder.delete(original);
        }
        logger.info(LOG_PREFIX + "reverseCounted END reversed=" + originals.size());
        return originals.size();
    }

    /**
     * Daily then monthly check. Every breach / blank limit is recorded on the request;
     * a missing EB.ALBE.CIF.LIMIT record sets limitMissing.
     */
    public void validate(CustomerLimitDTO request) {
        logger.info(LOG_PREFIX + "validate START customer=" + request.getCustomerId() + " aaa="
                + request.getTransactionId());
        CustomerLimitRepository repository = new CustomerLimitRepository(this);

        Limits limits = new CustomerLimitReader(repository).getCustomerLimits(request.getCustomerId());
        if (limits == null) {
            // No EB.ALBE.CIF.LIMIT record: the caller raises the "limit not defined" override.
            logger.info(LOG_PREFIX + "validate END customer=" + request.getCustomerId()
                    + " no EB.ALBE.CIF.LIMIT record, checks not possible");
            request.setLimitMissing(true);
            return;
        }
        logger.info(LOG_PREFIX + "validate customer=" + request.getCustomerId() + " limitsFrom=" + limits.source
                + " credit=" + request.getCreditAmount() + " debit=" + request.getDebitAmount());

        // On re-input of an amended transaction its earlier input is already in utilisation;
        // leave it out so the transaction is not checked against itself.
        List<CustomerLimitDTO> earlierInputs =
                new TxnDetailsLimitRecorder(repository).findOriginal(request.getCustomerId(), request.getTransactionId());
        logger.info(LOG_PREFIX + "validate earlierInputs=" + earlierInputs.size()
                + (earlierInputs.isEmpty() ? " (first input)" : " (re-input of an amended transaction)"));

        validateDailyLimit(repository, request, limits, earlierInputs);
        validateMonthlyLimit(repository, request, limits, earlierInputs);

        logger.info(LOG_PREFIX + "validate END customer=" + request.getCustomerId() + " breaches="
                + request.getBreaches().size() + " blankLimits=" + request.getUndefinedLimits());
    }

    /**
     * Credit (or debit) amount of this AAA's earlier input counted on the same day
     * (sameMonth=false) or in the same month (sameMonth=true) as the current request.
     */
    private BigDecimal earlierAmount(CustomerLimitDTO request, List<CustomerLimitDTO> earlierInputs,
            boolean credit, boolean sameMonth) {
        BigDecimal total = BigDecimal.ZERO;
        for (CustomerLimitDTO earlier : earlierInputs) {
            boolean samePeriod = sameMonth
                    ? earlier.getTransactionDate().getYear() == request.getTransactionDate().getYear()
                            && earlier.getTransactionDate().getMonthValue() == request.getTransactionDate().getMonthValue()
                    : earlier.getTransactionDate().equals(request.getTransactionDate());
            if (samePeriod) {
                total = total.add(credit ? earlier.getCreditAmount() : earlier.getDebitAmount());
            }
        }
        if (total.signum() > 0) {
            logger.info(LOG_PREFIX + "earlierAmount aaa=" + request.getTransactionId() + " "
                    + (credit ? "credit" : "debit") + " " + (sameMonth ? "month" : "day") + " excluded=" + total);
        }
        return total;
    }

    /** Daily: today's utilised credit/debit + txn amount against the daily credit/debit limit. */
    private void validateDailyLimit(CustomerLimitRepository repository, CustomerLimitDTO request, Limits limits,
            List<CustomerLimitDTO> earlierInputs) {
        String dailyId = CustomerLimitUtilizationUpdater.dailyRecordId(request);
        EbAlbeUtilisedLimitRecord dailyRec = repository.readUtilisedLimit(dailyId);

        if (isCredit(request)) {
            BigDecimal limit = limits.dailyCreditLimit;
            BigDecimal utilised = dailyRec == null ? BigDecimal.ZERO
                    : CustomerLimitUtilizationUpdater.toAmount(dailyRec.getUtilisedCreditLimit().getValue());
            utilised = utilised.subtract(earlierAmount(request, earlierInputs, true, false)).max(BigDecimal.ZERO);
            logger.info(LOG_PREFIX + "validateDailyLimit CREDIT record=" + dailyId + " utilised=" + utilised
                    + " txn=" + request.getCreditAmount() + " limit=" + limit);
            checkLimit(request, "DAILY.CREDIT.LIMIT", limit, utilised, request.getCreditAmount(),
                    CustomerLimitOverride.DAILY_LIMIT_EXCEED, MSG_DAILY_EXCEEDED);
        } else {
            BigDecimal limit = limits.dailyDebitLimit;
            BigDecimal utilised = dailyRec == null ? BigDecimal.ZERO
                    : CustomerLimitUtilizationUpdater.toAmount(dailyRec.getUtilisedDebitLimit().getValue());
            utilised = utilised.subtract(earlierAmount(request, earlierInputs, false, false)).max(BigDecimal.ZERO);
            logger.info(LOG_PREFIX + "validateDailyLimit DEBIT record=" + dailyId + " utilised=" + utilised
                    + " txn=" + request.getDebitAmount() + " limit=" + limit);
            checkLimit(request, "DAILY.DEBIT.LIMIT", limit, utilised, request.getDebitAmount(),
                    CustomerLimitOverride.DAILY_LIMIT_EXCEED, MSG_DAILY_EXCEEDED);
        }
    }

    /** Monthly: migrated + this month's utilised credit/debit + txn amount against the monthly limit. */
    private void validateMonthlyLimit(CustomerLimitRepository repository, CustomerLimitDTO request, Limits limits,
            List<CustomerLimitDTO> earlierInputs) {
        String monthlyId = CustomerLimitUtilizationUpdater.monthlyRecordId(request);
        EbAlbeUtilisedLimitRecord monthlyRec = repository.readUtilisedLimit(monthlyId);

        if (isCredit(request)) {
            BigDecimal limit = limits.monthlyCreditLimit;
            BigDecimal migrated = monthlyRec == null ? BigDecimal.ZERO
                    : CustomerLimitUtilizationUpdater.toAmount(monthlyRec.getUtilisedMigratedCreditLimit().getValue());
            BigDecimal monthly = monthlyRec == null ? BigDecimal.ZERO
                    : CustomerLimitUtilizationUpdater.toAmount(monthlyRec.getUtilisedCreditLimit().getValue());
            monthly = monthly.subtract(earlierAmount(request, earlierInputs, true, true)).max(BigDecimal.ZERO);
            logger.info(LOG_PREFIX + "validateMonthlyLimit CREDIT record=" + monthlyId + " migrated=" + migrated
                    + " utilised=" + monthly + " txn=" + request.getCreditAmount() + " limit=" + limit);
            checkLimit(request, "MONTHLY.CREDIT.LIMIT", limit, migrated.add(monthly), request.getCreditAmount(),
                    CustomerLimitOverride.MONTHLY_LIMIT_EXCEED, MSG_MONTHLY_EXCEEDED);
        } else {
            BigDecimal limit = limits.monthlyDebitLimit;
            BigDecimal migrated = monthlyRec == null ? BigDecimal.ZERO
                    : CustomerLimitUtilizationUpdater.toAmount(monthlyRec.getUtilisedMigratedDebitLimit().getValue());
            BigDecimal monthly = monthlyRec == null ? BigDecimal.ZERO
                    : CustomerLimitUtilizationUpdater.toAmount(monthlyRec.getUtilisedDebitLimit().getValue());
            monthly = monthly.subtract(earlierAmount(request, earlierInputs, false, true)).max(BigDecimal.ZERO);
            logger.info(LOG_PREFIX + "validateMonthlyLimit DEBIT record=" + monthlyId + " migrated=" + migrated
                    + " utilised=" + monthly + " txn=" + request.getDebitAmount() + " limit=" + limit);
            checkLimit(request, "MONTHLY.DEBIT.LIMIT", limit, migrated.add(monthly), request.getDebitAmount(),
                    CustomerLimitOverride.MONTHLY_LIMIT_EXCEED, MSG_MONTHLY_EXCEEDED);
        }
    }

    /**
     * Breach when utilised + txn amount is above the limit; the difference is the excess.
     * A blank (null) limit is recorded as "limit not defined" and raised as an override by the caller.
     */
    private void checkLimit(CustomerLimitDTO request, String limitField, BigDecimal limit, BigDecimal utilised,
            BigDecimal transactionAmount, String overrideId, String messageFormat) {
        if (limit == null) {
            logger.info(LOG_PREFIX + "checkLimit NOT DEFINED " + limitField + " is blank for customer="
                    + request.getCustomerId());
            request.addUndefinedLimit(limitField);
            return;
        }
        BigDecimal projected = utilised.add(transactionAmount);
        if (projected.compareTo(limit) > 0) {
            BigDecimal difference = projected.subtract(limit);
            logger.info(LOG_PREFIX + "checkLimit BREACH " + overrideId + " projected=" + projected
                    + " limit=" + limit + " difference=" + difference);
            request.addBreach(overrideId,
                    String.format(messageFormat, request.getCustomerId(), difference.toPlainString()), difference);
        } else {
            logger.info(LOG_PREFIX + "checkLimit OK " + overrideId + " projected=" + projected + " limit=" + limit);
        }
    }

    /**
     * Builds the request from the activity: customer, AAA id, server date and the
     * credit or debit amount. Returns null if the activity is neither credit nor debit.
     */
    private CustomerLimitDTO buildRequest(AaArrangementActivityRecord arrangementActivityRecord,
            AaArrangementRecord arrangementRecord, ArrangementContext arrangementContext) {

        String activity = arrangementActivityRecord.getActivity().getValue();
        BigDecimal amount = CustomerLimitUtilizationUpdater
                .toAmount(arrangementActivityRecord.getTxnAmount().getValue()).multiply(EXCHANGE_RATE);

        CustomerLimitDTO request = new CustomerLimitDTO();
        request.setCustomerId(arrangementRecord.getCustomer().get(0).getCustomer().getValue());
        request.setTransactionId(getAaaId(arrangementContext, arrangementActivityRecord));
        request.setTransactionDate(LocalDate.now()); // server date, as agreed

        if (activity != null && activity.contains(CREDIT)) {
            request.setCreditAmount(amount);
        } else if (activity != null && activity.contains(DEBIT)) {
            request.setDebitAmount(amount);
        } else {
            logger.info(LOG_PREFIX + "buildRequest activity=" + activity + " is not credit/debit");
            return null;
        }

        logger.info(LOG_PREFIX + "buildRequest activity=" + activity + " customer=" + request.getCustomerId()
                + " aaa=" + request.getTransactionId() + " amount=" + amount + " date="
                + request.getTransactionDate());
        return request;
    }

    /** AAA id of the current activity; arrangement id only if the context does not supply it. */
    private String getAaaId(ArrangementContext arrangementContext,
            AaArrangementActivityRecord arrangementActivityRecord) {
        String aaaId = arrangementContext.getArrangementActivityId();
        if (aaaId != null && !aaaId.trim().isEmpty()) {
            logger.info(LOG_PREFIX + "getAaaId aaa=" + aaaId.trim());
            return aaaId.trim();
        }
        logger.error(LOG_PREFIX + "getAaaId AAA id not available, using arrangement id");
        return arrangementActivityRecord.getArrangement().getValue();
    }

    /** First linked application id of the arrangement (the account number). */
    private String getAccountNo(AaArrangementRecord arrangementRecord) {
        try {
            String accountNo = arrangementRecord.getLinkedAppl().get(0).getLinkedApplId().getValue();
            logger.info(LOG_PREFIX + "getAccountNo account=" + accountNo);
            return accountNo;
        } catch (Exception e) {
            logger.info(LOG_PREFIX + "getAccountNo linked account not found " + e.getMessage());
            return "";
        }
    }

    /** Logs the raw activity fields received from T24, for tracing. */
    private void logActivity(String hook, AaArrangementActivityRecord arrangementActivityRecord,
            ArrangementContext arrangementContext, AaArrangementRecord arrangementRecord) {
        try {
            logger.info(LOG_PREFIX + hook + " activity=" + arrangementActivityRecord.getActivity().getValue()
                    + " arrangement=" + arrangementActivityRecord.getArrangement().getValue()
                    + " aaa=" + arrangementContext.getArrangementActivityId()
                    + " customer=" + arrangementRecord.getCustomer().get(0).getCustomer().getValue()
                    + " txnAmount=" + arrangementActivityRecord.getTxnAmount().getValue()
                    + " effectiveDate=" + arrangementActivityRecord.getEffectiveDate().getValue());
        } catch (Exception e) {
            logger.info(LOG_PREFIX + hook + " unable to log activity fields " + e.getMessage());
        }
    }

    private boolean isCredit(CustomerLimitDTO request) {
        return request.getCreditAmount().signum() > 0;
    }
}
