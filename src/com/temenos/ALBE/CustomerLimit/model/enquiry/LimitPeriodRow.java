package com.temenos.ALBE.CustomerLimit.model.enquiry;

import java.math.BigDecimal;

/*-----------------------------------------------------------------------------
 * Description : One row of the customer limit period enquiry: one customer for one day
 *               (DAILY, period YYYYMMDD) or one month (MONTHLY, period YYYYMM), with the
 *               credit / debit limit and what was used in that period.
 *               Monthly utilised = UTILISED.MIGRATED.* + UTILISED.* of the monthly record.
 *               A null limit means "not defined" (blank in EB.ALBE.CIF.LIMITS, or no record).
 *               Filled by CustomerLimitEnquiryService, formatted by CustomerLimitEnquiryView.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class LimitPeriodRow {
    /** DAILY or MONTHLY (Constants.PERIOD_*). */
    public String periodType;
    /** YYYYMMDD for DAILY, YYYYMM for MONTHLY. */
    public String period;
    public String customerId;
    public String ccy;

    public BigDecimal creditLimit;
    public BigDecimal creditUtilised = BigDecimal.ZERO;
    public BigDecimal debitLimit;
    public BigDecimal debitUtilised = BigDecimal.ZERO;

    /** True when credit or debit used is above its (defined) limit. */
    public boolean isOverLimit() {
        return isOver(creditLimit, creditUtilised) || isOver(debitLimit, debitUtilised);
    }

    private static boolean isOver(BigDecimal limit, BigDecimal utilised) {
        return limit != null && utilised != null && utilised.compareTo(limit) > 0;
    }

    @Override
    public String toString() {
        return "LimitPeriodRow[" + periodType + " " + period + " customer=" + customerId
                + " cr=" + creditUtilised + "/" + creditLimit + " dr=" + debitUtilised + "/" + debitLimit + "]";
    }
}
