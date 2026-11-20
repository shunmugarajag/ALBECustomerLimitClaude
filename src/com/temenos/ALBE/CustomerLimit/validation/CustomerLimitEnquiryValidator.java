package com.temenos.ALBE.CustomerLimit.validation;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;

/*-----------------------------------------------------------------------------
 * Description : Checks the selection values of the customer limit enquiries once, in the
 *               controller. Each method returns the EB.ERROR id to raise, or null when the
 *               value is fine; the controller raises the error.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitEnquiryValidator {
    private static final String CLASS_NAME = "CustomerLimitEnquiryValidator";

    private CustomerLimitEnquiryValidator() {
        // Static checks only.
    }

    /** A date selection must be blank or a valid YYYYMMDD date. */
    public static String checkDate(String selectionField, String value) {
        if (ValidationUtil.isBlank(value) || ValidationUtil.toDate(value) != null) {
            return null;
        }
        LogUtil.info(CLASS_NAME, "checkDate", "INVALID", selectionField + "=" + value);
        return Constants.ERR_INVALID_DATE;
    }

    /** DATE.FROM must not be after DATE.TO (each may be blank). */
    public static String checkDateRange(String fromDate, String toDate) {
        LocalDate from = ValidationUtil.toDate(fromDate);
        LocalDate to = ValidationUtil.toDate(toDate);
        if (from != null && to != null && from.isAfter(to)) {
            LogUtil.info(CLASS_NAME, "checkDateRange", "INVALID", "from=" + fromDate + " after to=" + toDate);
            return Constants.ERR_DATE_RANGE;
        }
        return null;
    }

    /** A month selection must be blank or a valid YYYYMM month. */
    public static String checkMonth(String selectionField, String value) {
        if (ValidationUtil.isBlank(value) || ValidationUtil.toMonth(value) != null) {
            return null;
        }
        LogUtil.info(CLASS_NAME, "checkMonth", "INVALID", selectionField + "=" + value);
        return Constants.ERR_INVALID_MONTH;
    }

    /** MONTH.FROM must not be after MONTH.TO (each may be blank). */
    public static String checkMonthRange(String fromMonth, String toMonth) {
        YearMonth from = ValidationUtil.toMonth(fromMonth);
        YearMonth to = ValidationUtil.toMonth(toMonth);
        if (from != null && to != null && from.isAfter(to)) {
            LogUtil.info(CLASS_NAME, "checkMonthRange", "INVALID", "from=" + fromMonth + " after to=" + toMonth);
            return Constants.ERR_DATE_RANGE;
        }
        return null;
    }

    /** The period report needs exactly one of: a date range or a month range. */
    public static String checkPeriodGiven(boolean dateGiven, boolean monthGiven) {
        if (dateGiven == monthGiven) {
            LogUtil.info(CLASS_NAME, "checkPeriodGiven", "INVALID", "dateGiven=" + dateGiven
                    + " monthGiven=" + monthGiven + " (give one of them)");
            return Constants.ERR_PERIOD_MANDATORY;
        }
        return null;
    }

    /** A date range may cover at most Constants.ENQ_MAX_DAYS days. */
    public static String checkDayCount(LocalDate from, LocalDate to) {
        if (from != null && to != null && ChronoUnit.DAYS.between(from, to) + 1 > Constants.ENQ_MAX_DAYS) {
            LogUtil.info(CLASS_NAME, "checkDayCount", "INVALID", from + " to " + to + " is more than "
                    + Constants.ENQ_MAX_DAYS + " days");
            return Constants.ERR_RANGE_TOO_LONG;
        }
        return null;
    }

    /** A month range may cover at most Constants.ENQ_MAX_MONTHS months. */
    public static String checkMonthCount(YearMonth from, YearMonth to) {
        if (from != null && to != null && ChronoUnit.MONTHS.between(from, to) + 1 > Constants.ENQ_MAX_MONTHS) {
            LogUtil.info(CLASS_NAME, "checkMonthCount", "INVALID", from + " to " + to + " is more than "
                    + Constants.ENQ_MAX_MONTHS + " months");
            return Constants.ERR_RANGE_TOO_LONG;
        }
        return null;
    }

    /** The transaction report needs a customer or at least one date (never the whole file). */
    public static String checkCustomerOrDate(String customerId, String fromDate, String toDate) {
        if (ValidationUtil.isBlank(customerId) && ValidationUtil.isBlank(fromDate) && ValidationUtil.isBlank(toDate)) {
            LogUtil.info(CLASS_NAME, "checkCustomerOrDate", "INVALID", "no customer and no date");
            return Constants.ERR_CUSTOMER_OR_DATE;
        }
        return null;
    }

    /** OVER.LIMIT selection: Y means only over-limit rows; anything else means all rows. */
    public static boolean isYes(String value) {
        return Constants.ENQ_YES.equalsIgnoreCase(ValidationUtil.nvl(value).trim());
    }
}
