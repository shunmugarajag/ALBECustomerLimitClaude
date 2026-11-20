package com.temenos.ALBE.CustomerLimit.controller;

import java.time.LocalDate;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.LimitSummaryRow;
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
 * EB.API      : ALBE.E.CUSTOMER.LIMIT.SUMMARY
 * Attached to : STANDARD.SELECTION > NOFILE.ALBE.CUSTOMER.LIMIT.SUMMARY
 * Description : Customer limit summary report: per customer the limits, what is used
 *               today (or on DATE) and in that month, and what is still available.
 *               Selection (both optional):
 *                 CUSTOMER  one customer; blank = every customer with an EB.ALBE.CIF.LIMIT
 *                 DATE      report date YYYYMMDD; blank = server date
 *               Columns: see CustomerLimitEnquiryView.SUMMARY_COLUMNS.
 *               No business rules and no DB access here.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitSummaryEnquiryController extends Enquiry {
    private static final String CLASS_NAME = "CustomerLimitSummaryEnquiryController";

    @Override
    public List<String> setIds(List<FilterCriteria> filterCriteria, EnquiryContext enquiryContext) {
        long startTime = System.currentTimeMillis();

        // Selection
        String customerId = "";
        String dateText = "";
        if (filterCriteria != null) {
            for (FilterCriteria filter : filterCriteria) {
                String field = filter.getFieldname();
                if (Constants.ENQ_SEL_CUSTOMER.equals(field)) {
                    customerId = ValidationUtil.nvl(filter.getValue()).trim();
                } else if (Constants.ENQ_SEL_DATE.equals(field)) {
                    dateText = ValidationUtil.nvl(filter.getValue()).trim();
                }
            }
        }
        LogUtil.info(CLASS_NAME, "setIds", "START", "customer=" + customerId + " date=" + dateText);

        String error = CustomerLimitEnquiryValidator.checkDate(Constants.ENQ_SEL_DATE, dateText);
        if (error != null) {
            throw new T24CoreException("", error);
        }
        LocalDate reportDate = ValidationUtil.isBlank(dateText) ? LocalDate.now() : ValidationUtil.toDate(dateText);

        List<String> lines;
        try {
            CustomerLimitEnquiryService service = new CustomerLimitEnquiryService(new CustomerLimitRepository(this));
            List<LimitSummaryRow> rows = service.getLimitSummary(customerId, reportDate);
            lines = CustomerLimitEnquiryView.toSummaryLines(rows);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "setIds", "ERROR", "customer=" + customerId + " date=" + reportDate, e);
            throw new T24CoreException("", Constants.ERR_NO_RECORDS);
        }

        LogUtil.info(CLASS_NAME, "setIds", "END", "rows=" + lines.size() + " elapsedMs="
                + (System.currentTimeMillis() - startTime));
        if (lines.isEmpty()) {
            throw new T24CoreException("", Constants.ERR_NO_RECORDS);
        }
        return lines;
    }
}
