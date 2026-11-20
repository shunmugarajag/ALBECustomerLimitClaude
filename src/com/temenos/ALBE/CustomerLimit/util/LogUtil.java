package com.temenos.ALBE.CustomerLimit.util;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;

/*-----------------------------------------------------------------------------
 * Description : One place that writes every log line for Customer Limit, in one format:
 *
 *                 ALBE.CUSTOMER.LIMIT | <Class>.<method> | <EVENT> | key=value key=value
 *
 *               e.g. ALBE.CUSTOMER.LIMIT | DailyMonthlyLimitService.validate | BREACH | customer=100234 ...
 *
 *               Levels
 *                 info   - normal flow: START, END, SKIP, OK, FOUND, NOT FOUND, WRITTEN, BREACH, STEP
 *                 error  - something failed or data is wrong: FAILED, ERROR, INVALID
 *                 detail - low-level DB trace (READ / WRITE / DELETE before the call); written at
 *                          info level only when Constants.LOG_DETAIL_ENABLED is true
 *
 *               Exceptions are written as ExceptionClass: message @ first stack frame.
 *               Logging never throws, so it can never break a transaction.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class LogUtil {

    private static final Logger logger = LoggerFactory.getLogger(Constants.LOGGER_NAME);

    private LogUtil() {
        // Static helpers only.
    }

    /** Normal flow step. */
    public static void info(String className, String method, String event, Object details) {
        write(false, className, method, event, details, null);
    }

    /** Normal flow step that also carries an exception (expected case, e.g. record not found). */
    public static void info(String className, String method, String event, Object details, Exception e) {
        write(false, className, method, event, details, e);
    }

    /** Failure or bad data. */
    public static void error(String className, String method, String event, Object details) {
        write(true, className, method, event, details, null);
    }

    /** Failure with the exception that caused it. */
    public static void error(String className, String method, String event, Object details, Exception e) {
        write(true, className, method, event, details, e);
    }

    /** Low-level DB trace; switched off in production with Constants.LOG_DETAIL_ENABLED. */
    public static void detail(String className, String method, String event, Object details) {
        if (Constants.LOG_DETAIL_ENABLED) {
            write(false, className, method, event, details, null);
        }
    }

    private static void write(boolean isError, String className, String method, String event, Object details,
            Exception e) {
        try {
            StringBuilder line = new StringBuilder(160);
            line.append(Constants.LOG_APP_TAG).append(Constants.LOG_SEPARATOR)
                    .append(className).append('.').append(method).append(Constants.LOG_SEPARATOR)
                    .append(event).append(Constants.LOG_SEPARATOR)
                    .append(details == null ? "" : String.valueOf(details).trim());
            if (e != null) {
                line.append(Constants.LOG_SEPARATOR).append(describe(e));
            }
            if (isError) {
                logger.error(line.toString());
            } else {
                logger.info(line.toString());
            }
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
