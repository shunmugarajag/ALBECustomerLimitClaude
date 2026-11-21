package com.temenos.ALBE.CustomerLimit.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.TransactionSide;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.api.TField;
import com.temenos.t24.api.records.fundstransfer.FundsTransferRecord;
import com.temenos.t24.api.records.teller.Account1Class;
import com.temenos.t24.api.records.teller.TellerRecord;

/*-----------------------------------------------------------------------------
 * Description : Reads the debit and the credit side of a FUNDS.TRANSFER or TELLER record:
 *               which account is the source (debited) and which the destination (credited).
 *
 *               FUNDS.TRANSFER  debit  DEBIT.ACCT.NO   DEBIT.AMOUNT (else AMOUNT.DEBITED)  LOC.AMT.DEBITED
 *                               credit CREDIT.ACCT.NO  CREDIT.AMOUNT (else AMOUNT.CREDITED) LOC.AMT.CREDITED
 *               TELLER          side 1 ACCOUNT.1  AMOUNT.FCY.1 (else AMOUNT.LOCAL.1), direction DR.CR.MARKER
 *                               side 2 ACCOUNT.2  AMOUNT.FCY.2 (else AMOUNT.LOCAL.2), opposite direction
 *
 *               No business rules and no DB access here.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 21-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class TransactionSideReader {
    private static final String CLASS_NAME = "TransactionSideReader";

    private TransactionSideReader() {
    }

    /** Debit side (DEBIT.ACCT.NO) and credit side (CREDIT.ACCT.NO) of a funds transfer. */
    public static List<TransactionSide> fromFundsTransfer(FundsTransferRecord ft) {
        List<TransactionSide> sides = new ArrayList<>();
        TField enteredAmount = isEntered(ft.getDebitAmount()) ? ft.getDebitAmount() : ft.getCreditAmount();

        TransactionSide debit = new TransactionSide();
        debit.sideCode = Constants.FLAG_DEBIT;
        debit.credit = false;
        debit.accountNo = text(ft.getDebitAcctNo());
        debit.customerId = text(ft.getDebitCustomer());
        debit.currency = text(ft.getDebitCurrency());
        debit.amount = firstAmount(ft.getDebitAmount(), ft.getAmountDebited());
        debit.amountLcy = ValidationUtil.toAmountWithoutCurrency(text(ft.getLocAmtDebited()));
        debit.valueDate = text(ft.getDebitValueDate());
        debit.amountField = isEntered(ft.getDebitAmount()) ? ft.getDebitAmount() : enteredAmount;
        sides.add(debit);

        TransactionSide credit = new TransactionSide();
        credit.sideCode = Constants.FLAG_CREDIT;
        credit.credit = true;
        credit.accountNo = text(ft.getCreditAcctNo());
        credit.customerId = text(ft.getCreditCustomer());
        credit.currency = text(ft.getCreditCurrency());
        credit.amount = firstAmount(ft.getCreditAmount(), ft.getAmountCredited());
        credit.amountLcy = ValidationUtil.toAmountWithoutCurrency(text(ft.getLocAmtCredited()));
        credit.valueDate = text(ft.getCreditValueDate());
        credit.amountField = isEntered(ft.getCreditAmount()) ? ft.getCreditAmount() : enteredAmount;
        sides.add(credit);

        LogUtil.info(CLASS_NAME, "fromFundsTransfer", "CALC", "debit=" + debit + " credit=" + credit);
        return sides;
    }

    /** Side 1 (every ACCOUNT.1 value) and side 2 (ACCOUNT.2) of a teller transaction. */
    public static List<TransactionSide> fromTeller(TellerRecord tt) {
        List<TransactionSide> sides = new ArrayList<>();
        boolean side1Credit = Constants.TT_MARKER_CREDIT.equalsIgnoreCase(text(tt.getDrCrMarker()));

        for (Account1Class account1 : tt.getAccount1()) {
            TransactionSide side1 = new TransactionSide();
            side1.credit = side1Credit;
            side1.sideCode = side1Credit ? Constants.FLAG_CREDIT : Constants.FLAG_DEBIT;
            side1.accountNo = text(account1.getAccount1());
            side1.customerId = text(tt.getCustomer1());
            side1.currency = text(tt.getCurrency1());
            side1.amount = firstAmount(account1.getAmountFcy1(), account1.getAmountLocal1());
            side1.amountLcy = ValidationUtil.toAmount(text(account1.getAmountLocal1()));
            side1.valueDate = text(tt.getValueDate1());
            side1.amountField = account1.getAmountLocal1();
            sides.add(side1);
        }

        TransactionSide side2 = new TransactionSide();
        side2.credit = !side1Credit;
        side2.sideCode = side2.credit ? Constants.FLAG_CREDIT : Constants.FLAG_DEBIT;
        side2.accountNo = text(tt.getAccount2());
        side2.customerId = text(tt.getCustomer2());
        side2.currency = text(tt.getCurrency2());
        side2.amount = firstAmount(tt.getAmountFcy2(), tt.getAmountLocal2());
        side2.amountLcy = ValidationUtil.toAmount(text(tt.getAmountLocal2()));
        side2.valueDate = text(tt.getValueDate2());
        side2.amountField = tt.getAmountLocal2();
        sides.add(side2);

        LogUtil.info(CLASS_NAME, "fromTeller", "CALC", "sides=" + sides);
        return sides;
    }

    /** First positive amount of the fields (a "currency + amount" value is accepted too); 0 if none. */
    private static BigDecimal firstAmount(TField first, TField second) {
        BigDecimal amount = ValidationUtil.toAmountWithoutCurrency(text(first));
        return ValidationUtil.isPositive(amount) ? amount : ValidationUtil.toAmountWithoutCurrency(text(second));
    }

    private static boolean isEntered(TField field) {
        return ValidationUtil.isPositive(ValidationUtil.toAmountWithoutCurrency(text(field)));
    }

    private static String text(TField field) {
        return field == null ? "" : ValidationUtil.nvl(field.getValue()).trim();
    }
}
