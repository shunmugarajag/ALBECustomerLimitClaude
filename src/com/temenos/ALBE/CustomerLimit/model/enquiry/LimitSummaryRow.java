package com.temenos.ALBE.CustomerLimit.model.enquiry;

import java.math.BigDecimal;
import java.time.LocalDate;

/*-----------------------------------------------------------------------------
 * Description : One row of the customer limit summary enquiry: a customer's limits
 *               and what is used of them on the report date (day) and in its month.
 *               Limit fields are null when the limit is not defined (blank).
 *               Monthly utilised = UTILISED.MIGRATED.* + UTILISED.* of the monthly record.
 *               Filled by CustomerLimitEnquiryService, formatted by CustomerLimitEnquiryView.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class LimitSummaryRow {
    public String customerId;
    public String ccy;
    public LocalDate reportDate;

    public BigDecimal dailyCreditLimit;
    public BigDecimal dailyCreditUtilised = BigDecimal.ZERO;
    public BigDecimal dailyDebitLimit;
    public BigDecimal dailyDebitUtilised = BigDecimal.ZERO;

    public BigDecimal monthlyCreditLimit;
    public BigDecimal monthlyCreditUtilised = BigDecimal.ZERO;
    public BigDecimal monthlyDebitLimit;
    public BigDecimal monthlyDebitUtilised = BigDecimal.ZERO;

    public BigDecimal maxCifBalance;

    /** True when any used amount (daily / monthly, credit / debit) is above its defined limit. */
    public boolean isOverLimit() {
        return isOver(dailyCreditLimit, dailyCreditUtilised) || isOver(dailyDebitLimit, dailyDebitUtilised)
                || isOver(monthlyCreditLimit, monthlyCreditUtilised) || isOver(monthlyDebitLimit, monthlyDebitUtilised);
    }

    private static boolean isOver(BigDecimal limit, BigDecimal utilised) {
        return limit != null && utilised != null && utilised.compareTo(limit) > 0;
    }

    @Override
    public String toString() {
        return "LimitSummaryRow[customer=" + customerId + ", ccy=" + ccy + ", date=" + reportDate
                + ", dailyCr=" + dailyCreditUtilised + "/" + dailyCreditLimit
                + ", dailyDr=" + dailyDebitUtilised + "/" + dailyDebitLimit
                + ", monthlyCr=" + monthlyCreditUtilised + "/" + monthlyCreditLimit
                + ", monthlyDr=" + monthlyDebitUtilised + "/" + monthlyDebitLimit
                + ", maxCif=" + maxCifBalance + "]";
    }
}
