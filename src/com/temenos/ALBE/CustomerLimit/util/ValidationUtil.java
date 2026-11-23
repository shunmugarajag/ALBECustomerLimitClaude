package com.temenos.ALBE.CustomerLimit.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;

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
    private static final String CLASS_NAME = "ValidationUtil";

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
            LogUtil.error(CLASS_NAME, "toAmount", "INVALID", "invalid amount '" + value + "', using 0");
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
            LogUtil.error(CLASS_NAME, "toOptionalAmount", "INVALID", "invalid amount '" + value
                    + "', treated as blank");
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
            return LocalDate.parse(value.trim(), Constants.DATE_PARSE_FORMAT);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "toDate", "INVALID", "invalid date '" + value + "'");
            return null;
        }
    }

    /** YYYYMM text to YearMonth; null when blank or invalid (invalid is logged). */
    public static YearMonth toMonth(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return YearMonth.parse(value.trim(), Constants.MONTH_PARSE_FORMAT);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "toMonth", "INVALID", "invalid month '" + value + "'");
            return null;
        }
    }

    /** True when both currencies are given and equal (ignoring case and spaces). */
    public static boolean sameCurrency(String ccy1, String ccy2) {
        return !isBlank(ccy1) && !isBlank(ccy2) && ccy1.trim().equalsIgnoreCase(ccy2.trim());
    }

    /** ACTION part of an activity class id <PRODUCT.LINE>-<ACTION>-<PROPERTY.CLASS>; "" if not there. */
    public static String activityAction(String activityClass) {
        String[] parts = nvl(activityClass).trim().split(Constants.ACTIVITY_CLASS_SEPARATOR);
        return parts.length > Constants.ACTIVITY_CLASS_ACTION_POS ? parts[Constants.ACTIVITY_CLASS_ACTION_POS].trim()
                : "";
    }

    /** True when the activity class action moves money in (Constants.CREDIT_ACTIONS), any product line. */
    public static boolean isCreditActivityClass(String activityClass) {
        return contains(Constants.CREDIT_ACTIONS, activityAction(activityClass));
    }

    /** True when the activity class action moves money out (Constants.DEBIT_ACTIONS), any product line. */
    public static boolean isDebitActivityClass(String activityClass) {
        return contains(Constants.DEBIT_ACTIONS, activityAction(activityClass));
    }

    /** True when activities of this INITIATION.TYPE are counted; a blank type is counted. */
    public static boolean isCountedInitiationType(String initiationType) {
        return isBlank(initiationType) || contains(Constants.COUNTED_INITIATION_TYPES, initiationType.trim());
    }

    /**
     * Root of a transaction reference: cut at the first Constants.REFERENCE_CUT_CHARS character
     * ("FT26091ABCDE\\BNK" and "FT26091ABCDE;1" give "FT26091ABCDE"); "" if blank.
     */
    public static String rootReference(String reference) {
        String text = nvl(reference).trim();
        for (String cut : Constants.REFERENCE_CUT_CHARS) {
            int pos = text.indexOf(cut);
            if (pos >= 0) {
                text = text.substring(0, pos);
            }
        }
        return text.trim();
    }

    /** True when value (not blank) is one of the values (exact, case sensitive like T24 ids). */
    public static boolean contains(List<String> values, String value) {
        return !isBlank(value) && values.contains(value);
    }
}
