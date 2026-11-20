package com.temenos.ALBE.CustomerLimit.model;

/*-----------------------------------------------------------------------------
 * Description : One row of the customer limit transaction enquiry: one transaction
 *               that is currently counted in the customer's utilisation
 *               (one EB.ALBE.TXN.DETAILS.LIMIT record, id <customer>-<AAA id>).
 *               Rejected and reversed transactions are not listed: their record is
 *               deleted when the utilisation is reversed.
 *               Filled by CustomerLimitEnquiryService, formatted by CustomerLimitEnquiryView.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class LimitTxnRow {
    public String recordId;
    public String customerId;
    public String aaaId;
    public String accountNo;
    /** CR / DR */
    public String creditDebitFlag;
    public String effectiveDate;
    /** Server date the amount was counted on (YYYYMMDD). */
    public String systemDate;
    /** Amount counted, in the limit currency. */
    public String amount;

    @Override
    public String toString() {
        return "LimitTxnRow[id=" + recordId + ", flag=" + creditDebitFlag + ", amount=" + amount
                + ", systemDate=" + systemDate + "]";
    }
}
