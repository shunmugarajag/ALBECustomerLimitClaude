package com.temenos.ALBE.CustomerLimit.constants;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;

/*-----------------------------------------------------------------------------
 * Description : All constants of the customer limit routines in one place.
 *               Not to be instantiated or extended.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 * 26-Nov-2026   Development      Report (enquiry) constants removed (reports dropped)
 * 26-Nov-2026   Development      ERR_CUSTOMER_ACCOUNT_MISSING (max CIF, no CUSTOMER.ACCOUNT)
 * 26-Nov-2026   Development      D46: debit / credit override ids, one parameter per message
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
    public static final String LOGGER_NAME = "API";

    /** Tag at the start of every log line, so all Customer Limit lines can be found with one grep. */
    public static final String LOG_APP_TAG = "ALBE.CUSTOMER.LIMIT";

    /** Separator between the parts of a log line. */
    public static final String LOG_SEPARATOR = " | ";

    /** true = also log every DB READ / WRITE / DELETE before the call (DEV/SIT); false in production. */
    public static final boolean LOG_DETAIL_ENABLED = false;
    /** Event of the plain-language summary lines (LogUtil.result). */
    public static final String LOG_EVENT_RESULT = "RESULT";
    /** Event of the activity field dump at the start of each hook (ActivityRequestBuilder.logActivity). */
    public static final String LOG_EVENT_ACTIVITY = "ACTIVITY";

    // -------------------------------------------------------------------------
    // Tables / files
    // -------------------------------------------------------------------------
    public static final String CIF_LIMIT_TABLE = "EB.ALBE.CIF.LIMITS";
    public static final String GEN_PARAM_TABLE = "EB.ALBE.GEN.PARAM";
    public static final String UTILISED_LIMIT_TABLE = "EB.ALBE.UTILISED.LIMIT";
    public static final String TXN_DETAILS_TABLE = "EB.ALBE.TXN.DETAILS.LIMIT";
    public static final String ACCOUNT_TABLE = "ACCOUNT";
    public static final String CUSTOMER_ACCOUNT_FILE = "CUSTOMER.ACCOUNT";
    public static final String COMPANY_TABLE = "COMPANY";

    /** EB.ALBE.GEN.PARAM record holding the generic limits. */
    public static final String GEN_PARAM_ID = "CUSTOMER.LIMIT";
    /**
     * EB.ALBE.GEN.PARAM record that maps an AA activity to credit or debit (user 25-Nov-2026, D41):
     * KEY.NAME = AAA ACTIVITY (e.g. ACCOUNTS-CREDIT-ARRANGEMENT), KEY.VALUE = CREDIT or DEBIT.
     * An activity the routine is attached to but not listed here raises ERR_ACTIVITY_NOT_MAPPED.
     */
    public static final String GEN_PARAM_ACTIVITY_ID = "CUSTOMER.LIMIT.ACTIVITY";
    /** KEY.VALUE in GEN_PARAM_ACTIVITY_ID for an activity that moves money INTO the account. */
    public static final String DIRECTION_CREDIT = "CREDIT";
    /** KEY.VALUE in GEN_PARAM_ACTIVITY_ID for an activity that moves money OUT of the account. */
    public static final String DIRECTION_DEBIT = "DEBIT";

    // -------------------------------------------------------------------------
    // Limit names (used in logs / overrides). Table EB.ALBE.CIF.LIMITS field names differ for two of them:
    // CURRENCY (= CCY) and MAXIMUM.OVERALL.CIF.AMOUNT (= MAX.CIF.BALANCE).
    // -------------------------------------------------------------------------
    public static final String KEY_CCY = "CCY";
    public static final String KEY_DAILY_CREDIT_LIMIT = "DAILY.CREDIT.LIMIT";
    public static final String KEY_DAILY_DEBIT_LIMIT = "DAILY.DEBIT.LIMIT";
    public static final String KEY_MONTHLY_CREDIT_LIMIT = "MONTHLY.CREDIT.LIMIT";
    public static final String KEY_MONTHLY_DEBIT_LIMIT = "MONTHLY.DEBIT.LIMIT";
    public static final String KEY_MAX_CIF_BALANCE = "MAX.CIF.BALANCE";
    /** EB.ALBE.CIF.LIMITS field holding the maximum CIF balance (shown when it is blank). */
    public static final String CIF_FIELD_MAX_CIF_AMOUNT = "MAXIMUM.OVERALL.CIF.AMOUNT";

    // -------------------------------------------------------------------------
    // EB.ALBE.GEN.PARAM CUSTOMER.LIMIT KEY.NAME values (as set up in DEV, 23-Nov-2026).
    // Two differ from the EB.ALBE.CIF.LIMITS field names: CURRENCY (-> CCY) and
    // MAXIMUM.OVERALL.CIF.BALANCE (-> MAX.CIF.BALANCE).
    // -------------------------------------------------------------------------
    public static final String PARAM_KEY_CCY = "CURRENCY";
    public static final String PARAM_KEY_DAILY_CREDIT_LIMIT = KEY_DAILY_CREDIT_LIMIT;
    public static final String PARAM_KEY_DAILY_DEBIT_LIMIT = KEY_DAILY_DEBIT_LIMIT;
    public static final String PARAM_KEY_MONTHLY_CREDIT_LIMIT = KEY_MONTHLY_CREDIT_LIMIT;
    public static final String PARAM_KEY_MONTHLY_DEBIT_LIMIT = KEY_MONTHLY_DEBIT_LIMIT;
    public static final String PARAM_KEY_MAX_CIF_BALANCE = "MAXIMUM.OVERALL.CIF.BALANCE";

    /**
     * Words in a T24 read exception that mean "record not found" (checked upper-case).
     * Any other read exception is treated as a failure, so an existing EB.ALBE.CIF.LIMITS is
     * never overwritten because of a read problem. R25 DEV (24-Nov-2026, TQ13): DataAccess.getRecord
     * throws T24CoreException EB-INTERNAL.ERROR "... Record does not exist" for a missing record.
     */
    public static final List<String> RECORD_NOT_FOUND_MARKERS = List.of("RECORD DOES NOT EXIST", "NOT FOUND",
            "NOTFOUND", "NO RECORD", "MISSING");

    // -------------------------------------------------------------------------
    // AA activity statuses and activity name keywords
    // -------------------------------------------------------------------------
    public static final String STATUS_UNAUTH = "UNAUTH";
    public static final String STATUS_AUTH = "AUTH";
    public static final String STATUS_DELETE = "DELETE";
    public static final String STATUS_AUTH_REV = "AUTH-REV";
    public static final String STATUS_REVERSE = "REVERSE";

    // -------------------------------------------------------------------------
    // Which activities are counted (the routines can be attached to any activity)
    // -------------------------------------------------------------------------
    /**
     * AAA INITIATION.TYPE values that are counted (agreed: all types). Remove a value here to stop
     * counting it, e.g. SECONDARY (charges) or SCHEDULED*EOD. A blank type is always counted.
     */
    public static final List<String> COUNTED_INITIATION_TYPES = List.of("USER", "TRANSACTION", "SECONDARY", "SCHEDULED*SOD",
            "SCHEDULED*EOD", "PAY*SOD", "PAY*EOD", "NOLOG");

    // -------------------------------------------------------------------------
    // Counted transaction key - the same for every place a routine is attached
    // -------------------------------------------------------------------------
    /**
     * A transaction reference is cut at these characters: "FT26091ABCDE\BNK" (company) and
     * "FT26091ABCDE;1" (history) both become "FT26091ABCDE". An activity is counted under the
     * root of its TXN.CONTRACT.ID (the FT / TT behind it), else its own id.
     */
    public static final List<String> REFERENCE_CUT_CHARS = List.of("\\", ";");

    // -------------------------------------------------------------------------
    // Transactions behind an activity
    // -------------------------------------------------------------------------
    /** Transaction id prefixes, to know which application a counted transaction came from. */
    public static final String TXN_PREFIX_AAA = "AAA";
    public static final String TXN_PREFIX_FT = "FT";
    public static final String TXN_PREFIX_TT = "TT";
    /** Between the parts of the EB.ALBE.TXN.DETAILS.LIMIT id <customer>-<YYYYMMDD>-<transaction>. */
    public static final String TXN_ID_SEPARATOR = "-";

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

    // -------------------------------------------------------------------------
    // Currency conversion
    // -------------------------------------------------------------------------
    /**
     * Exchange rate table and field - TO BE CONFIGURED.
     * Used only for the maximum CIF sum (other accounts' balances, parked - OVR_EXCH_RATE_MISSING).
     * The transaction amount is converted from the activity (D44): LCY amount, else TXN.EXCH.RATE,
     * else ERR_EXCH_RATE_MISSING.
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
    // EB.OVERRIDE ids spelled EB-ABLE.* exactly as created in DEV (23/24-Nov-2026); the EB.ERROR ids stay EB-ALBE.*.
    // D46: ONE parameter per message (DEV 26-Nov: a second parameter is not filled - both values landed in
    // the first &), and separate ids for debit and credit so the message says which limit.
    /** Daily debit limit exceeded for Customer &   (& = "<customer> by <amount over>") */
    public static final String OVR_DAILY_DEBIT_LIMIT_EXCEED = "EB-ABLE.DAILY.DEBIT.LIMIT.EXCEED";
    /** Daily credit limit exceeded for Customer &  (& = "<customer> by <amount over>") */
    public static final String OVR_DAILY_CREDIT_LIMIT_EXCEED = "EB-ABLE.DAILY.CREDIT.LIMIT.EXCEED";
    /** Monthly debit limit exceeded for Customer & (& = "<customer> by <amount over>") */
    public static final String OVR_MONTHLY_DEBIT_LIMIT_EXCEED = "EB-ABLE.MONTHLY.DEBIT.LIMIT.EXCEED";
    /** Monthly credit limit exceeded for Customer & (& = "<customer> by <amount over>") */
    public static final String OVR_MONTHLY_CREDIT_LIMIT_EXCEED = "EB-ABLE.MONTHLY.CREDIT.LIMIT.EXCEED";
    /** Overall balance limit exceeded for Customer & (& = "<customer> by <amount over>") */
    public static final String OVR_MAX_CIF_BAL_EXCEED = "EB-ABLE.MAX.CIF.BAL.EXCEED";
    /** Customer limit not defined for Customer & (no EB.ALBE.CIF.LIMITS record or a blank limit) */
    public static final String OVR_LIMIT_NOT_DEFINED = "EB-ABLE.CIF.LIMIT.NOT.DEFINED";
    /** Exchange rate not available & (& = "from <currency> to <limit currency>") - max CIF only */
    public static final String OVR_EXCH_RATE_MISSING = "EB-ABLE.EXCH.RATE.MISSING";
    /** Words joining the values into the one parameter. */
    public static final String PARAM_BY = " by ";
    public static final String PARAM_FROM = "from ";
    public static final String PARAM_TO = " to ";
    /**
     * ERROR (blocks the transaction): Limit currency not defined for Customer & - the customer's
     * EB.ALBE.CIF.LIMITS has a blank CCY. EB.ERROR record.
     */
    public static final String ERR_LIMIT_CCY_MISSING = "EB-ALBE.CIF.LIMIT.CCY.MISSING";
    /**
     * ERROR (blocks the transaction): Activity & not defined in EB.ALBE.GEN.PARAM CUSTOMER.LIMIT.ACTIVITY.
     * EB.ERROR record. Raised by the check routines when the activity they are attached to has no
     * CREDIT / DEBIT mapping (D41).
     */
    public static final String ERR_ACTIVITY_NOT_MAPPED = "EB-ALBE.ACTIVITY.NOT.MAPPED";
    /**
     * Customer limit could not be updated - transaction cancelled. EB.ERROR record. Raised from
     * postCoreTableUpdate when saving EB.ALBE.UTILISED.LIMIT / EB.ALBE.TXN.DETAILS.LIMIT fails
     * (decision D37: block, so utilisation never silently misses a transaction).
     */
    public static final String ERR_LIMIT_UPDATE_FAILED = "EB-ALBE.LIMIT.UPDATE.FAILED";
    /**
     * ERROR (blocks the transaction, D44): Exchange rate not available from & to &, transaction not
     * allowed - transaction currency differs from the limit currency and the activity has neither an
     * LCY amount nor TXN.EXCH.RATE, or the limit is not in the local currency. EB.ERROR record.
     */
    public static final String ERR_EXCH_RATE_MISSING = "EB-ALBE.EXCH.RATE.MISSING";
    /**
     * ERROR (blocks the transaction): No accounts found in CUSTOMER.ACCOUNT for Customer &, transaction
     * not allowed - raised by the max CIF check (credit, MAXIMUM.OVERALL.CIF.AMOUNT filled). EB.ERROR record.
     */
    public static final String ERR_CUSTOMER_ACCOUNT_MISSING = "EB-ALBE.CUSTOMER.ACCOUNT.MISSING";

    // -------------------------------------------------------------------------
    // Activity status -> action shown in the ACTION log line (single routine)
    // -------------------------------------------------------------------------
    public static final String ACTION_INPUT = "INPUT";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_REVERSAL = "REVERSAL";
    public static final String ACTION_AUTHORISE = "AUTHORISE";
    public static final String ACTION_REVERSAL_INPUT = "REVERSAL INPUT";
    public static final String LOG_EVENT_ACTION = "ACTION";


    // -------------------------------------------------------------------------
    // Messages (HLSD text, used for logging; %s = customer id, difference)
    // -------------------------------------------------------------------------
    public static final String MSG_DAILY_DEBIT_EXCEEDED = "Daily debit limit exceeded for Customer %s by %s";
    public static final String MSG_DAILY_CREDIT_EXCEEDED = "Daily credit limit exceeded for Customer %s by %s";
    public static final String MSG_MONTHLY_DEBIT_EXCEEDED = "Monthly debit limit exceeded for Customer %s by %s";
    public static final String MSG_MONTHLY_CREDIT_EXCEEDED = "Monthly credit limit exceeded for Customer %s by %s";
    public static final String MSG_MAX_CIF_EXCEEDED = "Overall balance limit exceeded for Customer %s by %s";
    public static final String MSG_LIMIT_NOT_DEFINED = "Customer limit not defined for Customer %s";
    public static final String MSG_EXCH_RATE_MISSING = "Exchange rate not available from %s to %s";

    // -------------------------------------------------------------------------
    // Override / error text sent to T24: [ID,param1,param2]
    // (R25 EB.HANDLE.OVERRIDES / EB.HANDLE.ERRORS: text starting with "[" -> part 1 = id,
    //  the other comma parts fill the & of the message; anything else is used as it is)
    // -------------------------------------------------------------------------
    public static final String MESSAGE_TEXT_START = "[";
    public static final String MESSAGE_TEXT_END = "]";
    public static final String MESSAGE_TEXT_SEPARATOR = ",";
}
