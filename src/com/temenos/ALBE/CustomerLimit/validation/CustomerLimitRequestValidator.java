package com.temenos.ALBE.CustomerLimit.validation;

import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;

/*-----------------------------------------------------------------------------
 * Description : Single place where the input taken from T24 is checked, once, by the
 *               controllers right after the request is built. A request that passes is
 *               trusted by the services (they do not check these values again).
 *
 *               Limit check    : customer, transaction key, date present;
 *                                exactly one of credit / debit, and it is > 0.
 *                                (A blank currency is not rejected: it is treated as the limit currency.)
 *               Limit creation : customer id present.
 *
 *               Business outcomes (no limit record, blank limit, no rate, breach) are NOT
 *               checked here: they are rules handled by the services.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 * 25-Nov-2026   Development      OK lines moved to detail (RESULT lines tell the outcome)
 * 26-Nov-2026   Development      Clean rewrite (D47): one isValid for the single routine
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitRequestValidator {
    private static final String CLASS_NAME = "CustomerLimitRequestValidator";

    private CustomerLimitRequestValidator() {
        // Static checks only.
    }

    /**
     * Request for the customer limit check and count (CustomerLimitEngine): customer, transaction key,
     * date present; exactly one of credit / debit, and it is > 0.
     */
    public static boolean isValid(CustomerLimitDTO request) {
        String reason = null;
        if (request == null) {
            return result(null, "request not built");
        }
        if (ValidationUtil.isBlank(request.getCustomerId())) {
            reason = "customer missing";
        } else if (ValidationUtil.isBlank(request.getTransactionId())) {
            reason = "transaction key missing";
        } else if (request.getTransactionDate() == null) {
            reason = "transaction date missing";
        } else {
            boolean credit = ValidationUtil.isPositive(request.getCreditAmount());
            boolean debit = ValidationUtil.isPositive(request.getDebitAmount());
            if (!credit && !debit) {
                reason = "amount is zero or negative";
            } else if (credit && debit) {
                reason = "both credit and debit amounts set";
            }
        }
        return result(request, reason);
    }

    /** Customer id for the limit creation / deletion (CustomerLimitCreationController). */
    public static boolean isValidCustomerId(String customerId) {
        if (ValidationUtil.isBlank(customerId)) {
            LogUtil.info(CLASS_NAME, "isValidCustomerId", "INVALID", "customer id missing");
            return false;
        }
        return true;
    }

    private static boolean result(CustomerLimitDTO request, String reason) {
        if (reason != null) {
            LogUtil.info(CLASS_NAME, "isValid", "INVALID", "reason=" + reason + " request=" + request);
            return false;
        }
        return true;
    }

}
