package com.temenos.ALBE.CustomerLimit.override;

import java.util.Arrays;

import com.temenos.api.TField;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;

/*-----------------------------------------------------------------------------
 * Description : Raises the HLSD customer limit overrides and errors on a field.
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
public final class CustomerLimitOverride {

    private static final Logger logger = LoggerFactory.getLogger("L3API");
    private static final String LOG_PREFIX = "CustomerLimitOverride:";

    /** Override: daily credit/debit limit exceeded (params: customer id, difference). */
    public static final String DAILY_LIMIT_EXCEED = "EB-ALBE.DAILY.LIMIT.EXCEED";
    /** Override: monthly credit/debit limit exceeded (params: customer id, difference). */
    public static final String MONTHLY_LIMIT_EXCEED = "EB-ALBE.MONTHLY.LIMIT.EXCEED";
    /** Override: maximum overall CIF balance exceeded (params: customer id, difference). */
    public static final String MAX_CIF_BAL_EXCEED = "EB-ALBE.MAX.CIF.BAL.EXCEED";

    /** Override: no EB.ALBE.CIF.LIMIT record, or one of its limit fields is blank (param: customer id). */
    public static final String LIMIT_NOT_DEFINED = "EB-ALBE.CIF.LIMIT.NOT.DEFINED";

    // T24 field mark / value mark separating the id and its & parameters
    private static final char FM = '\u00FE';
    private static final char VM = '\u00FD';

    private CustomerLimitOverride() {
        // Utility class: static methods only.
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
        return text.replace(FM, '^').replace(VM, ']');
    }

    /** Builds <id> FM <param1> VM <param2> ...; just <id> when there are no parameters. */
    public static String build(String overrideId, String... params) {
        if (params == null || params.length == 0) {
            return overrideId;
        }
        StringBuilder sb = new StringBuilder(overrideId).append(FM);
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(VM);
            }
            sb.append(params[i] == null ? "" : params[i]);
        }
        return sb.toString();
    }
}
