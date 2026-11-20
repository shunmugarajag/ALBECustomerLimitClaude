package com.temenos.ALBE.CustomerLimit.controller;

import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.LimitTxnRow;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.service.CustomerLimitEnquiryService;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.ALBE.CustomerLimit.validation.CustomerLimitEnquiryValidator;
import com.temenos.ALBE.CustomerLimit.view.CustomerLimitEnquiryView;
import com.temenos.api.exceptions.T24CoreException;
import com.temenos.t24.api.complex.eb.enquiryhook.EnquiryContext;
import com.temenos.t24.api.complex.eb.enquiryhook.FilterCriteria;
import com.temenos.t24.api.hook.system.Enquiry;

/*-----------------------------------------------------------------------------
 * Attached as : Nofile Enquiry Routine
 * EB.API      : ALBE.E.CUSTOMER.LIMIT.TXN
 * Attached to : STANDARD.SELECTION > NOFILE.ALBE.CUSTOMER.LIMIT.TXN
 * Description : Customer limit transaction report: the transactions currently counted
 *               in a customer's utilisation (EB.ALBE.TXN.DETAILS.LIMIT), oldest first.
 *               Rejected / reversed transactions are not shown (their record is deleted).
 *               Selection:
 *                 CUSTOMER   mandatory
 *                 DATE.FROM  optional, SYSTEM.DATE from (YYYYMMDD)
 *                 DATE.TO    optional, SYSTEM.DATE to   (YYYYMMDD)
 *               Columns: see CustomerLimitEnquiryView.TXN_COLUMNS.
 *               No business rules and no DB access here.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitTxnEnquiryController extends Enquiry {
    private static final String CLASS_NAME = "CustomerLimitTxnEnquiryController";

    @Override
    public List<String> setIds(List<FilterCriteria> filterCriteria, EnquiryContext enquiryContext) {
        long startTime = System.currentTimeMillis();

        // Selection
        String customerId = "";
        String fromDate = "";
        String toDate = "";
        if (filterCriteria != null) {
            for (FilterCriteria filter : filterCriteria) {
                String field = filter.getFieldname();
                String value = ValidationUtil.nvl(filter.getValue()).trim();
                if (Constants.ENQ_SEL_CUSTOMER.equals(field)) {
                    customerId = value;
                } else if (Constants.ENQ_SEL_DATE_FROM.equals(field)) {
                    fromDate = value;
                } else if (Constants.ENQ_SEL_DATE_TO.equals(field)) {
                    toDate = value;
                }
            }
        }
        LogUtil.info(CLASS_NAME, "setIds", "START", "customer=" + customerId + " from=" + fromDate + " to=" + toDate);

        String error = CustomerLimitEnquiryValidator.checkCustomerGiven(customerId);
        if (error == null) {
            error = CustomerLimitEnquiryValidator.checkDate(Constants.ENQ_SEL_DATE_FROM, fromDate);
        }
        if (error == null) {
            error = CustomerLimitEnquiryValidator.checkDate(Constants.ENQ_SEL_DATE_TO, toDate);
        }
        if (error == null) {
            error = CustomerLimitEnquiryValidator.checkDateRange(fromDate, toDate);
        }
        if (error != null) {
            throw new T24CoreException("", error);
        }

        List<String> lines;
        try {
            CustomerLimitEnquiryService service = new CustomerLimitEnquiryService(new CustomerLimitRepository(this));
            List<LimitTxnRow> rows = service.getTransactions(customerId, fromDate, toDate);
            lines = CustomerLimitEnquiryView.toTxnLines(rows);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "setIds", "ERROR", "customer=" + customerId, e);
            throw new T24CoreException("", Constants.ERR_NO_RECORDS);
        }

        LogUtil.info(CLASS_NAME, "setIds", "END", "customer=" + customerId + " rows=" + lines.size()
                + " elapsedMs=" + (System.currentTimeMillis() - startTime));
        if (lines.isEmpty()) {
            throw new T24CoreException("", Constants.ERR_NO_RECORDS);
        }
        return lines;
    }
}
