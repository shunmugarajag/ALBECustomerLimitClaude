package com.temenos.ALBE.CustomerLimit.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.model.Limits;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.ALBE.CustomerLimit.view.CustomerLimitOverride;
import com.temenos.t24.api.records.ebalbeutilisedlimit.EbAlbeUtilisedLimitRecord;

/*-----------------------------------------------------------------------------
 * Description : Business rules for the daily and monthly limits (HLSD 7.2.1 - 2).
 *               Called by DailyMonthlyLimitController; all DB access goes through
 *               CustomerLimitRepository.
 *
 *   All amounts are compared and counted in the limit currency (EB.ALBE.CIF.LIMIT CCY):
 *   a transaction in another currency is converted first (CurrencyConversionService).
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
    private static final String CLASS_NAME = "DailyMonthlyLimitService";

    private final CustomerLimitRepository repository;
    private final CustomerLimitReader limitReader;
    private final CustomerLimitUtilizationUpdater updater;
    private final TxnDetailsLimitRecorder recorder;
    private final CurrencyConversionService currencyService;

    public DailyMonthlyLimitService(CustomerLimitRepository repository) {
        this.repository = repository;
        this.limitReader = new CustomerLimitReader(repository);
        this.updater = new CustomerLimitUtilizationUpdater(repository);
        this.recorder = new TxnDetailsLimitRecorder(repository);
        this.currencyService = new CurrencyConversionService(repository);
    }

    // =========================================================================
    // Validation
    // =========================================================================

    /**
     * Daily then monthly check. Every breach / blank limit is recorded on the request;
     * a missing EB.ALBE.CIF.LIMIT record sets limitMissing.
     * The request has been checked by CustomerLimitRequestValidator (customer, AAA id, date,
     * one positive credit or debit amount).
     */
    public void validate(CustomerLimitDTO request) {
        LogUtil.info(CLASS_NAME, "validate", "START", request);

        Limits limits = limitReader.getCustomerLimits(request.getCustomerId());
        if (limits == null) {
            LogUtil.info(CLASS_NAME, "validate", "END", "customer=" + request.getCustomerId()
                    + " no EB.ALBE.CIF.LIMIT record, checks not possible");
            request.setLimitMissing(true);
            return;
        }
        LogUtil.info(CLASS_NAME, "validate", "CALC", "limits=" + limits);

        // Compare in the limit currency: convert the transaction amount if its currency differs.
        if (!currencyService.convertRequest(request, limits.ccy)) {
            LogUtil.info(CLASS_NAME, "validate", "END", "customer=" + request.getCustomerId()
                    + " no exchange rate, checks not possible");
            return;
        }

        // On re-input of an amended transaction its earlier input is already in utilisation;
        // leave it out so the transaction is not checked against itself.
        List<CustomerLimitDTO> earlierInputs = recorder.findOriginal(request);
        LogUtil.info(CLASS_NAME, "validate", "CALC", "earlierInputs=" + earlierInputs.size()
                + (earlierInputs.isEmpty() ? " (first input)" : " (re-input of an amended transaction)"));

        validateDailyLimit(request, limits, earlierInputs);
        validateMonthlyLimit(request, limits, earlierInputs);

        LogUtil.info(CLASS_NAME, "validate", "END", request + " blankLimits=" + request.getUndefinedLimits());
    }

    /** Daily: today's utilised credit (or debit) + txn amount against the daily limit. */
    private void validateDailyLimit(CustomerLimitDTO request, Limits limits, List<CustomerLimitDTO> earlierInputs) {
        boolean credit = request.isCredit();
        String dailyId = CustomerLimitUtilizationUpdater.dailyRecordId(request);
        EbAlbeUtilisedLimitRecord dailyRec = repository.readUtilisedLimit(dailyId);

        BigDecimal utilised = utilisedAmount(dailyRec, credit)
                .subtract(earlierAmount(request, earlierInputs, credit, false))
                .max(BigDecimal.ZERO);

        String limitField = credit ? Constants.KEY_DAILY_CREDIT_LIMIT : Constants.KEY_DAILY_DEBIT_LIMIT;
        BigDecimal limit = credit ? limits.dailyCreditLimit : limits.dailyDebitLimit;

        LogUtil.info(CLASS_NAME, "validateDailyLimit", "CALC", limitField + " record=" + dailyId
                + " utilised=" + utilised + " txn=" + request.getAmount() + " limit=" + limit);
        checkLimit(request, limitField, limit, utilised, Constants.OVR_DAILY_LIMIT_EXCEED,
                Constants.MSG_DAILY_EXCEEDED);
    }

    /** Monthly: migrated + this month's utilised credit (or debit) + txn amount against the monthly limit. */
    private void validateMonthlyLimit(CustomerLimitDTO request, Limits limits, List<CustomerLimitDTO> earlierInputs) {
        boolean credit = request.isCredit();
        String monthlyId = CustomerLimitUtilizationUpdater.monthlyRecordId(request);
        EbAlbeUtilisedLimitRecord monthlyRec = repository.readUtilisedLimit(monthlyId);

        BigDecimal migrated = migratedAmount(monthlyRec, credit);
        BigDecimal utilised = utilisedAmount(monthlyRec, credit)
                .subtract(earlierAmount(request, earlierInputs, credit, true))
                .max(BigDecimal.ZERO);

        String limitField = credit ? Constants.KEY_MONTHLY_CREDIT_LIMIT : Constants.KEY_MONTHLY_DEBIT_LIMIT;
        BigDecimal limit = credit ? limits.monthlyCreditLimit : limits.monthlyDebitLimit;

        LogUtil.info(CLASS_NAME, "validateMonthlyLimit", "CALC", limitField + " record=" + monthlyId
                + " migrated=" + migrated + " utilised=" + utilised + " txn=" + request.getAmount() + " limit="
                        + limit);
        checkLimit(request, limitField, limit, migrated.add(utilised), Constants.OVR_MONTHLY_LIMIT_EXCEED,
                Constants.MSG_MONTHLY_EXCEEDED);
    }

    /** UTILISED.CREDIT.LIMIT (or UTILISED.DEBIT.LIMIT) of the record; 0 when there is no record yet. */
    private BigDecimal utilisedAmount(EbAlbeUtilisedLimitRecord utilRec, boolean credit) {
        if (utilRec == null) {
            return BigDecimal.ZERO;
        }
        return ValidationUtil.toAmount(credit ? utilRec.getUtilisedCreditLimit().getValue()
                : utilRec.getUtilisedDebitLimit().getValue());
    }

    /** UTILISED.MIGRATED.CREDIT.LIMIT (or ...DEBIT.LIMIT) of the monthly record; 0 when there is no record. */
    private BigDecimal migratedAmount(EbAlbeUtilisedLimitRecord utilRec, boolean credit) {
        if (utilRec == null) {
            return BigDecimal.ZERO;
        }
        return ValidationUtil.toAmount(credit ? utilRec.getUtilisedMigratedCreditLimit().getValue()
                : utilRec.getUtilisedMigratedDebitLimit().getValue());
    }

    /**
     * Breach when utilised + txn amount is above the limit; the difference is the excess.
     * A blank (null) limit is recorded as "limit not defined".
     */
    private void checkLimit(CustomerLimitDTO request, String limitField, BigDecimal limit, BigDecimal utilised,
            String overrideId, String messageFormat) {
        if (limit == null) {
            LogUtil.info(CLASS_NAME, "checkLimit", "NOT DEFINED", limitField + " is blank for customer="
                    + request.getCustomerId());
            request.addUndefinedLimit(limitField);
            return;
        }
        BigDecimal projected = utilised.add(request.getAmount());
        if (projected.compareTo(limit) > 0) {
            BigDecimal difference = projected.subtract(limit);
            LogUtil.info(CLASS_NAME, "checkLimit", "BREACH", limitField + " projected=" + projected
                    + " limit=" + limit + " difference=" + difference);
            request.addBreach(overrideId,
                    String.format(messageFormat, request.getCustomerId(), difference.toPlainString()), difference);
        } else {
            LogUtil.info(CLASS_NAME, "checkLimit", "OK", limitField + " projected=" + projected + " limit=" + limit
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
            if (isSamePeriod(earlier.getTransactionDate(), request.getTransactionDate(), sameMonth)) {
                total = total.add(credit ? earlier.getCreditAmount() : earlier.getDebitAmount());
            }
        }
        if (total.signum() > 0) {
            LogUtil.info(CLASS_NAME, "earlierAmount", "CALC", "aaa=" + request.getTransactionId() + " "
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
        LogUtil.info(CLASS_NAME, "countAtInput", "START", request + " account=" + accountNo
                + " effectiveDate=" + effectiveDate);
        int earlier = reverseCounted(request);
        toLimitCurrency(request);
        updater.update(request);
        recorder.record(request, accountNo, effectiveDate);
        LogUtil.info(CLASS_NAME, "countAtInput", "END", request.logKey() + " earlierInputReversed=" + earlier);
    }

    /**
     * Rejected input (DELETE) or reversal (AUTH-REV): removes what was counted for the AAA
     * from its original day and month.
     * @param fallbackToToday true for a reversal: if nothing was recorded (counted before go-live),
     *                        reverse the amount on today's records instead
     * The original day / month comes from SYSTEM.DATE of EB.ALBE.TXN.DETAILS.LIMIT <customer>-<AAA id>.
     * @return number of amounts reversed
     */
    public int reverse(CustomerLimitDTO request, boolean fallbackToToday) {
        LogUtil.info(CLASS_NAME, "reverse", "START", request + " fallbackToToday=" + fallbackToToday);
        int reversed = reverseCounted(request);
        if (reversed == 0 && fallbackToToday) {
            LogUtil.info(CLASS_NAME, "reverse", "NOT FOUND", "no EB.ALBE.TXN.DETAILS.LIMIT for aaa="
                    + request.getTransactionId()
                    + ", reversing on server date");
            toLimitCurrency(request);
            updater.reverse(request);
            reversed = 1;
        } else if (reversed == 0) {
            LogUtil.info(CLASS_NAME, "reverse", "SKIP", "nothing was counted for aaa=" + request.getTransactionId());
        }
        LogUtil.info(CLASS_NAME, "reverse", "END", request.logKey() + " reversed=" + reversed);
        return reversed;
    }

    /**
     * Reverses every amount already counted for this customer + AAA (found through
     * EB.ALBE.TXN.DETAILS.LIMIT) on its original day and month, and deletes those records.
     */
    private int reverseCounted(CustomerLimitDTO request) {
        List<CustomerLimitDTO> originals = recorder.findOriginal(request);
        LogUtil.info(CLASS_NAME, "reverseCounted", "CALC", request.logKey() + " found=" + originals.size());
        for (CustomerLimitDTO original : originals) {
            LogUtil.info(CLASS_NAME, "reverseCounted", "CALC", "originalDate=" + original.getTransactionDate()
                    + " credit="
                    + original.getCreditAmount() + " debit=" + original.getDebitAmount());
            updater.reverse(original);
            recorder.delete(original);
        }
        return originals.size();
    }

    /**
     * Converts the request amount to the customer's limit currency before it is counted.
     * Reversals of recorded transactions do not need this: EB.ALBE.TXN.DETAILS.LIMIT already
     * holds the converted amount. With no limit record or no rate the amount is counted as is.
     */
    private void toLimitCurrency(CustomerLimitDTO request) {
        Limits limits = limitReader.getCustomerLimits(request.getCustomerId());
        if (limits == null) {
            LogUtil.info(CLASS_NAME, "toLimitCurrency", "NOT FOUND", "no EB.ALBE.CIF.LIMIT record, amount counted in "
                    + request.getTransactionCurrency());
            return;
        }
        if (!currencyService.convertRequest(request, limits.ccy)) {
            LogUtil.error(CLASS_NAME, "toLimitCurrency", "NO RATE", "no rate from " + request.getTransactionCurrency()
                    + " to "
                    + limits.ccy + ", amount counted unconverted for aaa=" + request.getTransactionId());
        }
    }

    /** Same day, or (sameMonth=true) same calendar month. */
    private boolean isSamePeriod(LocalDate date1, LocalDate date2, boolean sameMonth) {
        return sameMonth ? YearMonth.from(date1).equals(YearMonth.from(date2)) : date1.equals(date2);
    }
}
