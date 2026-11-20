package com.temenos.ALBE.CustomerLimit.service;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import java.math.BigDecimal;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.model.Limits;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.view.CustomerLimitOverride;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;
import com.temenos.t24.api.records.ebalbeutilisedlimit.EbAlbeUtilisedLimitRecord;

/*-----------------------------------------------------------------------------
 * Description : Business rules for the daily and monthly limits (HLSD 7.2.1 - 2).
 *               Called by DailyMonthlyLimitController; all DB access goes through
 *               CustomerLimitRepository.
 *
 *   validate      : daily  = UTILISED.CREDIT/DEBIT.LIMIT of <customer>-YYYYMMDD + txn
 *                            vs DAILY.CREDIT/DEBIT.LIMIT
 *                   monthly = UTILISED.MIGRATED.CREDIT/DEBIT.LIMIT + UTILISED.CREDIT/DEBIT.LIMIT
 *                            of <customer>-YYYYMM + txn vs MONTHLY.CREDIT/DEBIT.LIMIT
 *                   A re-input leaves out the AAA's own earlier input.
 *   countAtInput  : adds the txn to utilisation (earlier input of the same AAA removed first)
 *                   and records it in EB.ALBE.TXN.DETAILS.LIMIT.
 *   reverse       : removes what was counted for the AAA (rejected input or reversal).
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class DailyMonthlyLimitService {

    private static final Logger logger = LoggerFactory.getLogger(Constants.LOGGER_NAME);
    private static final String LOG_PREFIX = "DailyMonthlyLimitService:";

    private final CustomerLimitRepository repository;
    private final CustomerLimitReader limitReader;
    private final CustomerLimitUtilizationUpdater updater;
    private final TxnDetailsLimitRecorder recorder;

    public DailyMonthlyLimitService(CustomerLimitRepository repository) {
        this.repository = repository;
        this.limitReader = new CustomerLimitReader(repository);
        this.updater = new CustomerLimitUtilizationUpdater(repository);
        this.recorder = new TxnDetailsLimitRecorder(repository);
    }

    // =========================================================================
    // Validation
    // =========================================================================

    /**
     * Daily then monthly check. Every breach / blank limit is recorded on the request;
     * a missing EB.ALBE.CIF.LIMIT record sets limitMissing.
     */
    public void validate(CustomerLimitDTO request) {
        logger.info(LOG_PREFIX + "validate START " + request);

        Limits limits = limitReader.getCustomerLimits(request.getCustomerId());
        if (limits == null) {
            logger.info(LOG_PREFIX + "validate END customer=" + request.getCustomerId()
                    + " no EB.ALBE.CIF.LIMIT record, checks not possible");
            request.setLimitMissing(true);
            return;
        }
        logger.info(LOG_PREFIX + "validate limits=" + limits);

        // On re-input of an amended transaction its earlier input is already in utilisation;
        // leave it out so the transaction is not checked against itself.
        List<CustomerLimitDTO> earlierInputs =
                recorder.findOriginal(request.getCustomerId(), request.getTransactionId());
        logger.info(LOG_PREFIX + "validate earlierInputs=" + earlierInputs.size()
                + (earlierInputs.isEmpty() ? " (first input)" : " (re-input of an amended transaction)"));

        validateDailyLimit(request, limits, earlierInputs);
        validateMonthlyLimit(request, limits, earlierInputs);

        logger.info(LOG_PREFIX + "validate END " + request + " blankLimits=" + request.getUndefinedLimits());
    }

    /** Daily: today's utilised credit/debit + txn amount against the daily credit/debit limit. */
    private void validateDailyLimit(CustomerLimitDTO request, Limits limits, List<CustomerLimitDTO> earlierInputs) {
        String dailyId = CustomerLimitUtilizationUpdater.dailyRecordId(request);
        EbAlbeUtilisedLimitRecord dailyRec = repository.readUtilisedLimit(dailyId);

        if (isCredit(request)) {
            BigDecimal utilised = dailyRec == null ? BigDecimal.ZERO
                    : CustomerLimitUtilizationUpdater.toAmount(dailyRec.getUtilisedCreditLimit().getValue());
            utilised = utilised.subtract(earlierAmount(request, earlierInputs, true, false)).max(BigDecimal.ZERO);
            logger.info(LOG_PREFIX + "validateDailyLimit CREDIT record=" + dailyId + " utilised=" + utilised
                    + " txn=" + request.getCreditAmount() + " limit=" + limits.dailyCreditLimit);
            checkLimit(request, Constants.KEY_DAILY_CREDIT_LIMIT, limits.dailyCreditLimit, utilised, request.getCreditAmount(),
                    Constants.OVR_DAILY_LIMIT_EXCEED, Constants.MSG_DAILY_EXCEEDED);
        } else {
            BigDecimal utilised = dailyRec == null ? BigDecimal.ZERO
                    : CustomerLimitUtilizationUpdater.toAmount(dailyRec.getUtilisedDebitLimit().getValue());
            utilised = utilised.subtract(earlierAmount(request, earlierInputs, false, false)).max(BigDecimal.ZERO);
            logger.info(LOG_PREFIX + "validateDailyLimit DEBIT record=" + dailyId + " utilised=" + utilised
                    + " txn=" + request.getDebitAmount() + " limit=" + limits.dailyDebitLimit);
            checkLimit(request, Constants.KEY_DAILY_DEBIT_LIMIT, limits.dailyDebitLimit, utilised, request.getDebitAmount(),
                    Constants.OVR_DAILY_LIMIT_EXCEED, Constants.MSG_DAILY_EXCEEDED);
        }
    }

    /** Monthly: migrated + this month's utilised credit/debit + txn amount against the monthly limit. */
    private void validateMonthlyLimit(CustomerLimitDTO request, Limits limits, List<CustomerLimitDTO> earlierInputs) {
        String monthlyId = CustomerLimitUtilizationUpdater.monthlyRecordId(request);
        EbAlbeUtilisedLimitRecord monthlyRec = repository.readUtilisedLimit(monthlyId);

        if (isCredit(request)) {
            BigDecimal migrated = monthlyRec == null ? BigDecimal.ZERO
                    : CustomerLimitUtilizationUpdater.toAmount(monthlyRec.getUtilisedMigratedCreditLimit().getValue());
            BigDecimal monthly = monthlyRec == null ? BigDecimal.ZERO
                    : CustomerLimitUtilizationUpdater.toAmount(monthlyRec.getUtilisedCreditLimit().getValue());
            monthly = monthly.subtract(earlierAmount(request, earlierInputs, true, true)).max(BigDecimal.ZERO);
            logger.info(LOG_PREFIX + "validateMonthlyLimit CREDIT record=" + monthlyId + " migrated=" + migrated
                    + " utilised=" + monthly + " txn=" + request.getCreditAmount() + " limit="
                    + limits.monthlyCreditLimit);
            checkLimit(request, Constants.KEY_MONTHLY_CREDIT_LIMIT, limits.monthlyCreditLimit, migrated.add(monthly),
                    request.getCreditAmount(), Constants.OVR_MONTHLY_LIMIT_EXCEED, Constants.MSG_MONTHLY_EXCEEDED);
        } else {
            BigDecimal migrated = monthlyRec == null ? BigDecimal.ZERO
                    : CustomerLimitUtilizationUpdater.toAmount(monthlyRec.getUtilisedMigratedDebitLimit().getValue());
            BigDecimal monthly = monthlyRec == null ? BigDecimal.ZERO
                    : CustomerLimitUtilizationUpdater.toAmount(monthlyRec.getUtilisedDebitLimit().getValue());
            monthly = monthly.subtract(earlierAmount(request, earlierInputs, false, true)).max(BigDecimal.ZERO);
            logger.info(LOG_PREFIX + "validateMonthlyLimit DEBIT record=" + monthlyId + " migrated=" + migrated
                    + " utilised=" + monthly + " txn=" + request.getDebitAmount() + " limit="
                    + limits.monthlyDebitLimit);
            checkLimit(request, Constants.KEY_MONTHLY_DEBIT_LIMIT, limits.monthlyDebitLimit, migrated.add(monthly),
                    request.getDebitAmount(), Constants.OVR_MONTHLY_LIMIT_EXCEED, Constants.MSG_MONTHLY_EXCEEDED);
        }
    }

    /**
     * Breach when utilised + txn amount is above the limit; the difference is the excess.
     * A blank (null) limit is recorded as "limit not defined".
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
            logger.info(LOG_PREFIX + "checkLimit BREACH " + limitField + " projected=" + projected
                    + " limit=" + limit + " difference=" + difference);
            request.addBreach(overrideId,
                    String.format(messageFormat, request.getCustomerId(), difference.toPlainString()), difference);
        } else {
            logger.info(LOG_PREFIX + "checkLimit OK " + limitField + " projected=" + projected + " limit=" + limit
                    + " headroom=" + limit.subtract(projected));
        }
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

    // =========================================================================
    // Utilisation
    // =========================================================================

    /**
     * Transaction initiated (UNAUTH): removes any earlier input of the same AAA, adds this
     * amount to the daily and monthly utilisation, and records it for a later reversal.
     */
    public void countAtInput(CustomerLimitDTO request, String accountNo, String effectiveDate) {
        logger.info(LOG_PREFIX + "countAtInput START " + request + " account=" + accountNo
                + " effectiveDate=" + effectiveDate);
        int earlier = reverseCounted(request);
        updater.update(request);
        recorder.record(request, accountNo, effectiveDate);
        logger.info(LOG_PREFIX + "countAtInput END customer=" + request.getCustomerId() + " aaa="
                + request.getTransactionId() + " earlierInputReversed=" + earlier);
    }

    /**
     * Rejected input (DELETE) or reversal (AUTH-REV): removes what was counted for the AAA
     * from its original day and month.
     * @param fallbackToToday true for a reversal: if nothing was recorded (counted before go-live),
     *                        reverse the amount on today's records instead
     * @return number of amounts reversed
     */
    public int reverse(CustomerLimitDTO request, boolean fallbackToToday) {
        logger.info(LOG_PREFIX + "reverse START " + request + " fallbackToToday=" + fallbackToToday);
        int reversed = reverseCounted(request);
        if (reversed == 0 && fallbackToToday) {
            logger.info(LOG_PREFIX + "reverse no EB.ALBE.TXN.DETAILS.LIMIT for aaa=" + request.getTransactionId()
                    + ", reversing on server date");
            updater.reverse(request);
            reversed = 1;
        } else if (reversed == 0) {
            logger.info(LOG_PREFIX + "reverse nothing was counted for aaa=" + request.getTransactionId());
        }
        logger.info(LOG_PREFIX + "reverse END customer=" + request.getCustomerId() + " aaa="
                + request.getTransactionId() + " reversed=" + reversed);
        return reversed;
    }

    /**
     * Reverses every amount already counted for this customer + AAA (found through
     * EB.ALBE.TXN.DETAILS.LIMIT) on its original day and month, and deletes those records.
     */
    private int reverseCounted(CustomerLimitDTO request) {
        List<CustomerLimitDTO> originals =
                recorder.findOriginal(request.getCustomerId(), request.getTransactionId());
        logger.info(LOG_PREFIX + "reverseCounted customer=" + request.getCustomerId() + " aaa="
                + request.getTransactionId() + " found=" + originals.size());
        for (CustomerLimitDTO original : originals) {
            logger.info(LOG_PREFIX + "reverseCounted originalDate=" + original.getTransactionDate() + " credit="
                    + original.getCreditAmount() + " debit=" + original.getDebitAmount());
            updater.reverse(original);
            recorder.delete(original);
        }
        return originals.size();
    }

    private boolean isCredit(CustomerLimitDTO request) {
        return request.getCreditAmount().signum() > 0;
    }
}
