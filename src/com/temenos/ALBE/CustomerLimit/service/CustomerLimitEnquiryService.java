package com.temenos.ALBE.CustomerLimit.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.LimitPeriodRow;
import com.temenos.ALBE.CustomerLimit.model.LimitSummaryRow;
import com.temenos.ALBE.CustomerLimit.model.LimitTxnRow;
import com.temenos.ALBE.CustomerLimit.model.Limits;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.t24.api.records.ebalbetxndetailslimit.EbAlbeTxnDetailsLimitRecord;
import com.temenos.t24.api.records.ebalbeutilisedlimit.EbAlbeUtilisedLimitRecord;

/*-----------------------------------------------------------------------------
 * Description : Data for the customer limit enquiries. Read only: nothing is written.
 *               Called by CustomerLimitSummaryEnquiryController and
 *               CustomerLimitTxnEnquiryController; all DB access through CustomerLimitRepository.
 *
 *   getLimitSummary : per customer, the limits from EB.ALBE.CIF.LIMIT and the utilised
 *                     amounts of the report day (<customer>-YYYYMMDD) and its month
 *                     (<customer>-YYYYMM, migrated + utilised) from EB.ALBE.UTILISED.LIMIT.
 *   getDailyRows    : per customer per day with usage in a date range (daily records).
 *   getMonthlyRows  : per customer per month with usage in a month range (monthly records).
 *                     Both: one SELECT on EB.ALBE.UTILISED.LIMIT for all customers (only
 *                     customers with usage have records), or direct reads for one customer;
 *                     each customer's limits are read once and reused for all their rows.
 *   getTransactions : the transactions currently counted for one or all customers, from
 *                     EB.ALBE.TXN.DETAILS.LIMIT, optionally by counted-date range (date in the id).
 *   overLimitOnly   : keep only rows where something used is above its limit.
 *   overriddenOnly  : keep only transactions accepted with a limit-exceed override. The overrides
 *                     are read from each transaction's AA.ARRANGEMENT.ACTIVITY OVERRIDE field
 *                     (one read per transaction listed); no field is added to the local tables.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitEnquiryService {
    private static final String CLASS_NAME = "CustomerLimitEnquiryService";

    private final CustomerLimitRepository repository;
    private final CustomerLimitReader limitReader;

    public CustomerLimitEnquiryService(CustomerLimitRepository repository) {
        this.repository = repository;
        this.limitReader = new CustomerLimitReader(repository);
    }

    /**
     * Limit summary for one customer (customerId given) or for every customer that has an
     * EB.ALBE.CIF.LIMIT record (customerId blank). Customers without a record are left out.
     * overLimitOnly: only customers with at least one used amount above its limit.
     */
    public List<LimitSummaryRow> getLimitSummary(String customerId, LocalDate reportDate, boolean overLimitOnly) {
        LogUtil.info(CLASS_NAME, "getLimitSummary", "START", "customer=" + customerId + " date=" + reportDate
                + " overLimitOnly=" + overLimitOnly);

        List<String> customerIds = ValidationUtil.isBlank(customerId) ? repository.selectCifLimitIds()
                : List.of(customerId.trim());

        List<LimitSummaryRow> rows = new ArrayList<>();
        for (String id : customerIds) {
            LimitSummaryRow row = buildSummaryRow(id, reportDate);
            if (row != null && (!overLimitOnly || row.isOverLimit())) {
                rows.add(row);
            }
        }
        LogUtil.info(CLASS_NAME, "getLimitSummary", "END", "customers=" + customerIds.size() + " rows=" + rows.size());
        return rows;
    }

    /** One customer's row; null if the customer has no EB.ALBE.CIF.LIMIT record. */
    private LimitSummaryRow buildSummaryRow(String customerId, LocalDate reportDate) {
        Limits limits = limitReader.getCustomerLimits(customerId);
        if (limits == null) {
            LogUtil.detail(CLASS_NAME, "buildSummaryRow", "SKIP", "customer=" + customerId + " no EB.ALBE.CIF.LIMIT");
            return null;
        }
        EbAlbeUtilisedLimitRecord dailyRec = repository.readUtilisedLimit(
                CustomerLimitUtilizationUpdater.dailyRecordId(customerId, reportDate));
        EbAlbeUtilisedLimitRecord monthlyRec = repository.readUtilisedLimit(
                CustomerLimitUtilizationUpdater.monthlyRecordId(customerId, reportDate));

        LimitSummaryRow row = new LimitSummaryRow();
        row.customerId = customerId;
        row.ccy = limits.ccy;
        row.reportDate = reportDate;

        row.dailyCreditLimit = limits.dailyCreditLimit;
        row.dailyCreditUtilised = CustomerLimitUtilizationUpdater.utilisedAmount(dailyRec, true);
        row.dailyDebitLimit = limits.dailyDebitLimit;
        row.dailyDebitUtilised = CustomerLimitUtilizationUpdater.utilisedAmount(dailyRec, false);

        row.monthlyCreditLimit = limits.monthlyCreditLimit;
        row.monthlyCreditUtilised = CustomerLimitUtilizationUpdater.migratedAmount(monthlyRec, true)
                .add(CustomerLimitUtilizationUpdater.utilisedAmount(monthlyRec, true));
        row.monthlyDebitLimit = limits.monthlyDebitLimit;
        row.monthlyDebitUtilised = CustomerLimitUtilizationUpdater.migratedAmount(monthlyRec, false)
                .add(CustomerLimitUtilizationUpdater.utilisedAmount(monthlyRec, false));

        row.maxCifBalance = limits.maxCifBalance;

        LogUtil.detail(CLASS_NAME, "buildSummaryRow", "CALC", row);
        return row;
    }

    /**
     * One row per customer per day that has usage, from fromDate to toDate (inclusive):
     * daily limits against UTILISED.CREDIT/DEBIT.LIMIT of <customer>-YYYYMMDD.
     * customerId blank = all customers.
     */
    public List<LimitPeriodRow> getDailyRows(String customerId, LocalDate fromDate, LocalDate toDate,
            boolean overLimitOnly) {
        LogUtil.info(CLASS_NAME, "getDailyRows", "START", "customer=" + customerId + " from=" + fromDate
                + " to=" + toDate + " overLimitOnly=" + overLimitOnly);
        List<String> recordIds = new ArrayList<>();
        if (ValidationUtil.isBlank(customerId)) {
            for (String id : repository.selectUtilisedLimitIds(monthsBetween(YearMonth.from(fromDate),
                    YearMonth.from(toDate)), true)) {
                String period = periodOf(id);
                if (period.length() != Constants.DAILY_PERIOD_LENGTH) {
                    continue;   // the month pattern also matches the monthly record <customer>-YYYYMM
                }
                LocalDate day = ValidationUtil.toDate(period);
                if (day != null && !day.isBefore(fromDate) && !day.isAfter(toDate)) {
                    recordIds.add(id);
                }
            }
        } else {
            for (LocalDate day = fromDate; !day.isAfter(toDate); day = day.plusDays(1)) {
                recordIds.add(CustomerLimitUtilizationUpdater.dailyRecordId(customerId.trim(), day));
            }
        }
        List<LimitPeriodRow> rows = buildPeriodRows(recordIds, true, overLimitOnly);
        LogUtil.info(CLASS_NAME, "getDailyRows", "END", "records=" + recordIds.size() + " rows=" + rows.size());
        return rows;
    }

    /**
     * One row per customer per month that has usage, from fromMonth to toMonth (inclusive):
     * monthly limits against UTILISED.MIGRATED.* + UTILISED.* of <customer>-YYYYMM.
     * customerId blank = all customers.
     */
    public List<LimitPeriodRow> getMonthlyRows(String customerId, YearMonth fromMonth, YearMonth toMonth,
            boolean overLimitOnly) {
        LogUtil.info(CLASS_NAME, "getMonthlyRows", "START", "customer=" + customerId + " from=" + fromMonth
                + " to=" + toMonth + " overLimitOnly=" + overLimitOnly);
        List<String> months = monthsBetween(fromMonth, toMonth);
        List<String> recordIds = new ArrayList<>();
        if (ValidationUtil.isBlank(customerId)) {
            for (String id : repository.selectUtilisedLimitIds(months, false)) {
                if (months.contains(periodOf(id))) {
                    recordIds.add(id);
                }
            }
        } else {
            for (String month : months) {
                recordIds.add(customerId.trim() + "-" + month);
            }
        }
        List<LimitPeriodRow> rows = buildPeriodRows(recordIds, false, overLimitOnly);
        LogUtil.info(CLASS_NAME, "getMonthlyRows", "END", "records=" + recordIds.size() + " rows=" + rows.size());
        return rows;
    }

    /** Rows for the given utilisation record ids; records that do not exist (no usage) are skipped. */
    private List<LimitPeriodRow> buildPeriodRows(List<String> recordIds, boolean daily, boolean overLimitOnly) {
        Map<String, Limits> limitsByCustomer = new HashMap<>();
        List<LimitPeriodRow> rows = new ArrayList<>();
        for (String recordId : recordIds) {
            EbAlbeUtilisedLimitRecord utilRec = repository.readUtilisedLimit(recordId);
            if (utilRec == null) {
                continue;
            }
            String customerId = recordId.substring(0, recordId.lastIndexOf('-'));
            if (!limitsByCustomer.containsKey(customerId)) {
                limitsByCustomer.put(customerId, limitReader.getCustomerLimits(customerId));
            }
            Limits limits = limitsByCustomer.get(customerId);

            LimitPeriodRow row = new LimitPeriodRow();
            row.periodType = daily ? Constants.PERIOD_DAILY : Constants.PERIOD_MONTHLY;
            row.period = periodOf(recordId);
            row.customerId = customerId;
            row.ccy = limits == null ? "" : limits.ccy;
            if (daily) {
                row.creditLimit = limits == null ? null : limits.dailyCreditLimit;
                row.debitLimit = limits == null ? null : limits.dailyDebitLimit;
                row.creditUtilised = CustomerLimitUtilizationUpdater.utilisedAmount(utilRec, true);
                row.debitUtilised = CustomerLimitUtilizationUpdater.utilisedAmount(utilRec, false);
            } else {
                row.creditLimit = limits == null ? null : limits.monthlyCreditLimit;
                row.debitLimit = limits == null ? null : limits.monthlyDebitLimit;
                row.creditUtilised = CustomerLimitUtilizationUpdater.migratedAmount(utilRec, true)
                        .add(CustomerLimitUtilizationUpdater.utilisedAmount(utilRec, true));
                row.debitUtilised = CustomerLimitUtilizationUpdater.migratedAmount(utilRec, false)
                        .add(CustomerLimitUtilizationUpdater.utilisedAmount(utilRec, false));
            }
            if (!overLimitOnly || row.isOverLimit()) {
                rows.add(row);
            }
            LogUtil.detail(CLASS_NAME, "buildPeriodRows", "CALC", row);
        }
        LogUtil.info(CLASS_NAME, "buildPeriodRows", "CALC", "customers=" + limitsByCustomer.size()
                + " (limits read once each)");
        return rows;
    }

    /**
     * EB-ALBE.* override ids in the activity's OVERRIDE values, each once.
     * A value is "<override id>}<message>..." (Constants.OVERRIDE_ID_END); only the id is kept.
     */
    private List<String> albeOverrideIds(List<String> overrideValues) {
        List<String> ids = new ArrayList<>();
        for (String raw : overrideValues) {
            String value = ValidationUtil.nvl(raw);
            int end = value.indexOf(Constants.OVERRIDE_ID_END);
            String id = (end < 0 ? value : value.substring(0, end)).trim();
            if (id.startsWith(Constants.OVERRIDE_ID_PREFIX) && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** YYYYMM of every month from fromMonth to toMonth (inclusive). */
    private List<String> monthsBetween(YearMonth fromMonth, YearMonth toMonth) {
        List<String> months = new ArrayList<>();
        for (YearMonth month = fromMonth; !month.isAfter(toMonth); month = month.plusMonths(1)) {
            months.add(month.format(Constants.MONTHLY_ID_FORMAT));
        }
        return months;
    }

    /** The YYYYMMDD / YYYYMM part of a utilisation record id <customer>-<period>. */
    private String periodOf(String recordId) {
        return recordId.substring(recordId.lastIndexOf('-') + 1);
    }

    /**
     * Transactions counted for one customer (customerId given) or all customers (blank),
     * optionally between fromDate and toDate (counted date in the id, YYYYMMDD; blank = no bound),
     * oldest first.
     */
    public List<LimitTxnRow> getTransactions(String customerId, String fromDate, String toDate,
            boolean overriddenOnly) {
        LogUtil.info(CLASS_NAME, "getTransactions", "START", "customer=" + customerId + " from=" + fromDate
                + " to=" + toDate + " overriddenOnly=" + overriddenOnly);

        // The counted date is part of the id (<customer>-<YYYYMMDD>-<AAA>):
        // one customer -> select the customer's records and keep the dates in range here;
        // all customers -> select only the days of the range (the controller limits it).
        boolean oneCustomer = !ValidationUtil.isBlank(customerId);
        List<String> days = oneCustomer ? List.of() : daysBetween(fromDate, toDate);
        LocalDate from = ValidationUtil.toDate(fromDate);
        LocalDate to = ValidationUtil.toDate(toDate);

        List<LimitTxnRow> rows = new ArrayList<>();
        String finMnemonic = repository.getFinancialMnemonic();   // once, for the AA.ARRANGEMENT.ACTIVITY reads
        for (String recordId : repository.selectTxnDetailsIds(oneCustomer ? customerId.trim() : "", days)) {
            String[] parts = recordId.split("-", 3);   // customer, YYYYMMDD, AAA id
            LocalDate countedOn = parts.length == 3 ? ValidationUtil.toDate(parts[1]) : null;
            if (countedOn == null || (from != null && countedOn.isBefore(from))
                    || (to != null && countedOn.isAfter(to))) {
                continue;
            }
            EbAlbeTxnDetailsLimitRecord txnRec = repository.readTxnDetails(recordId);
            if (txnRec == null) {
                continue;
            }
            LimitTxnRow row = new LimitTxnRow();
            row.recordId = recordId;
            row.customerId = parts[0];
            row.countedDate = parts[1];
            row.aaaId = parts[2];
            row.accountNo = ValidationUtil.nvl(txnRec.getAccountNo().getValue());
            row.creditDebitFlag = ValidationUtil.nvl(txnRec.getCreditDebitFlag().getValue());
            row.effectiveDate = ValidationUtil.nvl(txnRec.getEffDate().getValue());
            row.amount = ValidationUtil.nvl(txnRec.getTxnAmount().getValue());
            // Accepted overrides come from the activity itself (no extra field in EB.ALBE.TXN.DETAILS.LIMIT).
            row.overrides = String.join(Constants.OVERRIDE_LIST_SEPARATOR,
                    albeOverrideIds(repository.readActivityOverrides(finMnemonic, row.aaaId)));
            if (!overriddenOnly || row.isLimitOverridden()) {
                rows.add(row);
            }
        }
        // Oldest first, then by id.
        rows.sort((a, b) -> a.countedDate.equals(b.countedDate) ? a.recordId.compareTo(b.recordId)
                : a.countedDate.compareTo(b.countedDate));
        LogUtil.info(CLASS_NAME, "getTransactions", "END", "customer=" + customerId + " rows=" + rows.size());
        return rows;
    }

    /** YYYYMMDD of every day from fromDate to toDate (inclusive); empty if either is blank. */
    private List<String> daysBetween(String fromDate, String toDate) {
        List<String> days = new ArrayList<>();
        LocalDate from = ValidationUtil.toDate(fromDate);
        LocalDate to = ValidationUtil.toDate(toDate);
        if (from == null || to == null) {
            return days;
        }
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            days.add(day.format(Constants.DAILY_ID_FORMAT));
        }
        return days;
    }
}
