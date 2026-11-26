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
 *               EB.OVERRIDE records to create (message text from HLSD):
 *                 EB-ABLE.DAILY.LIMIT.EXCEED    Daily credit/debit exceed for Customer & by &
 *                 EB-ABLE.MONTHLY.LIMIT.EXCEED  Monthly credit/debit exceed for Customer & by &
 *                 EB-ABLE.MAX.CIF.BAL.EXCEED    Overall credit/debit exceed for Customer & by &
 *                 EB-ABLE.CIF.LIMIT.NOT.DEFINED Customer limit not defined for Customer &
 *                                               (no EB.ALBE.CIF.LIMITS record, or a limit field is blank)
 *                 EB-ABLE.EXCH.RATE.MISSING     Exchange rate not available from & to &
 *                                               (transaction currency, limit currency)
 *
 *               Text format [ID,param1,param2] (R25 EB.HANDLE.OVERRIDES / EB.HANDLE.ERRORS:
 *               part 1 = id, the other parts fill the & placeholders in order). Parameters
 *               must not contain "," "[" or "]" - they are removed.
 *
 *               A T24 field holds ONE override (TField.setOverride replaces the value), so
 *               each override goes on its own AAA field:
 *                 EB-ABLE.DAILY.LIMIT.EXCEED    TXN.AMOUNT
 *                 EB-ABLE.MONTHLY.LIMIT.EXCEED  TXN.AMOUNT.LCY
 *                 EB-ABLE.MAX.CIF.BAL.EXCEED    ORIG.TXN.AMT
 *                 EB-ABLE.CIF.LIMIT.NOT.DEFINED CURRENCY
 *                 EB-ABLE.EXCH.RATE.MISSING     EFFECTIVE.DATE   (max CIF account currency only)
 *                 errors (EB-ALBE.*)            TXN.AMOUNT       (incl. EB-ALBE.EXCH.RATE.MISSING, D44)
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 * 22-Nov-2026   Development      texts(): overrides as a list
 * 22-Nov-2026   Development      Blank limit CCY: error EB-ALBE.CIF.LIMIT.CCY.MISSING (blocks)
 * 25-Nov-2026   Development      ids() for the RESULT log line
 * 26-Nov-2026   Development      D43: text [ID,p1,p2]; one override per AAA field (DEV: only the
 *                                last override per field was kept, & not filled)
 * 26-Nov-2026   Development      D44: amount not convertible -> ERROR EB-ALBE.EXCH.RATE.MISSING
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
            raiseError(activity.getTxnAmount(), Constants.ERR_EXCH_RATE_MISSING, request.getRateMissingCurrency(),
                    request.getLimitCurrency());
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
                    request.getRateMissingCurrency(), request.getLimitCurrency());
            raised++;
        }
        for (LimitBreach breach : request.getBreaches()) {
            raise(fieldFor(activity, breach.getOverrideId()), breach.getOverrideId(), request.getCustomerId(),
                    breach.getDifference().toPlainString());
            raised++;
        }
        LogUtil.detail(CLASS_NAME, "raiseFor", "END", "customer=" + request.getCustomerId() + " overridesRaised="
                + raised);
        return raised;
    }

    /** The AAA field an override is attached to: one field per override (a field keeps only one). */
    public static TField fieldFor(AaArrangementActivityRecord activity, String overrideId) {
        if (Constants.OVR_MONTHLY_LIMIT_EXCEED.equals(overrideId)) {
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

    /**
     * Every override the validation produced for this request, as override texts
     * ([ID,param1,param2]): "limit not defined" once, "no exchange rate", then one per
     * breached limit (same order as raiseFor).
     */
    public static List<String> texts(CustomerLimitDTO request) {
        List<String> texts = new ArrayList<>();
        if (request.isLimitCcyMissing()) {
            LogUtil.error(CLASS_NAME, "texts", "NO CCY", "customer=" + request.getCustomerId()
                    + " limit currency blank - returned as " + Constants.ERR_LIMIT_CCY_MISSING);
            texts.add(build(Constants.ERR_LIMIT_CCY_MISSING, request.getCustomerId()));
            return texts;
        }
        if (request.isRateBlocked()) {
            texts.add(build(Constants.ERR_EXCH_RATE_MISSING, request.getRateMissingCurrency(),
                    request.getLimitCurrency()));
            return texts;
        }
        if (request.isLimitMissing() || request.isLimitNotDefined()) {
            LogUtil.info(CLASS_NAME, "texts", "OVERRIDE", "customer=" + request.getCustomerId()
                    + " limit not defined"
                    + (request.isLimitMissing() ? " (no EB.ALBE.CIF.LIMITS record)"
                            : " (blank " + request.getUndefinedLimits() + ")"));
            texts.add(build(Constants.OVR_LIMIT_NOT_DEFINED, request.getCustomerId()));
        }
        if (request.isRateMissing()) {
            LogUtil.info(CLASS_NAME, "texts", "OVERRIDE", "customer=" + request.getCustomerId()
                    + " no exchange rate from " + request.getRateMissingCurrency() + " to "
                    + request.getLimitCurrency());
            texts.add(build(Constants.OVR_EXCH_RATE_MISSING, request.getRateMissingCurrency(),
                    request.getLimitCurrency()));
        }
        for (LimitBreach breach : request.getBreaches()) {
            LogUtil.info(CLASS_NAME, "texts", "OVERRIDE", "customer=" + request.getCustomerId() + " breach "
                    + breach.getMessage());
            texts.add(build(breach.getOverrideId(), request.getCustomerId(),
                    breach.getDifference().toPlainString()));
        }
        return texts;
    }

    /** Ids that raiseFor sends for this request, for the RESULT log line ("none" when nothing). */
    public static String ids(CustomerLimitDTO request) {
        if (request.isLimitCcyMissing()) {
            return "ERROR " + Constants.ERR_LIMIT_CCY_MISSING + " (input blocked)";
        }
        if (request.isRateBlocked()) {
            return "ERROR " + Constants.ERR_EXCH_RATE_MISSING + " (input blocked)";
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
