package com.temenos.ALBE.CustomerLimit.view;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.LimitPeriodRow;
import com.temenos.ALBE.CustomerLimit.model.LimitSummaryRow;
import com.temenos.ALBE.CustomerLimit.model.LimitTxnRow;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;

/*-----------------------------------------------------------------------------
 * Description : Formats the enquiry rows as T24 NOFILE enquiry lines: one String per row,
 *               columns separated by Constants.ENQ_SEPARATOR ("*"). The ENQUIRY record
 *               splits each line into its fields by column number (see the column lists).
 *               Amounts: 2 decimals. Available = limit - utilised (negative = over the
 *               limit, e.g. after an accepted override); blank when the limit is not defined.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitEnquiryView {

    /**
     * Summary columns:
     *  1 CUSTOMER  2 CCY  3 REPORT.DATE
     *  4 DAILY.CR.LIMIT    5 DAILY.CR.UTILISED    6 DAILY.CR.AVAILABLE
     *  7 DAILY.DR.LIMIT    8 DAILY.DR.UTILISED    9 DAILY.DR.AVAILABLE
     * 10 MONTHLY.CR.LIMIT 11 MONTHLY.CR.UTILISED 12 MONTHLY.CR.AVAILABLE
     * 13 MONTHLY.DR.LIMIT 14 MONTHLY.DR.UTILISED 15 MONTHLY.DR.AVAILABLE
     * 16 MAX.CIF.BALANCE  17 OVER.LIMIT (Y / N)
     */
    public static final String SUMMARY_COLUMNS = "CUSTOMER*CCY*REPORT.DATE"
            + "*DAILY.CR.LIMIT*DAILY.CR.UTILISED*DAILY.CR.AVAILABLE"
            + "*DAILY.DR.LIMIT*DAILY.DR.UTILISED*DAILY.DR.AVAILABLE"
            + "*MONTHLY.CR.LIMIT*MONTHLY.CR.UTILISED*MONTHLY.CR.AVAILABLE"
            + "*MONTHLY.DR.LIMIT*MONTHLY.DR.UTILISED*MONTHLY.DR.AVAILABLE"
            + "*MAX.CIF.BALANCE*OVER.LIMIT";

    /**
     * Period columns (one row per customer per day or month):
     *  1 PERIOD.TYPE (DAILY / MONTHLY)  2 PERIOD (YYYYMMDD / YYYYMM)  3 CUSTOMER  4 CCY
     *  5 CR.LIMIT  6 CR.UTILISED  7 CR.AVAILABLE  8 DR.LIMIT  9 DR.UTILISED  10 DR.AVAILABLE
     * 11 OVER.LIMIT (Y / N)
     */
    public static final String PERIOD_COLUMNS = "PERIOD.TYPE*PERIOD*CUSTOMER*CCY"
            + "*CR.LIMIT*CR.UTILISED*CR.AVAILABLE*DR.LIMIT*DR.UTILISED*DR.AVAILABLE*OVER.LIMIT";

    /**
     * Transaction columns:
     *  1 CUSTOMER  2 AAA.ID  3 ACCOUNT.NO  4 CR.DR  5 TXN.AMOUNT  6 EFF.DATE  7 SYSTEM.DATE  8 RECORD.ID
     */
    public static final String TXN_COLUMNS =
            "CUSTOMER*AAA.ID*ACCOUNT.NO*CR.DR*TXN.AMOUNT*EFF.DATE*SYSTEM.DATE*RECORD.ID";

    private CustomerLimitEnquiryView() {
        // Static methods only.
    }

    /** One enquiry line per customer, in the SUMMARY_COLUMNS order. */
    public static List<String> toSummaryLines(List<LimitSummaryRow> rows) {
        List<String> lines = new ArrayList<>(rows.size());
        for (LimitSummaryRow row : rows) {
            lines.add(join(
                    row.customerId, row.ccy, row.reportDate.format(Constants.DAILY_ID_FORMAT),
                    amount(row.dailyCreditLimit), amount(row.dailyCreditUtilised),
                    available(row.dailyCreditLimit, row.dailyCreditUtilised),
                    amount(row.dailyDebitLimit), amount(row.dailyDebitUtilised),
                    available(row.dailyDebitLimit, row.dailyDebitUtilised),
                    amount(row.monthlyCreditLimit), amount(row.monthlyCreditUtilised),
                    available(row.monthlyCreditLimit, row.monthlyCreditUtilised),
                    amount(row.monthlyDebitLimit), amount(row.monthlyDebitUtilised),
                    available(row.monthlyDebitLimit, row.monthlyDebitUtilised),
                    amount(row.maxCifBalance), yesNo(row.isOverLimit())));
        }
        return lines;
    }

    /** One enquiry line per customer per period, in the PERIOD_COLUMNS order. */
    public static List<String> toPeriodLines(List<LimitPeriodRow> rows) {
        List<String> lines = new ArrayList<>(rows.size());
        for (LimitPeriodRow row : rows) {
            lines.add(join(row.periodType, row.period, row.customerId, row.ccy,
                    amount(row.creditLimit), amount(row.creditUtilised), available(row.creditLimit, row.creditUtilised),
                    amount(row.debitLimit), amount(row.debitUtilised), available(row.debitLimit, row.debitUtilised),
                    yesNo(row.isOverLimit())));
        }
        return lines;
    }

    /** One enquiry line per transaction, in the TXN_COLUMNS order. */
    public static List<String> toTxnLines(List<LimitTxnRow> rows) {
        List<String> lines = new ArrayList<>(rows.size());
        for (LimitTxnRow row : rows) {
            lines.add(join(row.customerId, row.aaaId, row.accountNo, row.creditDebitFlag,
                    amount(ValidationUtil.toAmount(row.amount)),
                    row.effectiveDate, row.systemDate, row.recordId));
        }
        return lines;
    }

    private static String yesNo(boolean value) {
        return value ? Constants.ENQ_YES : Constants.ENQ_NO;
    }

    /** Amount with 2 decimals; "" when not defined. */
    private static String amount(BigDecimal value) {
        return value == null ? "" : value.setScale(Constants.AMOUNT_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    /** limit - utilised; "" when the limit is not defined. */
    private static String available(BigDecimal limit, BigDecimal utilised) {
        return limit == null ? "" : amount(limit.subtract(utilised == null ? BigDecimal.ZERO : utilised));
    }

    /** Columns joined with "*"; a "*" inside a value is removed so the columns cannot shift. */
    private static String join(String... values) {
        StringBuilder line = new StringBuilder(160);
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                line.append(Constants.ENQ_SEPARATOR);
            }
            line.append(values[i] == null ? "" : values[i].replace(Constants.ENQ_SEPARATOR, ""));
        }
        return line.toString();
    }
}
