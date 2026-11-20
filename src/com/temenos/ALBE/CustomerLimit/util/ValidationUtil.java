package com.temenos.ALBE.CustomerLimit.util;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;

/*-----------------------------------------------------------------------------
 * Description : Common value checks and conversions used across the layers, so each
 *               rule exists once: blank text, null to "", amount parsing, date parsing,
 *               currency comparison. No business rules here.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class ValidationUtil {

    private static final Logger logger = LoggerFactory.getLogger(Constants.LOGGER_NAME);
    private static final String LOG_PREFIX = "ValidationUtil:";

    private ValidationUtil() {
        // Static helpers only.
    }

    /** True for null, "" or only spaces. */
    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** "" for null, otherwise the value unchanged. */
    public static String nvl(String value) {
        return value == null ? "" : value;
    }

    /** True when the amount is not null and greater than zero. */
    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }

    /** T24 amount text to BigDecimal; blank or invalid gives 0 (invalid is logged). */
    public static BigDecimal toAmount(String value) {
        if (isBlank(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            logger.error(LOG_PREFIX + "toAmount invalid amount '" + value + "', using 0");
            return BigDecimal.ZERO;
        }
    }

    /**
     * T24 amount text to BigDecimal where blank has a meaning (e.g. a limit not defined):
     * blank gives null; invalid is logged and also gives null.
     */
    public static BigDecimal toOptionalAmount(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            logger.error(LOG_PREFIX + "toOptionalAmount invalid amount '" + value + "', treated as blank");
            return null;
        }
    }

    /** Amount to text for a T24 field; "" for null. */
    public static String toText(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    /** YYYYMMDD text to LocalDate; null when blank or invalid (invalid is logged). */
    public static LocalDate toDate(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), Constants.DAILY_ID_FORMAT);
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "toDate invalid date '" + value + "'");
            return null;
        }
    }

    /** True when both currencies are given and equal (ignoring case and spaces). */
    public static boolean sameCurrency(String ccy1, String ccy2) {
        return !isBlank(ccy1) && !isBlank(ccy2) && ccy1.trim().equalsIgnoreCase(ccy2.trim());
    }
}
