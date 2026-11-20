package com.temenos.ALBE.CustomerLimit.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
 *   getTransactions : the transactions currently counted for a customer, from
 *                     EB.ALBE.TXN.DETAILS.LIMIT, optionally by SYSTEM.DATE range.
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
     */
    public List<LimitSummaryRow> getLimitSummary(String customerId, LocalDate reportDate) {
        LogUtil.info(CLASS_NAME, "getLimitSummary", "START", "customer=" + customerId + " date=" + reportDate);

        List<String> customerIds = ValidationUtil.isBlank(customerId) ? repository.selectCifLimitIds()
                : List.of(customerId.trim());

        List<LimitSummaryRow> rows = new ArrayList<>();
        for (String id : customerIds) {
            LimitSummaryRow row = buildSummaryRow(id, reportDate);
            if (row != null) {
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
            LogUtil.info(CLASS_NAME, "buildSummaryRow", "SKIP", "customer=" + customerId + " no EB.ALBE.CIF.LIMIT");
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

        LogUtil.info(CLASS_NAME, "buildSummaryRow", "CALC", row);
        return row;
    }

    /**
     * Transactions counted for the customer, optionally between fromDate and toDate
     * (SYSTEM.DATE, YYYYMMDD; blank = no bound), oldest first.
     */
    public List<LimitTxnRow> getTransactions(String customerId, String fromDate, String toDate) {
        LogUtil.info(CLASS_NAME, "getTransactions", "START", "customer=" + customerId + " from=" + fromDate
                + " to=" + toDate);

        List<LimitTxnRow> rows = new ArrayList<>();
        for (String recordId : repository.selectTxnDetailsIds(customerId, fromDate, toDate)) {
            EbAlbeTxnDetailsLimitRecord txnRec = repository.readTxnDetails(recordId);
            if (txnRec == null) {
                continue;
            }
            LimitTxnRow row = new LimitTxnRow();
            row.recordId = recordId;
            row.customerId = customerId;
            int dash = recordId.indexOf('-');
            row.aaaId = dash < 0 ? "" : recordId.substring(dash + 1);
            row.accountNo = ValidationUtil.nvl(txnRec.getAccountNo().getValue());
            row.creditDebitFlag = ValidationUtil.nvl(txnRec.getCreditDebitFlag().getValue());
            row.effectiveDate = ValidationUtil.nvl(txnRec.getEffDate().getValue());
            row.systemDate = ValidationUtil.nvl(txnRec.getSystemDate().getValue());
            row.amount = ValidationUtil.nvl(txnRec.getTxnAmount().getValue());
            rows.add(row);
        }
        LogUtil.info(CLASS_NAME, "getTransactions", "END", "customer=" + customerId + " rows=" + rows.size());
        return rows;
    }
}
