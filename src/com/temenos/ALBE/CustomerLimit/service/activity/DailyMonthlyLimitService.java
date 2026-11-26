package com.temenos.ALBE.CustomerLimit.service.activity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.model.Limits;
import com.temenos.ALBE.CustomerLimit.model.UtilisationRecords;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.service.CustomerLimitUtilizationUpdater;
import com.temenos.ALBE.CustomerLimit.service.CustomerLimitReader;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.t24.api.records.ebalbeutilisedlimit.EbAlbeUtilisedLimitRecord;

/*-----------------------------------------------------------------------------
 * Description : Business rules for the daily and monthly limits (HLSD 7.2.1 - 2).
 *               Called by CustomerLimitEngine (single routine and the old controllers); all DB access goes through
 *               CustomerLimitRepository.
 *
 *   All amounts are compared and counted in the limit currency (EB.ALBE.CIF.LIMITS CCY):
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
 * 22-Nov-2026   Development      Blank CIF.LIMIT CCY = error (limitCcyMissing)
 * 25-Nov-2026   Development      plain-language notes for the RESULT log line (used + this = total vs limit)
 * 26-Nov-2026   Development      D46: debit / credit override ids
 * 26-Nov-2026   Development      Single routine: validate / count with the limits and records read once
 *                                (UtilisationRecords); D44 conversion -> blocked instead of 'no rate'
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
     * a missing EB.ALBE.CIF.LIMITS record sets limitMissing.
     * The request has been checked by CustomerLimitRequestValidator (customer, AAA id, date,
     * one positive credit or debit amount).
     */
    public void validate(CustomerLimitDTO request) {
        validate(request, limitReader.getCustomerLimits(request.getCustomerId()), null);
    }

    /**
     * Same check with the customer's limits already read (null = no EB.ALBE.CIF.LIMITS record).
     * @param records when not null, filled with the utilisation records and earlier input read
     *                here, so the count in the same call does not read them again
     */
    public void validate(CustomerLimitDTO request, Limits limits, UtilisationRecords records) {
        LogUtil.detail(CLASS_NAME, "validate", "START", request);

        if (limits == null) {
            LogUtil.detail(CLASS_NAME, "validate", "END", "customer=" + request.getCustomerId()
                    + " no EB.ALBE.CIF.LIMITS record, checks not possible");
            request.addNote("no EB.ALBE.CIF.LIMITS record for customer " + request.getCustomerId()
                    + " -> daily / monthly not checked");
            request.setLimitMissing(true);
            return;
        }
        LogUtil.detail(CLASS_NAME, "validate", "CALC", "limits=" + limits);
        if (ValidationUtil.isBlank(limits.ccy)) {
            LogUtil.error(CLASS_NAME, "validate", "NO CCY", "customer=" + request.getCustomerId()
                    + " EB.ALBE.CIF.LIMITS CCY is blank - error, transaction blocked");
            request.addNote("EB.ALBE.CIF.LIMITS CURRENCY is blank for customer " + request.getCustomerId());
            request.setLimitCcyMissing(true);
            return;
        }

        // Compare in the limit currency: convert the transaction amount if its currency differs.
        if (!currencyService.convertRequest(request, limits.ccy)) {
            LogUtil.detail(CLASS_NAME, "validate", "END", "customer=" + request.getCustomerId()
                    + " amount cannot be converted, transaction blocked (D44)");
            return;
        }

        // On re-input of an amended transaction its earlier input is already in utilisation;
        // leave it out so the transaction is not checked against itself.
        List<CustomerLimitDTO> earlierInputs = recorder.findOriginal(request);
        if (records != null) {
            records.setEarlierInputs(earlierInputs);
        }
        LogUtil.detail(CLASS_NAME, "validate", "CALC", "earlierInputs=" + earlierInputs.size()
                + (earlierInputs.isEmpty() ? " (first input)" : " (re-input of an amended transaction)"));

        if (!earlierInputs.isEmpty()) {
            request.addNote("re-input: the earlier input of this transaction is left out of 'used'");
        }
        validateDailyLimit(request, limits, earlierInputs, records);
        validateMonthlyLimit(request, limits, earlierInputs, records);

        LogUtil.detail(CLASS_NAME, "validate", "END", request + " blankLimits=" + request.getUndefinedLimits());
    }

    /** Daily: today's utilised credit (or debit) + txn amount against the daily limit. */
    private void validateDailyLimit(CustomerLimitDTO request, Limits limits, List<CustomerLimitDTO> earlierInputs,
            UtilisationRecords records) {
        boolean credit = request.isCredit();
        String dailyId = CustomerLimitUtilizationUpdater.dailyRecordId(request);
        EbAlbeUtilisedLimitRecord dailyRec = repository.readUtilisedLimit(dailyId);
        if (records != null) {
            records.setDaily(dailyId, dailyRec);
        }

        BigDecimal utilised = CustomerLimitUtilizationUpdater.utilisedAmount(dailyRec, credit)
                .subtract(earlierAmount(request, earlierInputs, credit, false))
                .max(BigDecimal.ZERO);

        String limitField = credit ? Constants.KEY_DAILY_CREDIT_LIMIT : Constants.KEY_DAILY_DEBIT_LIMIT;
        BigDecimal limit = credit ? limits.dailyCreditLimit : limits.dailyDebitLimit;

        LogUtil.detail(CLASS_NAME, "validateDailyLimit", "CALC", limitField + " record=" + dailyId
                + " utilised=" + utilised + " txn=" + request.getAmount() + " limit=" + limit);
        checkLimit(request, "daily " + (credit ? "credit" : "debit") + " (" + dailyId + ")", limitField, limit,
                utilised, credit ? Constants.OVR_DAILY_CREDIT_LIMIT_EXCEED : Constants.OVR_DAILY_DEBIT_LIMIT_EXCEED,
                credit ? Constants.MSG_DAILY_CREDIT_EXCEEDED : Constants.MSG_DAILY_DEBIT_EXCEEDED);
    }

    /** Monthly: migrated + this month's utilised credit (or debit) + txn amount against the monthly limit. */
    private void validateMonthlyLimit(CustomerLimitDTO request, Limits limits, List<CustomerLimitDTO> earlierInputs,
            UtilisationRecords records) {
        boolean credit = request.isCredit();
        String monthlyId = CustomerLimitUtilizationUpdater.monthlyRecordId(request);
        EbAlbeUtilisedLimitRecord monthlyRec = repository.readUtilisedLimit(monthlyId);
        if (records != null) {
            records.setMonthly(monthlyId, monthlyRec);
        }

        BigDecimal migrated = CustomerLimitUtilizationUpdater.migratedAmount(monthlyRec, credit);
        BigDecimal utilised = CustomerLimitUtilizationUpdater.utilisedAmount(monthlyRec, credit)
                .subtract(earlierAmount(request, earlierInputs, credit, true))
                .max(BigDecimal.ZERO);

        String limitField = credit ? Constants.KEY_MONTHLY_CREDIT_LIMIT : Constants.KEY_MONTHLY_DEBIT_LIMIT;
        BigDecimal limit = credit ? limits.monthlyCreditLimit : limits.monthlyDebitLimit;

        LogUtil.detail(CLASS_NAME, "validateMonthlyLimit", "CALC", limitField + " record=" + monthlyId
                + " migrated=" + migrated + " utilised=" + utilised + " txn=" + request.getAmount() + " limit="
                        + limit);
        checkLimit(request, "monthly " + (credit ? "credit" : "debit") + " (" + monthlyId
                + (migrated.signum() > 0 ? ", migrated " + ValidationUtil.formatAmount(migrated) : "") + ")",
                limitField, limit, migrated.add(utilised),
                credit ? Constants.OVR_MONTHLY_CREDIT_LIMIT_EXCEED : Constants.OVR_MONTHLY_DEBIT_LIMIT_EXCEED,
                credit ? Constants.MSG_MONTHLY_CREDIT_EXCEEDED : Constants.MSG_MONTHLY_DEBIT_EXCEEDED);
    }

    /**
     * Breach when utilised + txn amount is above the limit; the difference is the excess.
     * A blank (null) limit is recorded as "limit not defined".
     */
    private void checkLimit(CustomerLimitDTO request, String label, String limitField, BigDecimal limit,
            BigDecimal utilised, String overrideId, String messageFormat) {
        if (limit == null) {
            request.addNote(label + ": " + limitField + " is blank -> not checked");
            LogUtil.detail(CLASS_NAME, "checkLimit", "NOT DEFINED", limitField + " is blank for customer="
                    + request.getCustomerId());
            request.addUndefinedLimit(limitField);
            return;
        }
        BigDecimal projected = utilised.add(request.getAmount());
        if (projected.compareTo(limit) > 0) {
            BigDecimal difference = projected.subtract(limit);
            request.addNote(label + ": used " + ValidationUtil.formatAmount(utilised) + " + this "
                    + ValidationUtil.formatAmount(request.getAmount()) + " = " + ValidationUtil.formatAmount(projected)
                    + " > limit " + ValidationUtil.formatAmount(limit) + " -> EXCEEDED by "
                    + ValidationUtil.formatAmount(difference));
            LogUtil.detail(CLASS_NAME, "checkLimit", "BREACH", limitField + " projected=" + projected
                    + " limit=" + limit + " difference=" + difference);
            request.addBreach(overrideId,
                    String.format(messageFormat, request.getCustomerId(), difference.toPlainString()), difference);
        } else {
            request.addNote(label + ": used " + ValidationUtil.formatAmount(utilised) + " + this "
                    + ValidationUtil.formatAmount(request.getAmount()) + " = " + ValidationUtil.formatAmount(projected)
                    + " <= limit " + ValidationUtil.formatAmount(limit) + " -> OK");
            LogUtil.detail(CLASS_NAME, "checkLimit", "OK", limitField + " projected=" + projected + " limit=" + limit
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
            LogUtil.detail(CLASS_NAME, "earlierAmount", "CALC", "aaa=" + request.getTransactionId() + " "
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
        LogUtil.detail(CLASS_NAME, "countAtInput", "START", request + " account=" + accountNo
                + " effectiveDate=" + effectiveDate);
        int earlier = reverseCounted(request);
        toLimitCurrency(request);
        updater.update(request);
        recorder.record(request, accountNo, effectiveDate);
        LogUtil.detail(CLASS_NAME, "countAtInput", "END", request.logKey() + " earlierInputReversed=" + earlier);
    }

    /**
     * Single routine: counts a first input with the records the check in the same call already
     * read (no second read). The request is the CHECKED one - its amount is already in the limit
     * currency. Caller makes sure records.isUsableForCount().
     */
    public void countChecked(CustomerLimitDTO checkedRequest, UtilisationRecords records, String accountNo,
            String effectiveDate) {
        LogUtil.detail(CLASS_NAME, "countChecked", "START", checkedRequest.logKey() + " account=" + accountNo);
        updater.update(checkedRequest, records.getDailyRecord(), records.getMonthlyRecord());
        recorder.record(checkedRequest, accountNo, effectiveDate);
        LogUtil.detail(CLASS_NAME, "countChecked", "END", checkedRequest.logKey());
    }

    /**
     * Rejected input (DELETE) or reversal (AUTH-REV): removes what was counted for the AAA
     * from its original day and month.
     * @param fallbackToToday true for a reversal: if nothing was recorded (counted before go-live),
     *                        reverse the amount on today's records instead
     * The original day / month is the counted date in the EB.ALBE.TXN.DETAILS.LIMIT id; the record is
     * EB.ALBE.TXN.DETAILS.LIMIT <customer>-<date>-<transaction key>.
     * @return number of amounts reversed
     */
    public int reverse(CustomerLimitDTO request, boolean fallbackToToday) {
        return reverse(request, fallbackToToday, null);
    }

    /**
     * Same, with what was recorded for the transaction already found by the caller (null = look it up),
     * so EB.ALBE.TXN.DETAILS.LIMIT is not read twice.
     */
    public int reverse(CustomerLimitDTO request, boolean fallbackToToday, List<CustomerLimitDTO> recorded) {
        LogUtil.detail(CLASS_NAME, "reverse", "START", request + " fallbackToToday=" + fallbackToToday);
        int reversed = reverseCounted(request, recorded);
        if (reversed == 0 && fallbackToToday) {
            LogUtil.info(CLASS_NAME, "reverse", "NOT FOUND", "no EB.ALBE.TXN.DETAILS.LIMIT for aaa="
                    + request.getTransactionId()
                    + ", reversing on server date");
            request.addNote("nothing was recorded for this transaction (counted before go-live?) -> taken back on "
                    + request.getTransactionDate());
            toLimitCurrency(request);
            updater.reverse(request);
            reversed = 1;
        } else if (reversed == 0) {
            LogUtil.detail(CLASS_NAME, "reverse", "SKIP", "nothing was counted for aaa=" + request.getTransactionId());
            request.addNote("nothing was counted for this transaction -> nothing to take back");
        }
        LogUtil.detail(CLASS_NAME, "reverse", "END", request.logKey() + " reversed=" + reversed);
        return reversed;
    }

    /**
     * Reverses every amount already counted for this customer + AAA (found through
     * EB.ALBE.TXN.DETAILS.LIMIT) on its original day and month, and deletes those records.
     */
    private int reverseCounted(CustomerLimitDTO request) {
        return reverseCounted(request, null);
    }

    /** Same, with the recorded entries already found (null = look them up). */
    private int reverseCounted(CustomerLimitDTO request, List<CustomerLimitDTO> recorded) {
        List<CustomerLimitDTO> originals = recorded != null ? recorded : recorder.findOriginal(request);
        LogUtil.detail(CLASS_NAME, "reverseCounted", "CALC", request.logKey() + " found=" + originals.size());
        for (CustomerLimitDTO original : originals) {
            LogUtil.detail(CLASS_NAME, "reverseCounted", "CALC", "originalDate=" + original.getTransactionDate()
                    + " credit="
                    + original.getCreditAmount() + " debit=" + original.getDebitAmount());
            updater.reverse(original);
            recorder.delete(original);
            request.addNote("taken back " + ValidationUtil.formatAmount(original.getAmount()) + " counted on "
                    + original.getTransactionDate());
            for (String note : original.getNotes()) {
                request.addNote(note);
            }
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
            LogUtil.info(CLASS_NAME, "toLimitCurrency", "NOT FOUND", "no EB.ALBE.CIF.LIMITS record, amount counted in "
                    + request.getTransactionCurrency());
            return;
        }
        if (!currencyService.convertRequest(request, limits.ccy)) {
            LogUtil.error(CLASS_NAME, "toLimitCurrency", "NO RATE", "cannot convert " + request.getTransactionCurrency()
                    + " to " + limits.ccy + ", amount counted unconverted for aaa=" + request.getTransactionId()
                    + " (the check blocks such a transaction, D44)");
        }
    }

    /** Same day, or (sameMonth=true) same calendar month. */
    private boolean isSamePeriod(LocalDate date1, LocalDate date2, boolean sameMonth) {
        return sameMonth ? YearMonth.from(date1).equals(YearMonth.from(date2)) : date1.equals(date2);
    }
}
