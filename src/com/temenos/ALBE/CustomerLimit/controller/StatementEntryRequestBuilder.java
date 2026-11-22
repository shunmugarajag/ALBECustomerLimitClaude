package com.temenos.ALBE.CustomerLimit.controller;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.service.CustomerLimitEngine;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.api.TField;
import com.temenos.t24.api.records.stmtentry.StmtEntryRecord;

/*-----------------------------------------------------------------------------
 * Description : Turns one accounting entry (STMT.ENTRY) of ANY transaction into transaction
 *               info for CustomerLimitEngine - used by AccountingEntryLimitController.
 *
 *                 customer   CUSTOMER.ID (blank = internal / GL account => skipped)
 *                 direction  sign of the amount on the customer's account: + money in = credit,
 *                            - money out = debit (flipped for a reversal entry)
 *                 amount     AMOUNT.FCY (account in foreign currency), else AMOUNT.LCY
 *                 LCY amount AMOUNT.LCY (used when the limit is in local currency)
 *                 currency   CURRENCY
 *                 key        TRANS.REFERENCE root + .CR / .DR (same key as the activity and FT / TT
 *                            routines, so no double count)
 *                 date       BOOKING.DATE, else the T24 date
 *
 *               Skipped: entry type other than STMT, no customer, zero amount, SYSTEM.ID in
 *               Constants.STMT_SKIP_SYSTEM_IDS, TRANSACTION.CODE in STMT_SKIP_TRANSACTION_CODES.
 *               No business rules and no DB access here.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 22-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class StatementEntryRequestBuilder {
    private static final String CLASS_NAME = "StatementEntryRequestBuilder";

    private StatementEntryRequestBuilder() {
    }

    /**
     * Transaction info of the entry; null when the entry is not counted (reason logged).
     * @param flipSign true for an entry that reverses an earlier one (its sign is the opposite)
     */
    public static CustomerLimitDTO build(String entryType, StmtEntryRecord entry, boolean flipSign,
            CustomerLimitRepository repository) {

        String reference = text(entry.getTransReference());
        if (!Constants.STMT_ENTRY_TYPE.equalsIgnoreCase(ValidationUtil.nvl(entryType).trim())) {
            LogUtil.detail(CLASS_NAME, "build", "SKIP", "entryType=" + entryType + " ref=" + reference);
            return null;
        }
        String customerId = text(entry.getCustomerId());
        if (ValidationUtil.isBlank(customerId)) {
            LogUtil.detail(CLASS_NAME, "build", "SKIP", "no customer (internal account) account="
                    + text(entry.getAccountNumber()) + " ref=" + reference);
            return null;
        }
        String systemId = text(entry.getSystemId());
        String transactionCode = text(entry.getTransactionCode());
        if (ValidationUtil.contains(Constants.STMT_SKIP_SYSTEM_IDS, systemId)
                || ValidationUtil.contains(Constants.STMT_SKIP_TRANSACTION_CODES, transactionCode)) {
            LogUtil.info(CLASS_NAME, "build", "SKIP", "systemId=" + systemId + " txnCode=" + transactionCode
                    + " not counted ref=" + reference);
            return null;
        }

        BigDecimal fcy = ValidationUtil.toAmount(text(entry.getAmountFcy()));
        BigDecimal lcy = ValidationUtil.toAmount(text(entry.getAmountLcy()));
        BigDecimal signed = fcy.signum() != 0 ? fcy : lcy;
        if (signed.signum() == 0 || ValidationUtil.isBlank(reference)) {
            LogUtil.info(CLASS_NAME, "build", "SKIP", "zero amount or no reference ref=" + reference);
            return null;
        }
        boolean credit = (signed.signum() > 0) != flipSign;

        CustomerLimitDTO request = new CustomerLimitDTO();
        request.setCustomerId(customerId);
        request.setTransactionId(CustomerLimitEngine.transactionKey(reference, credit));
        request.setTransactionDate(entryDate(entry, repository));
        request.setTransactionCurrency(text(entry.getCurrency()));
        request.setAmountLcy(lcy.abs());
        if (credit) {
            request.setCreditAmount(signed.abs());
        } else {
            request.setDebitAmount(signed.abs());
        }
        LogUtil.info(CLASS_NAME, "build", "CALC", "systemId=" + systemId + " txnCode=" + transactionCode
                + " account=" + text(entry.getAccountNumber()) + " flip=" + flipSign + " request=" + request);
        return request;
    }

    /** BOOKING.DATE of the entry, else the T24 date, else the server clock (logged). */
    private static LocalDate entryDate(StmtEntryRecord entry, CustomerLimitRepository repository) {
        LocalDate booking = ValidationUtil.toDate(text(entry.getBookingDate()));
        if (booking != null) {
            return booking;
        }
        LocalDate today = repository.getT24Today();
        if (today != null) {
            return today;
        }
        LocalDate clock = LocalDate.now();
        LogUtil.error(CLASS_NAME, "entryDate", "FALLBACK", "no BOOKING.DATE and no T24 date, server clock " + clock);
        return clock;
    }

    private static String text(TField field) {
        return field == null ? "" : ValidationUtil.nvl(field.getValue()).trim();
    }
}
