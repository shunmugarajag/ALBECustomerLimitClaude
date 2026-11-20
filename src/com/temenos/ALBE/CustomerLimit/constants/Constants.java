package com.temenos.ALBE.CustomerLimit.constants;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

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
    public static final String GEN_PARAM_ID = "SYSTEM";

    // -------------------------------------------------------------------------
    // Limit field names (EB.ALBE.CIF.LIMIT fields = EB.ALBE.GEN.PARAM SYSTEM KEY.NAME values)
    // -------------------------------------------------------------------------
    public static final String KEY_CCY = "CCY";
    public static final String KEY_DAILY_CREDIT_LIMIT = "DAILY.CREDIT.LIMIT";
    public static final String KEY_DAILY_DEBIT_LIMIT = "DAILY.DEBIT.LIMIT";
    public static final String KEY_MONTHLY_CREDIT_LIMIT = "MONTHLY.CREDIT.LIMIT";
    public static final String KEY_MONTHLY_DEBIT_LIMIT = "MONTHLY.DEBIT.LIMIT";
    public static final String KEY_MAX_CIF_BALANCE = "MAX.CIF.BALANCE";

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
    // Version function codes (customer delete)
    // -------------------------------------------------------------------------
    public static final String FUNCTION_DELETE = "D";
    public static final String FUNCTION_DELETE_FULL = "DELETE";

    // -------------------------------------------------------------------------
    // EB.ALBE.TXN.DETAILS.LIMIT CREDIT.DEBIT.FLAG values
    // -------------------------------------------------------------------------
    public static final String FLAG_CREDIT = "CR";
    public static final String FLAG_DEBIT = "DR";

    // -------------------------------------------------------------------------
    // Record id date formats
    // -------------------------------------------------------------------------
    /** Daily utilisation / transaction details id date: YYYYMMDD. */
    public static final DateTimeFormatter DAILY_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** Monthly utilisation id date: YYYYMM. */
    public static final DateTimeFormatter MONTHLY_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    // -------------------------------------------------------------------------
    // Currency
    // -------------------------------------------------------------------------
    /** Exchange rate is fixed at 1 (LCY only) until currency conversion is confirmed. */
    public static final BigDecimal EXCHANGE_RATE = BigDecimal.ONE;

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

    // -------------------------------------------------------------------------
    // Messages (HLSD text, used for logging; %s = customer id, difference)
    // -------------------------------------------------------------------------
    public static final String MSG_DAILY_EXCEEDED = "Daily credit/debit exceed for Customer %s by %s";
    public static final String MSG_MONTHLY_EXCEEDED = "Monthly credit/debit exceed for Customer %s by %s";
    public static final String MSG_MAX_CIF_EXCEEDED = "Overall credit/debit exceed for Customer %s by %s";
    public static final String MSG_LIMIT_NOT_DEFINED = "Customer limit not defined for Customer %s";

    // -------------------------------------------------------------------------
    // T24 dynamic array marks (override id FM param1 VM param2)
    // -------------------------------------------------------------------------
    public static final char FM = '\u00FE';
    public static final char VM = '\u00FD';
}
