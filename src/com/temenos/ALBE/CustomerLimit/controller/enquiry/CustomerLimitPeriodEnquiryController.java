package com.temenos.ALBE.CustomerLimit.controller.enquiry;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.LimitPeriodRow;
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
 * EB.API      : ALBE.E.CUSTOMER.LIMIT.PERIOD
 * Attached to : STANDARD.SELECTION > NOFILE.ALBE.CUSTOMER.LIMIT.PERIOD
 * Description : Customer limit period report: limit, used and available per customer
 *               per day (date range) or per month (month range). Only days / months with
 *               usage are listed.
 *               Selection:
 *                 DATE.FROM / DATE.TO    YYYYMMDD - daily rows      } give exactly one of
 *                 MONTH.FROM / MONTH.TO  YYYYMM   - monthly rows    } the two ranges
 *                 CUSTOMER               optional, blank = all customers
 *                 OVER.LIMIT             Y = only rows above a limit
 *               A missing TO is taken as FROM (and the other way round). At most
 *               Constants.ENQ_MAX_DAYS days or Constants.ENQ_MAX_MONTHS months.
 *               Columns: see CustomerLimitEnquiryView.PERIOD_COLUMNS.
 *               No business rules and no DB access here.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitPeriodEnquiryController extends Enquiry {
    private static final String CLASS_NAME = "CustomerLimitPeriodEnquiryController";

    @Override
    public List<String> setIds(List<FilterCriteria> filterCriteria, EnquiryContext enquiryContext) {
        long startTime = System.currentTimeMillis();

        // Selection
        String customerId = "";
        String dateFrom = "";
        String dateTo = "";
        String monthFrom = "";
        String monthTo = "";
        String overLimit = "";
        if (filterCriteria != null) {
            for (FilterCriteria filter : filterCriteria) {
                String field = filter.getFieldname();
                String value = ValidationUtil.nvl(filter.getValue()).trim();
                if (Constants.ENQ_SEL_CUSTOMER.equals(field)) {
                    customerId = value;
                } else if (Constants.ENQ_SEL_DATE_FROM.equals(field)) {
                    dateFrom = value;
                } else if (Constants.ENQ_SEL_DATE_TO.equals(field)) {
                    dateTo = value;
                } else if (Constants.ENQ_SEL_MONTH_FROM.equals(field)) {
                    monthFrom = value;
                } else if (Constants.ENQ_SEL_MONTH_TO.equals(field)) {
                    monthTo = value;
                } else if (Constants.ENQ_SEL_OVER_LIMIT.equals(field)) {
                    overLimit = value;
                }
            }
        }
        LogUtil.info(CLASS_NAME, "setIds", "START", "customer=" + customerId + " date=" + dateFrom + "-" + dateTo
                + " month=" + monthFrom + "-" + monthTo + " overLimit=" + overLimit);

        boolean daily = !ValidationUtil.isBlank(dateFrom) || !ValidationUtil.isBlank(dateTo);
        boolean monthly = !ValidationUtil.isBlank(monthFrom) || !ValidationUtil.isBlank(monthTo);
        // A missing end of the range is the other end (one day / one month).
        dateFrom = ValidationUtil.isBlank(dateFrom) ? dateTo : dateFrom;
        dateTo = ValidationUtil.isBlank(dateTo) ? dateFrom : dateTo;
        monthFrom = ValidationUtil.isBlank(monthFrom) ? monthTo : monthFrom;
        monthTo = ValidationUtil.isBlank(monthTo) ? monthFrom : monthTo;

        String error = CustomerLimitEnquiryValidator.checkPeriodGiven(daily, monthly);
        if (error == null && daily) {
            error = firstError(
                    CustomerLimitEnquiryValidator.checkDate(Constants.ENQ_SEL_DATE_FROM, dateFrom),
                    CustomerLimitEnquiryValidator.checkDate(Constants.ENQ_SEL_DATE_TO, dateTo),
                    CustomerLimitEnquiryValidator.checkDateRange(dateFrom, dateTo));
            if (error == null) {
                error = CustomerLimitEnquiryValidator.checkDayCount(ValidationUtil.toDate(dateFrom),
                        ValidationUtil.toDate(dateTo));
            }
        }
        if (error == null && monthly) {
            error = firstError(
                    CustomerLimitEnquiryValidator.checkMonth(Constants.ENQ_SEL_MONTH_FROM, monthFrom),
                    CustomerLimitEnquiryValidator.checkMonth(Constants.ENQ_SEL_MONTH_TO, monthTo),
                    CustomerLimitEnquiryValidator.checkMonthRange(monthFrom, monthTo));
            if (error == null) {
                error = CustomerLimitEnquiryValidator.checkMonthCount(ValidationUtil.toMonth(monthFrom),
                        ValidationUtil.toMonth(monthTo));
            }
        }
        if (error != null) {
            throw new T24CoreException("", error);
        }

        List<String> lines;
        try {
            CustomerLimitEnquiryService service = new CustomerLimitEnquiryService(new CustomerLimitRepository(this));
            boolean overLimitOnly = CustomerLimitEnquiryValidator.isYes(overLimit);
            List<LimitPeriodRow> rows;
            if (daily) {
                LocalDate from = ValidationUtil.toDate(dateFrom);
                LocalDate to = ValidationUtil.toDate(dateTo);
                rows = service.getDailyRows(customerId, from, to, overLimitOnly);
            } else {
                YearMonth from = ValidationUtil.toMonth(monthFrom);
                YearMonth to = ValidationUtil.toMonth(monthTo);
                rows = service.getMonthlyRows(customerId, from, to, overLimitOnly);
            }
            lines = CustomerLimitEnquiryView.toPeriodLines(rows);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "setIds", "ERROR", "customer=" + customerId, e);
            throw new T24CoreException("", Constants.ERR_NO_RECORDS);
        }

        LogUtil.info(CLASS_NAME, "setIds", "END", "rows=" + lines.size() + " elapsedMs="
                + (System.currentTimeMillis() - startTime));
        if (lines.isEmpty()) {
            throw new T24CoreException("", Constants.ERR_NO_RECORDS);
        }
        return lines;
    }

    /** The first non-null error id, or null. */
    private String firstError(String... errors) {
        for (String error : errors) {
            if (error != null) {
                return error;
            }
        }
        return null;
    }
}
