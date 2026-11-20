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
    // AA activity statuses and activity name keywords
    // -------------------------------------------------------------------------
    public static final String STATUS_UNAUTH = "UNAUTH";
    public static final String STATUS_AUTH = "AUTH";
    public static final String STATUS_DELETE = "DELETE";
    public static final String STATUS_AUTH_REV = "AUTH-REV";

    /** Activity name contains CREDIT => credit activity (e.g. ACCOUNTS-CREDIT-ARRANGEMENT). */
    public static final String ACTIVITY_CREDIT = "CREDIT";
    /** Activity name contains DEBIT => debit activity (e.g. ACCOUNTS-DEBIT-ARRANGEMENT). */
    public static final String ACTIVITY_DEBIT = "DEBIT";

    // -------------------------------------------------------------------------
    // EB.ALBE.TXN.DETAILS.LIMIT CREDIT.DEBIT.FLAG values
    // -------------------------------------------------------------------------
    public static final String FLAG_CREDIT = "CR";
    public static final String FLAG_DEBIT = "DR";

    // -------------------------------------------------------------------------
    // Record id date formats
    // -------------------------------------------------------------------------
    /** Daily utilisation id date and EB.ALBE.TXN.DETAILS.LIMIT SYSTEM.DATE: YYYYMMDD. */
    public static final DateTimeFormatter DAILY_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** Monthly utilisation id date: YYYYMM. */
    public static final DateTimeFormatter MONTHLY_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
    /** Strict YYYYMMDD for reading dates: an impossible date such as 20261131 is rejected, not adjusted. */
    public static final DateTimeFormatter DATE_PARSE_FORMAT =
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);

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
    /** EB.ERROR ids raised by the enquiries. */
    public static final String ERR_NO_RECORDS = "EB-NO.REC.SELECT";
    public static final String ERR_INVALID_DATE = "EB-ALBE.ENQ.INVALID.DATE";
    public static final String ERR_DATE_RANGE = "EB-ALBE.ENQ.DATE.RANGE";
    public static final String ERR_CUSTOMER_MANDATORY = "EB-ALBE.ENQ.CUSTOMER.MANDATORY";

    // -------------------------------------------------------------------------
    // T24 dynamic array marks (override id FM param1 VM param2)
    // -------------------------------------------------------------------------
    public static final char FM = '\u00FE';
    public static final char VM = '\u00FD';
}
