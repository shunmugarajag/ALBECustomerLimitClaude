package com.temenos.ALBE.CustomerLimit.model;

import java.math.BigDecimal;

/*-----------------------------------------------------------------------------
 * Description : Limit values of one customer (from EB.ALBE.CIF.LIMIT) or the generic
 *               limits (from EB.ALBE.GEN.PARAM SYSTEM). A null amount means the field
 *               is blank (limit not defined).
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class Limits {
    /** CCY: currency of the limits; transaction amounts and balances are converted to it. */
    public String ccy;
    /** DAILY.CREDIT.LIMIT */
    public BigDecimal dailyCreditLimit;
    /** DAILY.DEBIT.LIMIT */
    public BigDecimal dailyDebitLimit;
    /** MONTHLY.CREDIT.LIMIT */
    public BigDecimal monthlyCreditLimit;
    /** MONTHLY.DEBIT.LIMIT */
    public BigDecimal monthlyDebitLimit;
    /** MAX.CIF.BALANCE */
    public BigDecimal maxCifBalance;
    /** Where the limits were read from, for logging (e.g. EB.ALBE.CIF.LIMIT>100234). */
    public String source;

    @Override
    public String toString() {
        return "Limits[source=" + source + ", ccy=" + ccy + ", dailyCredit=" + dailyCreditLimit
                + ", dailyDebit=" + dailyDebitLimit + ", monthlyCredit=" + monthlyCreditLimit
                + ", monthlyDebit=" + monthlyDebitLimit + ", maxCifBalance=" + maxCifBalance + "]";
    }
}
