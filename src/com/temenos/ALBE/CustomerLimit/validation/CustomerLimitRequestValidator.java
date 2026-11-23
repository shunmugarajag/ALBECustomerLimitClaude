package com.temenos.ALBE.CustomerLimit.validation;

import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;

/*-----------------------------------------------------------------------------
 * Description : Single place where the input taken from T24 is checked, once, by the
 *               controllers right after the request is built. A request that passes is
 *               trusted by the services (they do not check these values again).
 *
 *               Daily / monthly : customer, AAA id, date present;
 *                                 exactly one of credit / debit, and it is > 0.
 *               Maximum CIF     : customer present; credit > 0.
 *               (A blank currency is not rejected: it is treated as the limit currency.)
 *               Limit creation  : customer id present.
 *
 *               Business outcomes (no limit record, blank limit, no rate, breach) are NOT
 *               checked here: they are rules handled by the services.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitRequestValidator {
    private static final String CLASS_NAME = "CustomerLimitRequestValidator";

    private CustomerLimitRequestValidator() {
        // Static checks only.
    }

    /** Request for the daily / monthly check and utilisation (DailyMonthlyLimitController). */
    public static boolean isValidForDailyMonthly(CustomerLimitDTO request) {
        String reason = commonReason(request);
        if (reason == null && ValidationUtil.isBlank(request.getTransactionId())) {
            reason = "AAA id missing";
        }
        if (reason == null && request.getTransactionDate() == null) {
            reason = "transaction date missing";
        }
        if (reason == null) {
            boolean credit = ValidationUtil.isPositive(request.getCreditAmount());
            boolean debit = ValidationUtil.isPositive(request.getDebitAmount());
            if (!credit && !debit) {
                reason = "amount is zero or negative";
            } else if (credit && debit) {
                reason = "both credit and debit amounts set";
            }
        }
        return result("isValidForDailyMonthly", request, reason);
    }

    /** Request for the maximum CIF balance check (MaximumCifLimitController). */
    public static boolean isValidForMaxCif(CustomerLimitDTO request) {
        String reason = commonReason(request);
        if (reason == null && !ValidationUtil.isPositive(request.getCreditAmount())) {
            reason = "credit amount is zero or negative";
        }
        return result("isValidForMaxCif", request, reason);
    }

    /** Customer id for the limit creation / deletion (CustomerLimitCreationController). */
    public static boolean isValidCustomerId(String customerId) {
        if (ValidationUtil.isBlank(customerId)) {
            LogUtil.info(CLASS_NAME, "isValidCustomerId", "INVALID", "customer id missing");
            return false;
        }
        LogUtil.info(CLASS_NAME, "isValidCustomerId", "OK", "customer=" + customerId);
        return true;
    }

    /** Checks shared by both limit requests; null when all present. */
    private static String commonReason(CustomerLimitDTO request) {
        if (request == null) {
            return "request not built";
        }
        if (ValidationUtil.isBlank(request.getCustomerId())) {
            return "customer missing";
        }
        return null;
    }

    private static boolean result(String check, CustomerLimitDTO request, String reason) {
        if (reason != null) {
            LogUtil.info(CLASS_NAME, check, "INVALID", "reason=" + reason + " request=" + request);
            return false;
        }
        LogUtil.info(CLASS_NAME, check, "OK", "customer=" + request.getCustomerId() + " txn="
                + request.getTransactionId());
        return true;
    }

}
