package com.temenos.ALBE.CustomerLimit.service.activity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.model.Limits;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.service.CustomerLimitReader;
import com.temenos.ALBE.CustomerLimit.service.CustomerLimitUtilizationUpdater;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.ALBE.CustomerLimit.validation.CustomerLimitRequestValidator;
import com.temenos.t24.api.records.ebalbeutilisedlimit.EbAlbeUtilisedLimitRecord;

/*-----------------------------------------------------------------------------
 * Description : The business logic of the customer limit check (HLSD 7.2.1 - 2 and 3), called by
 *               the single routine CustomerLimitActivityController. Two entry points:
 *
 *   processInput (activity UNAUTH, FT input / amendment)
 *     1. EB.ALBE.CIF.LIMITS of the customer (read once)
 *          no record          -> override "limit not defined", still counted (amount as it is)
 *          CURRENCY blank     -> ERROR, transaction blocked, not counted
 *     2. amount in the limit currency (CurrencyConversionService, D44) - cannot convert -> ERROR, blocked
 *     3. earlier input of the same transaction: EB.ALBE.TXN.DETAILS.LIMIT by its key
 *        <customer>-<date>-<FT/AAA>-<arrangement> (same key = same day and month)
 *     4. daily record <customer>-YYYYMMDD and monthly record <customer>-YYYYMM (read once)
 *     5. daily:   used today - earlier input + this                 > daily limit   -> override
 *        monthly: migrated + used this month - earlier input + this  > monthly limit -> override
 *        blank limit -> override "limit not defined"
 *     6. credit only: maximum CIF balance (MaximumCifLimitService) -> override; no CUSTOMER.ACCOUNT -> ERROR
 *     7. not blocked -> count: each record = used - earlier input + this (written once), and
 *        EB.ALBE.TXN.DETAILS.LIMIT written (replaces the earlier input)
 *     The count is written in the same commit as the FT: a rejected override or a cancelled FT
 *     is rolled back by T24 together with it (TQ22).
 *
 *   reverse (activity DELETE / AUTH-REV)
 *     EB.ALBE.TXN.DETAILS.LIMIT by the key; not found -> looked up by customer + FT + arrangement
 *     (reversal on a later day). Found -> its amount is taken off the day and month it was counted
 *     on and the record is deleted. Not found -> nothing is taken back (never a guess).
 *
 *   All amounts are in the limit currency, 2 decimals; totals never go below 0.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 22-Nov-2026   Development      Initial Version
 * 23-Nov-2026   Development      Arrangement id in the key; own-account transfers counted on both sides (FQ6)
 * 26-Nov-2026   Development      Clean rewrite (D47): one input path, one take-back path; old routines,
 *                                DailyMonthlyLimitService and UtilisationRecords removed
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitEngine {
    private static final String CLASS_NAME = "CustomerLimitEngine";

    private final CustomerLimitRepository repository;
    private final CustomerLimitUtilizationUpdater updater;
    private final TxnDetailsLimitRecorder recorder;

    public CustomerLimitEngine(CustomerLimitRepository repository) {
        this.repository = repository;
        this.updater = new CustomerLimitUtilizationUpdater(repository);
        this.recorder = new TxnDetailsLimitRecorder(repository);
    }

    /**
     * Input (UNAUTH): check, then count. The request is changed in place: its amount is converted to
     * the limit currency and it carries every breach / flag (for the overrides) and the notes (for the
     * RESULT log line).
     * @return the checked request; null if the transaction info is not valid (nothing checked or counted)
     */
    public CustomerLimitDTO processInput(CustomerLimitDTO request, String accountNo, String effectiveDate) {
        if (!CustomerLimitRequestValidator.isValid(request)) {
            return null;
        }
        String customerId = request.getCustomerId();

        // 1. + 2. limits and amount in the limit currency
        Limits limits = new CustomerLimitReader(repository).getCustomerLimits(customerId);
        if (limits == null) {
            request.setLimitMissing(true);
            request.addNote("no EB.ALBE.CIF.LIMITS record for customer " + customerId + " -> not checked, counted");
            scaleAmount(request);
        } else if (ValidationUtil.isBlank(limits.ccy)) {
            LogUtil.info(CLASS_NAME, "processInput", "NO CCY", "customer=" + customerId
                    + " EB.ALBE.CIF.LIMITS CURRENCY is blank, raising " + Constants.ERR_LIMIT_CCY_MISSING);
            request.addNote("BLOCKED: EB.ALBE.CIF.LIMITS CURRENCY is blank for customer " + customerId);
            request.setLimitCcyMissing(true);
            return request;
        } else if (!new CurrencyConversionService(repository).convertRequest(request, limits.ccy)) {
            if (!request.isBlocked()) {
                // local currency not readable (technical): override, no check, no count
                request.addNote("amount not converted -> not checked, not counted");
            }
            return request;
        }

        // 3. earlier input of the same transaction (re-input of an amended FT)
        CustomerLimitDTO earlier = recorder.findCounted(request);
        if (earlier != null) {
            request.addNote("re-input: earlier input " + ValidationUtil.formatAmount(earlier.getAmount())
                    + " is replaced");
        }
        BigDecimal earlierCredit = earlier == null ? BigDecimal.ZERO : earlier.getCreditAmount();
        BigDecimal earlierDebit = earlier == null ? BigDecimal.ZERO : earlier.getDebitAmount();

        // 4. utilisation records, read once
        String dailyId = CustomerLimitUtilizationUpdater.dailyRecordId(request);
        String monthlyId = CustomerLimitUtilizationUpdater.monthlyRecordId(request);
        EbAlbeUtilisedLimitRecord dailyRec = repository.readUtilisedLimit(dailyId);
        EbAlbeUtilisedLimitRecord monthlyRec = repository.readUtilisedLimit(monthlyId);

        // 5. + 6. checks
        if (limits != null) {
            checkDaily(request, limits, dailyId, dailyRec, request.isCredit() ? earlierCredit : earlierDebit);
            checkMonthly(request, limits, monthlyId, monthlyRec, request.isCredit() ? earlierCredit : earlierDebit);
            if (request.isCredit()) {
                new MaximumCifLimitService(repository).check(request, limits);
            }
        }
        if (request.isBlocked()) {
            request.addNote("not counted: the transaction is blocked");
            LogUtil.info(CLASS_NAME, "processInput", "END", request.logKey() + " blocked, not counted");
            return request;
        }

        // 7. count: used - earlier input + this, each record written once
        BigDecimal credit = request.getCreditAmount().subtract(earlierCredit);
        BigDecimal debit = request.getDebitAmount().subtract(earlierDebit);
        updater.add(request, "daily", dailyId, dailyRec, credit, debit);
        updater.add(request, "monthly", monthlyId, monthlyRec, credit, debit);
        recorder.record(request, accountNo, effectiveDate);
        LogUtil.info(CLASS_NAME, "processInput", "END", request.logKey() + " breaches="
                + request.getBreaches().size() + " counted");
        return request;
    }

    /**
     * DELETE / AUTH-REV: takes back what EB.ALBE.TXN.DETAILS.LIMIT shows was counted for this
     * transaction, on the day and month it was counted on, and deletes that record.
     * @return 1 when taken back, 0 when nothing was counted for it
     */
    public int reverse(CustomerLimitDTO request) {
        if (!CustomerLimitRequestValidator.isValid(request)) {
            return 0;
        }
        CustomerLimitDTO counted = recorder.findCounted(request);
        if (counted == null) {
            LocalDate countedOn = findCountedDate(request);
            if (countedOn != null) {
                request.setTransactionDate(countedOn);
                counted = recorder.findCounted(request);
            }
        }
        if (counted == null) {
            request.addNote("nothing was counted for this transaction -> nothing taken back");
            LogUtil.info(CLASS_NAME, "reverse", "NOT FOUND", request.logKey() + " nothing counted");
            return 0;
        }
        String dailyId = CustomerLimitUtilizationUpdater.dailyRecordId(counted);
        String monthlyId = CustomerLimitUtilizationUpdater.monthlyRecordId(counted);
        BigDecimal credit = counted.getCreditAmount().negate();
        BigDecimal debit = counted.getDebitAmount().negate();
        updater.add(request, "daily", dailyId, repository.readUtilisedLimit(dailyId), credit, debit);
        updater.add(request, "monthly", monthlyId, repository.readUtilisedLimit(monthlyId), credit, debit);
        recorder.delete(counted);
        LogUtil.info(CLASS_NAME, "reverse", "END", request.logKey() + " taken back "
                + counted.getAmount() + " counted on " + counted.getTransactionDate());
        return 1;
    }

    /**
     * Counted transaction key: the root reference (e.g. FT26091ABCDE behind an activity, else the
     * AAA id). With the customer, the day and the arrangement it is the EB.ALBE.TXN.DETAILS.LIMIT id.
     */
    public static String transactionKey(String reference) {
        return ValidationUtil.rootReference(reference);
    }

    /** Daily: used today (without this transaction's earlier input) + this, against the daily limit. */
    private void checkDaily(CustomerLimitDTO request, Limits limits, String dailyId,
            EbAlbeUtilisedLimitRecord dailyRec, BigDecimal earlier) {
        boolean credit = request.isCredit();
        BigDecimal used = CustomerLimitUtilizationUpdater.utilisedAmount(dailyRec, credit).subtract(earlier)
                .max(BigDecimal.ZERO);
        checkLimit(request, "daily " + direction(credit) + " (" + dailyId + ")",
                credit ? Constants.KEY_DAILY_CREDIT_LIMIT : Constants.KEY_DAILY_DEBIT_LIMIT,
                credit ? limits.dailyCreditLimit : limits.dailyDebitLimit, used,
                credit ? Constants.OVR_DAILY_CREDIT_LIMIT_EXCEED : Constants.OVR_DAILY_DEBIT_LIMIT_EXCEED);
    }

    /** Monthly: migrated + used this month (without the earlier input) + this, against the monthly limit. */
    private void checkMonthly(CustomerLimitDTO request, Limits limits, String monthlyId,
            EbAlbeUtilisedLimitRecord monthlyRec, BigDecimal earlier) {
        boolean credit = request.isCredit();
        BigDecimal migrated = CustomerLimitUtilizationUpdater.migratedAmount(monthlyRec, credit);
        BigDecimal used = CustomerLimitUtilizationUpdater.utilisedAmount(monthlyRec, credit).subtract(earlier)
                .max(BigDecimal.ZERO);
        checkLimit(request, "monthly " + direction(credit) + " (" + monthlyId
                + (migrated.signum() > 0 ? ", migrated " + ValidationUtil.formatAmount(migrated) : "") + ")",
                credit ? Constants.KEY_MONTHLY_CREDIT_LIMIT : Constants.KEY_MONTHLY_DEBIT_LIMIT,
                credit ? limits.monthlyCreditLimit : limits.monthlyDebitLimit, migrated.add(used),
                credit ? Constants.OVR_MONTHLY_CREDIT_LIMIT_EXCEED : Constants.OVR_MONTHLY_DEBIT_LIMIT_EXCEED);
    }

    /** Breach when used + this is ABOVE the limit (equal is allowed); blank limit = "not defined". */
    private void checkLimit(CustomerLimitDTO request, String label, String limitField, BigDecimal limit,
            BigDecimal used, String overrideId) {
        if (limit == null) {
            request.addNote(label + ": " + limitField + " is blank -> not checked");
            request.addUndefinedLimit(limitField);
            return;
        }
        BigDecimal total = used.add(request.getAmount());
        String calc = label + ": used " + ValidationUtil.formatAmount(used) + " + this "
                + ValidationUtil.formatAmount(request.getAmount()) + " = " + ValidationUtil.formatAmount(total);
        if (total.compareTo(limit) > 0) {
            BigDecimal excess = total.subtract(limit);
            request.addNote(calc + " > limit " + ValidationUtil.formatAmount(limit) + " -> EXCEEDED by "
                    + ValidationUtil.formatAmount(excess));
            request.addBreach(overrideId, excess);
        } else {
            request.addNote(calc + " <= limit " + ValidationUtil.formatAmount(limit) + " -> OK");
        }
        LogUtil.info(CLASS_NAME, "checkLimit", "CALC", limitField + " used=" + used + " txn=" + request.getAmount()
                + " limit=" + limit);
    }

    /** No limit record: the amount is counted as it is, 2 decimals. */
    private void scaleAmount(CustomerLimitDTO request) {
        BigDecimal amount = request.getAmount().setScale(Constants.AMOUNT_SCALE, RoundingMode.HALF_UP);
        if (request.isCredit()) {
            request.setCreditAmount(amount);
        } else {
            request.setDebitAmount(amount);
        }
    }

    private static String direction(boolean credit) {
        return credit ? "credit" : "debit";
    }

    /** Date a transaction was counted on, from its EB.ALBE.TXN.DETAILS.LIMIT id; null if none. */
    private LocalDate findCountedDate(CustomerLimitDTO request) {
        for (String recordId : repository.selectTxnDetailsIdsForTransaction(request.getCustomerId(),
                request.getTransactionId(), ValidationUtil.nvl(request.getArrangementId()))) {
            String[] parts = recordId.split(Constants.TXN_ID_SEPARATOR, 4);
            LocalDate date = parts.length >= 3 ? ValidationUtil.toDate(parts[1]) : null;
            if (date != null) {
                LogUtil.info(CLASS_NAME, "findCountedDate", "FOUND", "id=" + recordId);
                return date;
            }
        }
        return null;
    }
}
