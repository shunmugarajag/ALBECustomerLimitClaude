package com.temenos.ALBE.CustomerLimit.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.model.LimitBreach;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.api.TField;
import com.temenos.t24.api.records.aaarrangementactivity.AaArrangementActivityRecord;

/*-----------------------------------------------------------------------------
 * Description : View layer: everything the user sees from the limit checks.
 *               Raises the HLSD customer limit overrides (and errors) on a field.
 *
 *               EB.OVERRIDE records (D46: ONE & each - a second & is not filled in DEV):
 *                 EB-ABLE.DAILY.DEBIT.LIMIT.EXCEED    Daily debit limit exceeded for Customer &
 *                 EB-ABLE.DAILY.CREDIT.LIMIT.EXCEED   Daily credit limit exceeded for Customer &
 *                 EB-ABLE.MONTHLY.DEBIT.LIMIT.EXCEED  Monthly debit limit exceeded for Customer &
 *                 EB-ABLE.MONTHLY.CREDIT.LIMIT.EXCEED Monthly credit limit exceeded for Customer &
 *                 EB-ABLE.MAX.CIF.BAL.EXCEED          Overall balance limit exceeded for Customer &
 *                     (& = "<customer> by <amount over>", e.g. "100128 by 12000.00")
 *                 EB-ABLE.CIF.LIMIT.NOT.DEFINED       Customer limit not defined for Customer &
 *                 EB-ABLE.EXCH.RATE.MISSING           Exchange rate not available &  (& = "from USD to EGP")
 *
 *               Text format [ID,param] (R25 EB.HANDLE.OVERRIDES / EB.HANDLE.ERRORS: part 1 = id,
 *               part 2 fills the &). "," "[" "]" are removed from the parameter.
 *
 *               A T24 field holds ONE override (TField.setOverride replaces the value), so
 *               each override goes on its own AAA field:
 *                 daily debit / credit       TXN.AMOUNT
 *                 monthly debit / credit     TXN.AMOUNT.LCY
 *                 max CIF                    ORIG.TXN.AMT
 *                 limit not defined          CURRENCY
 *                 exchange rate (override)   EFFECTIVE.DATE   (max CIF account currency only)
 *                 errors (EB-ALBE.*)         TXN.AMOUNT
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 * 22-Nov-2026   Development      Blank limit CCY: error EB-ALBE.CIF.LIMIT.CCY.MISSING (blocks)
 * 25-Nov-2026   Development      ids() for the RESULT log line
 * 26-Nov-2026   Development      D43: text [ID,p1,p2]; one override per AAA field (DEV: only the
 *                                last override per field was kept, & not filled)
 * 26-Nov-2026   Development      D44: amount not convertible -> ERROR EB-ALBE.EXCH.RATE.MISSING
 * 26-Nov-2026   Development      No CUSTOMER.ACCOUNT (max CIF) -> ERROR EB-ALBE.CUSTOMER.ACCOUNT.MISSING
 * 26-Nov-2026   Development      D46: one parameter per message; debit / credit override ids
 * 26-Nov-2026   Development      Clean rewrite (D47): texts() removed (ids() is the log form)
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitOverride {
    private static final String CLASS_NAME = "CustomerLimitOverride";

    private CustomerLimitOverride() {
        // Utility class: static methods only.
    }

    /**
     * Raises every override the validation produced for this request on the activity, each on its
     * own field (see fieldFor): "limit not defined" once, "no exchange rate", then one per breached limit.
     * @return number of overrides raised
     */
    public static int raiseFor(AaArrangementActivityRecord activity, CustomerLimitDTO request) {
        if (request.isLimitCcyMissing()) {
            // Blank limit currency: an error, not an override - the transaction cannot be committed.
            raiseError(activity.getTxnAmount(), Constants.ERR_LIMIT_CCY_MISSING, request.getCustomerId());
            return 1;
        }
        if (request.isRateBlocked()) {
            // D44: the amount cannot be converted to the limit currency - error, transaction blocked.
            raiseError(activity.getTxnAmount(), Constants.ERR_EXCH_RATE_MISSING, fromTo(request));
            return 1;
        }
        if (request.isCustomerAccountMissing()) {
            // Max CIF: no accounts in CUSTOMER.ACCOUNT - error, transaction blocked.
            raiseError(activity.getTxnAmount(), Constants.ERR_CUSTOMER_ACCOUNT_MISSING, request.getCustomerId());
            return 1;
        }
        int raised = 0;
        if (request.isLimitMissing() || request.isLimitNotDefined()) {
            raise(fieldFor(activity, Constants.OVR_LIMIT_NOT_DEFINED), Constants.OVR_LIMIT_NOT_DEFINED,
                    request.getCustomerId());
            raised++;
        }
        if (request.isRateMissing()) {
            raise(fieldFor(activity, Constants.OVR_EXCH_RATE_MISSING), Constants.OVR_EXCH_RATE_MISSING,
                    fromTo(request));
            raised++;
        }
        for (LimitBreach breach : request.getBreaches()) {
            raise(fieldFor(activity, breach.getOverrideId()), breach.getOverrideId(), customerBy(request, breach));
            raised++;
        }
        return raised;
    }

    /** The AAA field an override is attached to: one field per override (a field keeps only one). */
    public static TField fieldFor(AaArrangementActivityRecord activity, String overrideId) {
        if (Constants.OVR_MONTHLY_CREDIT_LIMIT_EXCEED.equals(overrideId)
                || Constants.OVR_MONTHLY_DEBIT_LIMIT_EXCEED.equals(overrideId)) {
            return activity.getTxnAmountLcy();
        }
        if (Constants.OVR_MAX_CIF_BAL_EXCEED.equals(overrideId)) {
            return activity.getOrigTxnAmt();
        }
        if (Constants.OVR_LIMIT_NOT_DEFINED.equals(overrideId)) {
            return activity.getCurrency();
        }
        if (Constants.OVR_EXCH_RATE_MISSING.equals(overrideId)) {
            return activity.getEffectiveDate();
        }
        return activity.getTxnAmount();   // daily limit and anything else
    }

    /** The one parameter of a limit-exceeded override: "<customer> by <amount over>" (D46). */
    public static String customerBy(CustomerLimitDTO request, LimitBreach breach) {
        return request.getCustomerId() + Constants.PARAM_BY + breach.getDifference().toPlainString();
    }

    /** The one parameter of an exchange-rate message: "from <currency> to <limit currency>" (D46). */
    public static String fromTo(CustomerLimitDTO request) {
        return Constants.PARAM_FROM + ValidationUtil.nvl(request.getRateMissingCurrency()) + Constants.PARAM_TO
                + ValidationUtil.nvl(request.getLimitCurrency());
    }

    /** Ids that raiseFor sends for this request, for the RESULT log line ("none" when nothing). */
    public static String ids(CustomerLimitDTO request) {
        if (request.isLimitCcyMissing()) {
            return "ERROR " + Constants.ERR_LIMIT_CCY_MISSING + " (input blocked)";
        }
        if (request.isRateBlocked()) {
            return "ERROR " + Constants.ERR_EXCH_RATE_MISSING + " (input blocked)";
        }
        if (request.isCustomerAccountMissing()) {
            return "ERROR " + Constants.ERR_CUSTOMER_ACCOUNT_MISSING + " (input blocked)";
        }
        List<String> ids = new ArrayList<>();
        if (request.isLimitMissing() || request.isLimitNotDefined()) {
            ids.add(Constants.OVR_LIMIT_NOT_DEFINED);
        }
        if (request.isRateMissing()) {
            ids.add(Constants.OVR_EXCH_RATE_MISSING);
        }
        for (LimitBreach breach : request.getBreaches()) {
            ids.add(breach.getOverrideId());
        }
        return ids.isEmpty() ? "none" : String.join(", ", ids);
    }

    /** Raises an override on the given field; the user can accept it and continue. */
    public static void raise(TField field, String overrideId, String... params) {
        String overrideText = build(overrideId, params);
        LogUtil.info(CLASS_NAME, "raise", "OVERRIDE", "override=" + overrideId + " params=" + Arrays.toString(params)
                + " sent=" + overrideText);
        field.setOverride(overrideText);
    }

    /** Raises an error on the given field; the transaction cannot be committed. */
    public static void raiseError(TField field, String errorId, String... params) {
        String errorText = build(errorId, params);
        LogUtil.info(CLASS_NAME, "raiseError", "ERROR", "error=" + errorId + " params=" + Arrays.toString(params)
                + " sent=" + errorText);
        field.setError(errorText);
    }

    /**
     * Builds the text T24 expects: [ID,param1,param2]; just ID when there are no parameters.
     * "," "[" "]" are removed from the parameters (they would split the text).
     */
    public static String build(String messageId, String... params) {
        if (params == null || params.length == 0) {
            return messageId;
        }
        StringBuilder sb = new StringBuilder(Constants.MESSAGE_TEXT_START).append(messageId);
        for (String param : params) {
            sb.append(Constants.MESSAGE_TEXT_SEPARATOR).append(clean(param));
        }
        return sb.append(Constants.MESSAGE_TEXT_END).toString();
    }

    /** Parameter without the characters that split the text. */
    private static String clean(String param) {
        return ValidationUtil.nvl(param).replace(Constants.MESSAGE_TEXT_SEPARATOR, "")
                .replace(Constants.MESSAGE_TEXT_START, "").replace(Constants.MESSAGE_TEXT_END, "").trim();
    }
}
