package com.temenos.ALBE.CustomerLimit.view;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import java.util.Arrays;

import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.model.LimitBreach;
import com.temenos.api.TField;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;

/*-----------------------------------------------------------------------------
 * Description : View layer: everything the user sees from the limit checks.
 *               Raises the HLSD customer limit overrides (and errors) on a field.
 *
 *               EB.OVERRIDE records to create (message text from HLSD):
 *                 EB-ALBE.DAILY.LIMIT.EXCEED    Daily credit/debit exceed for Customer & by &
 *                 EB-ALBE.MONTHLY.LIMIT.EXCEED  Monthly credit/debit exceed for Customer & by &
 *                 EB-ALBE.MAX.CIF.BAL.EXCEED    Overall credit/debit exceed for Customer & by &
 *                 EB-ALBE.CIF.LIMIT.NOT.DEFINED Customer limit not defined for Customer &
 *                                               (no EB.ALBE.CIF.LIMIT record, or a limit field is blank)
 *
 *               Parameters are passed in the standard T24 format
 *               <id> FM <param1> VM <param2>, which fills the & placeholders in order.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitOverride {

    private static final Logger logger = LoggerFactory.getLogger(Constants.LOGGER_NAME);
    private static final String LOG_PREFIX = "CustomerLimitOverride:";

    private CustomerLimitOverride() {
        // Utility class: static methods only.
    }

    /**
     * Raises every override the validation produced for this request on the field:
     * "limit not defined" (missing record or blank limit) once, then one per breached limit.
     * @return number of overrides raised
     */
    public static int raiseFor(TField field, CustomerLimitDTO request) {
        int raised = 0;
        if (request.isLimitMissing() || request.isLimitNotDefined()) {
            logger.info(LOG_PREFIX + "raiseFor customer=" + request.getCustomerId() + " limit not defined"
                    + (request.isLimitMissing() ? " (no EB.ALBE.CIF.LIMIT record)"
                            : " (blank " + request.getUndefinedLimits() + ")"));
            raise(field, Constants.OVR_LIMIT_NOT_DEFINED, request.getCustomerId());
            raised++;
        }
        for (LimitBreach breach : request.getBreaches()) {
            logger.info(LOG_PREFIX + "raiseFor customer=" + request.getCustomerId() + " breach "
                    + breach.getMessage());
            raise(field, breach.getOverrideId(), request.getCustomerId(), breach.getDifference().toPlainString());
            raised++;
        }
        logger.info(LOG_PREFIX + "raiseFor customer=" + request.getCustomerId() + " overridesRaised=" + raised);
        return raised;
    }

    /** Raises an override on the given field; the user can accept it and continue. */
    public static void raise(TField field, String overrideId, String... params) {
        String overrideText = build(overrideId, params);
        logger.info(LOG_PREFIX + "raise override=" + overrideId + " params=" + Arrays.toString(params)
                + " sent=" + readable(overrideText));
        field.setOverride(overrideText);
    }

    /** Raises an error on the given field; the transaction cannot be committed. Not used by the limit checks. */
    public static void raiseError(TField field, String errorId, String... params) {
        String errorText = build(errorId, params);
        logger.info(LOG_PREFIX + "raiseError error=" + errorId + " params=" + Arrays.toString(params)
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
            sb.append(params[i] == null ? "" : params[i]);
        }
        return sb.toString();
    }
}
