package com.temenos.ALBE.CustomerLimit.constants;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/*-----------------------------------------------------------------------------
 * Description : All constants of the customer limit routines in one place.
 *               Not to be instantiated or extended.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public final class Constants {

    private Constants() {
        // Constants only.
    }

    // -------------------------------------------------------------------------
    // Logging
    // -------------------------------------------------------------------------
    /** Logger used by all classes (same as the team's L3 routines). */
    public static final String LOGGER_NAME = "L3API";

    /** Tag at the start of every log line, so all Customer Limit lines can be found with one grep. */
    public static final String LOG_APP_TAG = "ALBE.CUSTOMER.LIMIT";

    /** Separator between the parts of a log line. */
    public static final String LOG_SEPARATOR = " | ";

    /** true = also log every DB READ / WRITE / DELETE before the call (DEV/SIT); false in production. */
    public static final boolean LOG_DETAIL_ENABLED = true;

    // -------------------------------------------------------------------------
    // Tables / files
    // -------------------------------------------------------------------------
    public static final String CIF_LIMIT_TABLE = "EB.ALBE.CIF.LIMIT";
    public static final String GEN_PARAM_TABLE = "EB.ALBE.GEN.PARAM";
    public static final String UTILISED_LIMIT_TABLE = "EB.ALBE.UTILISED.LIMIT";
    public static final String TXN_DETAILS_TABLE = "EB.ALBE.TXN.DETAILS.LIMIT";
    public static final String ACCOUNT_TABLE = "ACCOUNT";
    public static final String CUSTOMER_ACCOUNT_FILE = "CUSTOMER.ACCOUNT";
    public static final String COMPANY_TABLE = "COMPANY";
    public static final String AAA_TABLE = "AA.ARRANGEMENT.ACTIVITY";
    /** File suffix of unauthorised records. */
    public static final String NAU_SUFFIX = "$NAU";

    /** EB.ALBE.GEN.PARAM record holding the generic limits. */
    public static final String GEN_PARAM_ID = "CUSTOMER.LIMIT";

    // -------------------------------------------------------------------------
    // Limit field names (EB.ALBE.CIF.LIMIT fields = EB.ALBE.GEN.PARAM CUSTOMER.LIMIT KEY.NAME values)
    // -------------------------------------------------------------------------
    public static final String KEY_CCY = "CCY";
    public static final String KEY_DAILY_CREDIT_LIMIT = "DAILY.CREDIT.LIMIT";
    public static final String KEY_DAILY_DEBIT_LIMIT = "DAILY.DEBIT.LIMIT";
    public static final String KEY_MONTHLY_CREDIT_LIMIT = "MONTHLY.CREDIT.LIMIT";
    public static final String KEY_MONTHLY_DEBIT_LIMIT = "MONTHLY.DEBIT.LIMIT";
    public static final String KEY_MAX_CIF_BALANCE = "MAX.CIF.BALANCE";

    /**
     * Words in a T24 read exception that mean "record not found" (checked upper-case).
     * Any other read exception is treated as a failure, so an existing EB.ALBE.CIF.LIMIT is
     * never overwritten because of a read problem. Confirm the exact T24 text in DEV.
     */
    public static final String[] RECORD_NOT_FOUND_MARKERS = { "NOT FOUND", "NOTFOUND", "NO RECORD", "MISSING" };

    // -------------------------------------------------------------------------
    // Counted date kept in the activity REMARKS (no extra table field)
    // -------------------------------------------------------------------------
    /** REMARKS tag holding the server date the transaction is counted on: ALBE.LIMIT.DATE=YYYYMMDD. */
    public static final String REMARKS_DATE_TAG = "ALBE.LIMIT.DATE=";
    /** Length of the YYYYMMDD after the tag. */
    public static final int REMARKS_DATE_LENGTH = 8;
    /** Separator between existing REMARKS text and the tag. */
    public static final String REMARKS_SEPARATOR = " ";

    // -------------------------------------------------------------------------
    // AA activity statuses and activity name keywords
    // -------------------------------------------------------------------------
    public static final String STATUS_UNAUTH = "UNAUTH";
    public static final String STATUS_AUTH = "AUTH";
    public static final String STATUS_DELETE = "DELETE";
    public static final String STATUS_AUTH_REV = "AUTH-REV";

    // -------------------------------------------------------------------------
    // Which activities are counted (the routines can be attached to any activity)
    // -------------------------------------------------------------------------
    /**
     * Separator in the AA.ACTIVITY.CLASS id <PRODUCT.LINE>-<ACTION>-<PROPERTY.CLASS>,
     * e.g. ACCOUNTS-CREDIT-ARRANGEMENT. The AAA field ACTIVITY.CLASS is filled by the system,
     * so user-defined activities linked to a class are covered too.
     */
    public static final String ACTIVITY_CLASS_SEPARATOR = "-";
    /** Position of the ACTION in the activity class id (0 = product line). */
    public static final int ACTIVITY_CLASS_ACTION_POS = 1;
    /** Class actions that move money INTO the account (counted as credit), any product line. */
    public static final String[] CREDIT_ACTIONS = { "CREDIT" };
    /** Class actions that move money OUT of the account (counted as debit), any product line. */
    public static final String[] DEBIT_ACTIONS = { "DEBIT" };
    /**
     * AAA INITIATION.TYPE values that are counted (agreed: all types). Remove a value here to stop
     * counting it, e.g. SECONDARY (charges) or SCHEDULED*EOD. A blank type is always counted.
     */
    public static final String[] COUNTED_INITIATION_TYPES = { "USER", "TRANSACTION", "SECONDARY", "SCHEDULED*SOD",
            "SCHEDULED*EOD", "PAY*SOD", "PAY*EOD", "NOLOG" };

    // -------------------------------------------------------------------------
    // Counted transaction key - the same for every place a routine is attached
    // -------------------------------------------------------------------------
    /**
     * A transaction reference is cut at these characters: "FT26091ABCDE\BNK" (company) and
     * "FT26091ABCDE;1" (history) both become "FT26091ABCDE". Every routine (activity, FT / TT
     * version, accounting entry) counts a transaction under <root reference>, so the same
     * transaction seen from two places replaces itself instead of being counted twice.
     */
    public static final String[] REFERENCE_CUT_CHARS = { "\\", ";" };

    // -------------------------------------------------------------------------
    // ACCOUNT.PARAMETER accounting hook (AccountingEntryLimitController)
    // -------------------------------------------------------------------------
    /** Only statement entries (customer accounts) are checked; CATEG / CONSOL / FWD are skipped. */
    public static final String STMT_ENTRY_TYPE = "STMT";
    /** Entry action types that count the entry (input, input + authorise, forward input, change). */
    public static final String[] STMT_COUNT_ACTIONS = { "VAL", "VALAUT", "ADD", "ADD.AUT", "CHG" };
    /** Entry action types that take the entry back (delete, reversal, reversal with auto-approved overrides). */
    public static final String[] STMT_REVERSE_ACTIONS = { "DEL", "REV", "RSAO" };
    /** STMT.ENTRY SYSTEM.ID values that are not counted (e.g. COB interest / charges). Bank decides; empty = all. */
    public static final String[] STMT_SKIP_SYSTEM_IDS = {};
    /** STMT.ENTRY TRANSACTION.CODE values that are not counted (e.g. charge codes). Bank decides; empty = all. */
    public static final String[] STMT_SKIP_TRANSACTION_CODES = {};

    // -------------------------------------------------------------------------
    // FUNDS.TRANSFER / TELLER version routines
    // -------------------------------------------------------------------------
    public static final String FT_APPLICATION = "FUNDS.TRANSFER";
    public static final String TELLER_APPLICATION = "TELLER";
    /** Transaction id prefixes, to know which application a counted transaction came from. */
    public static final String TXN_PREFIX_AAA = "AAA";
    public static final String TXN_PREFIX_FT = "FT";
    public static final String TXN_PREFIX_TT = "TT";
    /** Between the parts of the EB.ALBE.TXN.DETAILS.LIMIT id <customer>-<YYYYMMDD>-<transaction>. */
    public static final String TXN_ID_SEPARATOR = "-";
    /** TELLER DR.CR.MARKER value meaning side 1 (ACCOUNT.1) is credited. */
    public static final String TT_MARKER_CREDIT = "CREDIT";
    /** TransactionContext current function: input, delete, authorise, reverse. */
    public static final String FUNCTION_INPUT = "I";
    public static final String FUNCTION_DELETE = "D";
    public static final String FUNCTION_AUTHORISE = "A";
    public static final String FUNCTION_REVERSE = "R";
    /** What a FT / TT version routine does with the utilisation (TransactionLimitHandler.actionFor). */
    public static final String UTIL_ACTION_COUNT = "COUNT";
    public static final String UTIL_ACTION_REVERSE_DELETE = "REVERSE.DELETE";
    public static final String UTIL_ACTION_REVERSE = "REVERSE";
    public static final String UTIL_ACTION_NONE = "NONE";
    /** RECORD.STATUS values of a reversal (input of the reversal / reversed). */
    public static final String[] REVERSAL_STATUSES = { "RNAU", "RNAO", "REVE" };

    // -------------------------------------------------------------------------
    // T24 session values
    // -------------------------------------------------------------------------
    /** T24 common variable with the current T24 (business) date, YYYYMMDD. */
    public static final String T24_TODAY_VARIABLE = "!TODAY";

    // -------------------------------------------------------------------------
    // EB.ALBE.TXN.DETAILS.LIMIT CREDIT.DEBIT.FLAG values
    // -------------------------------------------------------------------------
    public static final String FLAG_CREDIT = "CR";
    public static final String FLAG_DEBIT = "DR";

    // -------------------------------------------------------------------------
    // Record id date formats
    // -------------------------------------------------------------------------
    /** Daily utilisation id date and EB.ALBE.TXN.DETAILS.LIMIT id date: YYYYMMDD. */
    public static final DateTimeFormatter DAILY_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** Monthly utilisation id date: YYYYMM. */
    public static final DateTimeFormatter MONTHLY_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
    /** Strict YYYYMMDD for reading dates: an impossible date such as 20261131 is rejected, not adjusted. */
    public static final DateTimeFormatter DATE_PARSE_FORMAT =
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    /** Length of the YYYYMMDD part of a daily utilisation id. */
    public static final int DAILY_PERIOD_LENGTH = 8;
    /** Strict YYYYMM for reading months (enquiry MONTH.FROM / MONTH.TO). */
    public static final DateTimeFormatter MONTH_PARSE_FORMAT =
            DateTimeFormatter.ofPattern("uuuuMM").withResolverStyle(ResolverStyle.STRICT);

    // -------------------------------------------------------------------------
    // Currency conversion
    // -------------------------------------------------------------------------
    /**
     * Exchange rate table and field - TO BE CONFIGURED.
     * While EXCH_RATE_TABLE is blank, only same-currency transactions can be checked;
     * a different currency raises OVR_EXCH_RATE_MISSING.
     */
    public static final String EXCH_RATE_TABLE = "";
    /** Field of EXCH_RATE_TABLE holding the rate - TO BE CONFIGURED. */
    public static final String EXCH_RATE_FIELD = "";
    /** Rate used when both currencies are the same. */
    public static final BigDecimal SAME_CURRENCY_RATE = BigDecimal.ONE;
    /** Decimals kept after converting an amount to the limit currency. */
    public static final int AMOUNT_SCALE = 2;
    /** Decimals of a rate worked out from the activity's LCY amount (logged / kept on the request). */
    public static final int RATE_SCALE = 6;

    // -------------------------------------------------------------------------
    // EB.OVERRIDE ids
    // -------------------------------------------------------------------------
    /** Daily credit/debit exceed for Customer & by & */
    public static final String OVR_DAILY_LIMIT_EXCEED = "EB-ALBE.DAILY.LIMIT.EXCEED";
    /** Monthly credit/debit exceed for Customer & by & */
    public static final String OVR_MONTHLY_LIMIT_EXCEED = "EB-ALBE.MONTHLY.LIMIT.EXCEED";
    /** Overall credit/debit exceed for Customer & by & */
    public static final String OVR_MAX_CIF_BAL_EXCEED = "EB-ALBE.MAX.CIF.BAL.EXCEED";
    /** Customer limit not defined for Customer & (no EB.ALBE.CIF.LIMIT record or a blank limit) */
    public static final String OVR_LIMIT_NOT_DEFINED = "EB-ALBE.CIF.LIMIT.NOT.DEFINED";
    /** Exchange rate not available from & to & (transaction currency, limit currency) */
    public static final String OVR_EXCH_RATE_MISSING = "EB-ALBE.EXCH.RATE.MISSING";
    /**
     * ERROR (blocks the transaction): Limit currency not defined for Customer & - the customer's
     * EB.ALBE.CIF.LIMIT has a blank CCY. EB.ERROR record (and an EB.OVERRIDE record with the same
     * id for the ACCOUNT.PARAMETER hook, which can only return overrides).
     */
    public static final String ERR_LIMIT_CCY_MISSING = "EB-ALBE.CIF.LIMIT.CCY.MISSING";

    /** Prefix of this module's EB.OVERRIDE ids; only these are taken from the AAA OVERRIDE field. */
    public static final String OVERRIDE_ID_PREFIX = "EB-ALBE.";
    /** End of the override id inside an AAA OVERRIDE value ("<id>}<message>..."). Confirm in DEV. */
    public static final String OVERRIDE_ID_END = "}";
    /** Separator of the override ids in the transaction report OVERRIDES column. */
    public static final String OVERRIDE_LIST_SEPARATOR = " ";
    /** Overrides that mean "accepted above a limit" (transaction report OVERRIDDEN = Y). */
    public static final String[] LIMIT_EXCEED_OVERRIDES = { OVR_DAILY_LIMIT_EXCEED, OVR_MONTHLY_LIMIT_EXCEED,
            OVR_MAX_CIF_BAL_EXCEED };

    // -------------------------------------------------------------------------
    // Messages (HLSD text, used for logging; %s = customer id, difference)
    // -------------------------------------------------------------------------
    public static final String MSG_DAILY_EXCEEDED = "Daily credit/debit exceed for Customer %s by %s";
    public static final String MSG_MONTHLY_EXCEEDED = "Monthly credit/debit exceed for Customer %s by %s";
    public static final String MSG_MAX_CIF_EXCEEDED = "Overall credit/debit exceed for Customer %s by %s";
    public static final String MSG_LIMIT_NOT_DEFINED = "Customer limit not defined for Customer %s";
    public static final String MSG_EXCH_RATE_MISSING = "Exchange rate not available from %s to %s";

    // -------------------------------------------------------------------------
    // Enquiries (NOFILE.ALBE.CUSTOMER.LIMIT.SUMMARY / NOFILE.ALBE.CUSTOMER.LIMIT.TXN)
    // -------------------------------------------------------------------------
    /** Separator between the columns of one enquiry row (split by the ENQUIRY record). */
    public static final String ENQ_SEPARATOR = "*";
    /** Selection field names in STANDARD.SELECTION. */
    public static final String ENQ_SEL_CUSTOMER = "CUSTOMER";
    public static final String ENQ_SEL_DATE = "DATE";
    public static final String ENQ_SEL_DATE_FROM = "DATE.FROM";
    public static final String ENQ_SEL_DATE_TO = "DATE.TO";
    public static final String ENQ_SEL_MONTH_FROM = "MONTH.FROM";
    public static final String ENQ_SEL_MONTH_TO = "MONTH.TO";
    /** OVER.LIMIT = Y: only rows where something used is above its limit. */
    public static final String ENQ_SEL_OVER_LIMIT = "OVER.LIMIT";
    /** OVERRIDDEN = Y: only transactions accepted with a limit-exceed override. */
    public static final String ENQ_SEL_OVERRIDDEN = "OVERRIDDEN";
    public static final String ENQ_YES = "Y";
    public static final String ENQ_NO = "N";
    /** Period report row types. */
    public static final String PERIOD_DAILY = "DAILY";
    public static final String PERIOD_MONTHLY = "MONTHLY";
    /** Longest ranges an enquiry accepts (protects the online system). */
    public static final int ENQ_MAX_DAYS = 92;
    public static final int ENQ_MAX_MONTHS = 24;
    /** EB.ERROR ids raised by the enquiries. */
    public static final String ERR_NO_RECORDS = "EB-NO.REC.SELECT";
    public static final String ERR_INVALID_DATE = "EB-ALBE.ENQ.INVALID.DATE";
    public static final String ERR_DATE_RANGE = "EB-ALBE.ENQ.DATE.RANGE";
    public static final String ERR_CUSTOMER_OR_DATE = "EB-ALBE.ENQ.CUSTOMER.OR.DATE";
    public static final String ERR_INVALID_MONTH = "EB-ALBE.ENQ.INVALID.MONTH";
    public static final String ERR_PERIOD_MANDATORY = "EB-ALBE.ENQ.PERIOD.MANDATORY";
    public static final String ERR_RANGE_TOO_LONG = "EB-ALBE.ENQ.RANGE.TOO.LONG";

    // -------------------------------------------------------------------------
    // T24 dynamic array marks (override id FM param1 VM param2)
    // -------------------------------------------------------------------------
    public static final char FM = '\u00FE';
    public static final char VM = '\u00FD';
}
