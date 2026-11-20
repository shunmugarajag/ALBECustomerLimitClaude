package com.temenos.ALBE.CustomerLimit.controller;

import java.time.LocalDate;
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
 *               in utilisation (EB.ALBE.TXN.DETAILS.LIMIT), oldest first, for one customer
 *               or for all customers in a date range.
 *               Rejected / reversed transactions are not shown (their record is deleted).
 *               Selection:
 *                 CUSTOMER   optional when a date is given (blank = all customers)
 *                 DATE.FROM  counted date from (YYYYMMDD, the date in the record id)
 *                 DATE.TO    counted date to   (YYYYMMDD)
 *                 OVERRIDDEN Y = only transactions accepted with a limit-exceed override
 *               Customer or at least one date is mandatory. Without a customer the range is
 *               completed (missing DATE.TO = today, missing DATE.FROM = DATE.TO) and may cover
 *               at most Constants.ENQ_MAX_DAYS days.
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
        String overridden = "";
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
                } else if (Constants.ENQ_SEL_OVERRIDDEN.equals(field)) {
                    overridden = value;
                }
            }
        }
        LogUtil.info(CLASS_NAME, "setIds", "START", "customer=" + customerId + " from=" + fromDate + " to=" + toDate
                + " overridden=" + overridden);

        String error = CustomerLimitEnquiryValidator.checkCustomerOrDate(customerId, fromDate, toDate);
        if (error == null) {
            error = CustomerLimitEnquiryValidator.checkDate(Constants.ENQ_SEL_DATE_FROM, fromDate);
        }
        if (error == null) {
            error = CustomerLimitEnquiryValidator.checkDate(Constants.ENQ_SEL_DATE_TO, toDate);
        }
        if (error == null) {
            error = CustomerLimitEnquiryValidator.checkDateRange(fromDate, toDate);
        }
        if (error == null && ValidationUtil.isBlank(customerId)) {
            // All customers: always a closed, limited range.
            if (ValidationUtil.isBlank(toDate)) {
                toDate = LocalDate.now().format(Constants.DAILY_ID_FORMAT);
            }
            if (ValidationUtil.isBlank(fromDate)) {
                fromDate = toDate;
            }
            error = CustomerLimitEnquiryValidator.checkDateRange(fromDate, toDate);
            if (error == null) {
                error = CustomerLimitEnquiryValidator.checkDayCount(ValidationUtil.toDate(fromDate),
                        ValidationUtil.toDate(toDate));
            }
        }
        if (error != null) {
            throw new T24CoreException("", error);
        }

        List<String> lines;
        try {
            CustomerLimitEnquiryService service = new CustomerLimitEnquiryService(new CustomerLimitRepository(this));
            List<LimitTxnRow> rows = service.getTransactions(customerId, fromDate, toDate,
                    CustomerLimitEnquiryValidator.isYes(overridden));
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
