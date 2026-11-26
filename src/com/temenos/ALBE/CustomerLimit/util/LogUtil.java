package com.temenos.ALBE.CustomerLimit.util;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;

/*-----------------------------------------------------------------------------
 * Description : One place that writes every log line for Customer Limit, at INFO level only
 *               (user, 26-Nov), in one format:
 *
 *                 ALBE.CUSTOMER.LIMIT | <Class>.<method> | <EVENT> | details
 *
 *               e.g. ALBE.CUSTOMER.LIMIT | CustomerLimitActivityController.validateRecord | RESULT | INPUT ...
 *
 *               Events tell the kind of line: ACTIVITY / ACTION / RESULT (plain-language outcome of a
 *               call), READ / WRITE / FOUND / NOT FOUND / UPDATED / CALC (trace), OVERRIDE / ERROR
 *               (what was raised), FAILED / BLOCKED (something went wrong - grep these first).
 *               In api.log the lines go to DIAGNOSTIC.API: set it to INFO to see them.
 *
 *               Exceptions are written as ExceptionClass: message @ first stack frame.
 *               Logging never throws, so it can never break a transaction.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 * 25-Nov-2026   Development      result(): plain-language RESULT line per step
 * 26-Nov-2026   Development      Clean rewrite (D47): info only - error / detail / result removed (user)
 *-----------------------------------------------------------------------------
 */
public class LogUtil {

    private static final Logger logger = LoggerFactory.getLogger(Constants.LOGGER_NAME);

    private LogUtil() {
        // Static helpers only.
    }

    /** One log line. */
    public static void info(String className, String method, String event, Object details) {
        write(className, method, event, details, null);
    }

    /** One log line with the exception that goes with it. */
    public static void info(String className, String method, String event, Object details, Exception e) {
        write(className, method, event, details, e);
    }

    private static void write(String className, String method, String event, Object details, Exception e) {
        try {
            StringBuilder line = new StringBuilder(160);
            line.append(Constants.LOG_APP_TAG).append(Constants.LOG_SEPARATOR)
                    .append(className).append('.').append(method).append(Constants.LOG_SEPARATOR)
                    .append(event).append(Constants.LOG_SEPARATOR)
                    .append(details == null ? "" : String.valueOf(details).trim());
            if (e != null) {
                line.append(Constants.LOG_SEPARATOR).append(describe(e));
            }
            logger.info(line.toString());
        } catch (Exception ignored) {
            // A logging problem must never stop the transaction.
        }
    }

    /** ExceptionClass: message @ Class.method(File:line) */
    private static String describe(Exception e) {
        StringBuilder sb = new StringBuilder(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
        StackTraceElement[] trace = e.getStackTrace();
        if (trace != null && trace.length > 0) {
            sb.append(" @ ").append(trace[0]);
        }
        return sb.toString();
    }
}
