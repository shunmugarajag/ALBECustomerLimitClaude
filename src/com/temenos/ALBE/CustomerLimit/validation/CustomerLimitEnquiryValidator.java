package com.temenos.ALBE.CustomerLimit.validation;

import java.time.LocalDate;

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

    /** The transaction enquiry needs a customer. */
    public static String checkCustomerGiven(String customerId) {
        if (ValidationUtil.isBlank(customerId)) {
            LogUtil.info(CLASS_NAME, "checkCustomerGiven", "INVALID", "customer missing");
            return Constants.ERR_CUSTOMER_MANDATORY;
        }
        return null;
    }
}
