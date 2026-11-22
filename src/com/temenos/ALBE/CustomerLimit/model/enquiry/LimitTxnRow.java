package com.temenos.ALBE.CustomerLimit.model.enquiry;

import com.temenos.ALBE.CustomerLimit.constants.Constants;

/*-----------------------------------------------------------------------------
 * Description : One row of the customer limit transaction enquiry: one transaction
 *               that is currently counted in the customer's utilisation
 *               (one EB.ALBE.TXN.DETAILS.LIMIT record, id <customer>-<YYYYMMDD>-<transaction key>).
 *               Rejected and reversed transactions are not listed: their record is
 *               deleted when the utilisation is reversed.
 *               Filled by CustomerLimitEnquiryService, formatted by CustomerLimitEnquiryView.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 * 21-Nov-2026   Development      TXN.REF (FT / TT reference from the activity)
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
    /** Server date the amount was counted on (YYYYMMDD, from the record id). */
    public String countedDate;
    /** Amount counted, in the limit currency. */
    public String amount;
    /** EB-ALBE.* overrides accepted on the transaction (from its AAA OVERRIDE field), space separated. */
    public String overrides = "";
    /** FT / TT reference: AAA TXN.CONTRACT.ID, or the FT / TT id for the FT / TT version routines. */
    public String txnReference = "";

    /** True when the transaction was accepted with a limit-exceed override (daily, monthly, max CIF). */
    public boolean isLimitOverridden() {
        for (String id : overrides.split(Constants.OVERRIDE_LIST_SEPARATOR)) {
            for (String exceed : Constants.LIMIT_EXCEED_OVERRIDES) {
                if (exceed.equals(id.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "LimitTxnRow[id=" + recordId + ", flag=" + creditDebitFlag + ", amount=" + amount
                + ", countedDate=" + countedDate + ", overrides=" + overrides + ", txnRef=" + txnReference + "]";
    }
}
