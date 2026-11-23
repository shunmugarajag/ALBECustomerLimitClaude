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

/*-----------------------------------------------------------------------------
 * Description : View layer: everything the user sees from the limit checks.
 *               Raises the HLSD customer limit overrides (and errors) on a field.
 *
 *               EB.OVERRIDE records to create (message text from HLSD):
 *                 EB-ALBE.DAILY.LIMIT.EXCEED    Daily credit/debit exceed for Customer & by &
 *                 EB-ALBE.MONTHLY.LIMIT.EXCEED  Monthly credit/debit exceed for Customer & by &
 *                 EB-ALBE.MAX.CIF.BAL.EXCEED    Overall credit/debit exceed for Customer & by &
 *                 EB-ALBE.CIF.LIMIT.NOT.DEFINED Customer limit not defined for Customer &
 *                                               (no EB.ALBE.CIF.LIMITS record, or a limit field is blank)
 *                 EB-ALBE.EXCH.RATE.MISSING     Exchange rate not available from & to &
 *                                               (transaction currency, limit currency)
 *
 *               Parameters are passed in the standard T24 format
 *               <id> FM <param1> VM <param2>, which fills the & placeholders in order.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 * 22-Nov-2026   Development      texts(): overrides as a list
 * 22-Nov-2026   Development      Blank limit CCY: error EB-ALBE.CIF.LIMIT.CCY.MISSING (blocks)
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitOverride {
    private static final String CLASS_NAME = "CustomerLimitOverride";

    private CustomerLimitOverride() {
        // Utility class: static methods only.
    }

    /**
     * Raises every override the validation produced for this request on the field:
     * "limit not defined" (missing record or blank limit) once, then one per breached limit.
     * @return number of overrides raised
     */
    public static int raiseFor(TField field, CustomerLimitDTO request) {
        if (request.isLimitCcyMissing()) {
            // Blank limit currency: an error, not an override - the transaction cannot be committed.
            raiseError(field, Constants.ERR_LIMIT_CCY_MISSING, request.getCustomerId());
            return 1;
        }
        List<String> texts = texts(request);
        for (String text : texts) {
            LogUtil.info(CLASS_NAME, "raiseFor", "OVERRIDE", "customer=" + request.getCustomerId() + " sent="
                    + readable(text));
            field.setOverride(text);
        }
        LogUtil.info(CLASS_NAME, "raiseFor", "END", "customer=" + request.getCustomerId() + " overridesRaised="
                + texts.size());
        return texts.size();
    }

    /**
     * Every override the validation produced for this request, as override texts
     * (<id> FM <param1> VM <param2>): "limit not defined" once, "no exchange rate", then one per
     * breached limit. Used by raiseFor.
     */
    public static List<String> texts(CustomerLimitDTO request) {
        List<String> texts = new ArrayList<>();
        if (request.isLimitCcyMissing()) {
            LogUtil.error(CLASS_NAME, "texts", "NO CCY", "customer=" + request.getCustomerId()
                    + " limit currency blank - returned as " + Constants.ERR_LIMIT_CCY_MISSING);
            texts.add(build(Constants.ERR_LIMIT_CCY_MISSING, request.getCustomerId()));
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

    /** Raises an override on the given field; the user can accept it and continue. */
    public static void raise(TField field, String overrideId, String... params) {
        String overrideText = build(overrideId, params);
        LogUtil.info(CLASS_NAME, "raise", "OVERRIDE", "override=" + overrideId + " params=" + Arrays.toString(params)
                + " sent=" + readable(overrideText));
        field.setOverride(overrideText);
    }

    /** Raises an error on the given field; the transaction cannot be committed. Not used by the limit checks. */
    public static void raiseError(TField field, String errorId, String... params) {
        String errorText = build(errorId, params);
        LogUtil.info(CLASS_NAME, "raiseError", "OVERRIDE", "error=" + errorId + " params=" + Arrays.toString(params)
                + " sent=" + readable(errorText));
        field.setError(errorText);
    }

    /** Shows FM as ^ and VM as ] so the exact text sent to T24 is readable in the log. */
    private static String readable(String text) {
        return text.replace(Constants.FM, '^').replace(Constants.VM, ']');
    }

    /** Builds <id> FM <param1> VM <param2> ...; just <id> when there are no parameters. */
    public static String build(String overrideId, String... params) {
        if (params == null || params.length == 0) {
            return overrideId;
        }
        StringBuilder sb = new StringBuilder(overrideId).append(Constants.FM);
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(Constants.VM);
            }
            sb.append(ValidationUtil.nvl(params[i]));
        }
        return sb.toString();
    }
}
