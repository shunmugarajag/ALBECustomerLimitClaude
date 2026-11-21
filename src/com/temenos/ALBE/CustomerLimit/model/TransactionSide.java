package com.temenos.ALBE.CustomerLimit.model;

import java.math.BigDecimal;

import com.temenos.api.TField;

/*-----------------------------------------------------------------------------
 * Description : One side (debit or credit) of a FUNDS.TRANSFER or TELLER transaction, as read
 *               by TransactionSideReader: the account, its customer (when the transaction
 *               gives it), the direction, the amount and the field the overrides go on.
 *               A transaction has two sides; each customer account side is checked and
 *               counted for its own customer, internal accounts are skipped.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 21-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class TransactionSide {
    /** CR or DR (Constants.FLAG_CREDIT / FLAG_DEBIT). */
    public String sideCode;
    /** True when money goes INTO the account of this side. */
    public boolean credit;
    public String accountNo = "";
    /** Customer given by the transaction (FT DEBIT/CREDIT.CUSTOMER, TT CUSTOMER.1/2); blank = read from ACCOUNT. */
    public String customerId = "";
    /** Amount in the account currency of this side. */
    public BigDecimal amount = BigDecimal.ZERO;
    /** Same amount in local currency (FT LOC.AMT.DEBITED/CREDITED, TT AMOUNT.LOCAL.1/2); 0 if not given. */
    public BigDecimal amountLcy = BigDecimal.ZERO;
    public String currency = "";
    public String valueDate = "";
    /** Field the overrides of this side are raised on. */
    public TField amountField;

    @Override
    public String toString() {
        return "TransactionSide[" + sideCode + " account=" + accountNo + " customer=" + customerId + " amount="
                + amount + " " + currency + " lcy=" + amountLcy + " valueDate=" + valueDate + "]";
    }
}
