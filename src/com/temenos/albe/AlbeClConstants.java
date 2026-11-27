package com.temenos.albe;

import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;

/*-----------------------------------------------------------------------------
 * Description : All literals of the customer limit routines (AlbeClActivityLimit, AlbeClMaxCif,
 *               AlbeClLimitCreate).
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 26-Nov-2026   Development      Initial Version
 * 26-Nov-2026   Development      Package com.temenos.albe, class named after the EB.API (MVC version removed)
 *-----------------------------------------------------------------------------
 */
public final class AlbeClConstants {

    private AlbeClConstants() {
    }

    // Logging: logger API -> TAFJ/log/api.log; every line starts with LOG_TAG
    public static final String LOGGER_NAME = "API";
    public static final String LOG_TAG = "ALBE.CUSTOMER.LIMIT | ";

    // Tables
    public static final String CIF_LIMIT_TABLE = "EB.ALBE.CIF.LIMIT";
    public static final String GEN_PARAM_TABLE = "EB.ALBE.GEN.PARAM";
    public static final String TXN_DETAILS_TABLE = "EB.ALBE.TXN.DETAILS.LIMIT";
    public static final String ACCOUNT_TABLE = "ACCOUNT";
    public static final String CONTRACT_BALANCES_TABLE = "EB.CONTRACT.BALANCES";
    /** CURRENCY.MARKET used to convert account balances (T24 standard market 1, user 27-Nov, D54). */
    public static final String CURRENCY_MARKET = "1";
    public static final String CUSTOMER_ACCOUNT_FILE = "CUSTOMER.ACCOUNT";
    public static final String COMPANY_TABLE = "COMPANY";

    // EB.ALBE.GEN.PARAM records
    public static final String GEN_PARAM_LIMIT_ID = "CUSTOMER.LIMIT";
    public static final String GEN_PARAM_ACTIVITY_ID = "CUSTOMER.LIMIT.ACTIVITY";
    public static final String KEY_CURRENCY = "CURRENCY";
    public static final String KEY_DAILY_CREDIT = "DAILY.CREDIT.LIMIT";
    public static final String KEY_DAILY_DEBIT = "DAILY.DEBIT.LIMIT";
    public static final String KEY_MONTHLY_CREDIT = "MONTHLY.CREDIT.LIMIT";
    public static final String KEY_MONTHLY_DEBIT = "MONTHLY.DEBIT.LIMIT";
    public static final String KEY_MAX_CIF = "MAXIMUM.OVERALL.CIF.BALANCE";
    public static final String FIELD_MAX_CIF = "MAXIMUM.OVERALL.CIF.AMOUNT";
    public static final String CREDIT = "CREDIT";
    public static final String DEBIT = "DEBIT";

    // Activity status and the action written in the log
    public static final String STATUS_UNAUTH = "UNAUTH";
    public static final String STATUS_DELETE = "DELETE";
    public static final String STATUS_AUTH_REV = "AUTH-REV";
    public static final String STATUS_AUTH = "AUTH";
    public static final String STATUS_REVERSE = "REVERSE";
    public static final String ACTION_INPUT = "INPUT";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_REVERSAL = "REVERSAL";
    public static final String ACTION_AUTHORISE = "AUTHORISE";
    public static final String ACTION_REVERSAL_INPUT = "REVERSAL INPUT";

    /** INITIATION.TYPE values that are counted (a blank type is counted too). */
    public static final List<String> COUNTED_INITIATION_TYPES = List.of("USER", "TRANSACTION", "SECONDARY",
            "SCHEDULED*SOD", "SCHEDULED*EOD", "PAY*SOD", "PAY*EOD", "NOLOG");

    // TXN.DETAILS: id <customer>-<YYYYMMDD>-<FT/AAA>-<arrangement>, flag CR / DR
    public static final String ID_SEPARATOR = "-";
    public static final String FLAG_CREDIT = "CR";
    public static final String FLAG_DEBIT = "DR";
    public static final String T24_TODAY = "!TODAY";
    public static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    public static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
    public static final DateTimeFormatter DATE_PARSE = DateTimeFormatter.ofPattern("uuuuMMdd")
            .withResolverStyle(ResolverStyle.STRICT);
    public static final int AMOUNT_SCALE = 2;

    // Overrides (user can accept) - one & each: "<customer> by <amount over>" / "from USD to EGP"
    public static final String OVR_DAILY_CREDIT = "EB-ALBE.DAILY.CREDIT.LIMIT.EXCEED";
    public static final String OVR_DAILY_DEBIT = "EB-ALBE.DAILY.DEBIT.LIMIT.EXCEED";
    public static final String OVR_MONTHLY_CREDIT = "EB-ALBE.MONTHLY.CREDIT.LIMIT.EXCEED";
    public static final String OVR_MONTHLY_DEBIT = "EB-ALBE.MONTHLY.DEBIT.LIMIT.EXCEED";
    public static final String OVR_MAX_CIF = "EB-ALBE.MAX.CIF.BAL.EXCEED";
    public static final String OVR_RATE_MISSING = "EB-ALBE.EXCH.RATE.MISSING";
    /** Start of every override id of this module: old ones are removed before each new check (D56). */
    public static final String OVR_PREFIX = "EB-ALBE.";

    // Errors (transaction blocked)
    public static final String ERR_NOT_MAPPED = "EB-ALBE.ACTIVITY.NOT.MAPPED";
    public static final String ERR_CCY_MISSING = "EB-ALBE.CIF.LIMIT.CCY.MISSING";
    public static final String ERR_RATE_MISSING = "EB-ALBE.EXCH.RATE.MISSING";
    public static final String ERR_NO_ACCOUNTS = "EB-ALBE.CUSTOMER.ACCOUNT.MISSING";
    public static final String ERR_UPDATE_FAILED = "EB-ALBE.LIMIT.UPDATE.FAILED";

    // Message text sent to T24: [ID,param]
    public static final String BY = " by ";
    public static final String FROM = "from ";
    public static final String TO = " to ";

    /** Words in a T24 read error that mean "no such record". */
    public static final List<String> NOT_FOUND_MARKERS = List.of("RECORD DOES NOT EXIST", "NOT FOUND", "NOTFOUND",
            "NO RECORD", "MISSING");
}
