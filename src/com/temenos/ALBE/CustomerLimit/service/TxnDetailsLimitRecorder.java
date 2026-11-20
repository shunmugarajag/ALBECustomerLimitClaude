package com.temenos.ALBE.CustomerLimit.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.t24.api.records.ebalbetxndetailslimit.EbAlbeTxnDetailsLimitRecord;

/*-----------------------------------------------------------------------------
 * Description : Keeps EB.ALBE.TXN.DETAILS.LIMIT for every transaction counted in
 *               EB.ALBE.UTILISED.LIMIT (written at input), so a re-input, a rejected
 *               input or a reversal subtracts from the original day and month.
 *
 *               Record id : <customer id>-<AAA id>   (known at every step => direct read)
 *               Fields    : ACCOUNT.NO, CREDIT.DEBIT.FLAG (CR/DR), EFF.DATE (value date),
 *                           TXN.AMOUNT (in the limit currency),
 *                           SYSTEM.DATE (server date the amount was counted on)
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class TxnDetailsLimitRecorder {
    private static final String CLASS_NAME = "TxnDetailsLimitRecorder";

    private final CustomerLimitRepository repository;

    /** @param repository DB access of the calling routine */
    public TxnDetailsLimitRecorder(CustomerLimitRepository repository) {
        this.repository = repository;
    }

    /** <customer id>-<AAA id> */
    public static String recordId(CustomerLimitDTO request) {
        return request.getCustomerId() + "-" + request.getTransactionId();
    }

    /**
     * Writes the details of a transaction that was added to utilisation (called on input, UNAUTH).
     * @param effectiveDate value date of the transaction, stored in EFF.DATE
     */
    public void record(CustomerLimitDTO request, String accountNo, String effectiveDate) {
        String recordId = recordId(request);
        LogUtil.info(CLASS_NAME, "record", "START", "id=" + recordId);
        try {
            boolean credit = request.getCreditAmount().signum() > 0;
            EbAlbeTxnDetailsLimitRecord txnRec = repository.newTxnDetailsRecord();
            txnRec.setAccountNo(ValidationUtil.nvl(accountNo));
            txnRec.setCreditDebitFlag(credit ? Constants.FLAG_CREDIT : Constants.FLAG_DEBIT);
            txnRec.setEffDate(ValidationUtil.nvl(effectiveDate));
            txnRec.setTxnAmount((credit ? request.getCreditAmount() : request.getDebitAmount()).toPlainString());
            txnRec.setSystemDate(request.getTransactionDate().format(Constants.DAILY_ID_FORMAT));
            // Accepted EB-ALBE.* overrides, so reports can list the overridden transactions.
            txnRec.setOverrideIds(String.join(Constants.OVERRIDE_LIST_SEPARATOR, request.getAcceptedOverrides()));
            repository.writeTxnDetails(recordId, txnRec);
            LogUtil.info(CLASS_NAME, "record", "WRITTEN", "id=" + recordId + " account=" + accountNo + " flag="
                    + (credit ? Constants.FLAG_CREDIT : Constants.FLAG_DEBIT) + " effDate=" + effectiveDate
                    + " systemDate=" + request.getTransactionDate() + " amount="
                    + (credit ? request.getCreditAmount() : request.getDebitAmount())
                    + " overrides=" + request.getAcceptedOverrides());
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "record", "FAILED", "id=" + recordId, e);
        }
    }

    /**
     * Finds what was recorded for this transaction: direct read of <customer>-<AAA id>.
     * The returned entry carries the original SYSTEM.DATE, flag and amount.
     * @return 0 or 1 entries
     */
    public List<CustomerLimitDTO> findOriginal(CustomerLimitDTO request) {
        List<CustomerLimitDTO> originals = new ArrayList<>();
        String recordId = recordId(request);
        LogUtil.info(CLASS_NAME, "findOriginal", "START", "direct read id=" + recordId);
        CustomerLimitDTO original = toRequest(recordId, request.getCustomerId(), request.getTransactionId());
        if (original != null) {
            originals.add(original);
        }
        LogUtil.info(CLASS_NAME, "findOriginal", "END", "id=" + recordId + " found=" + originals.size());
        return originals;
    }

    /** Removes the record once reversed, so the same transaction is never reversed twice. */
    public void delete(CustomerLimitDTO original) {
        String recordId = recordId(original);
        try {
            repository.deleteTxnDetails(recordId);
            LogUtil.info(CLASS_NAME, "delete", "DELETED", "id=" + recordId);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "delete", "FAILED", "id=" + recordId, e);
        }
    }

    /**
     * Rebuilds the original transaction from its record: SYSTEM.DATE, flag and amount.
     * Null if there is no record. A blank SYSTEM.DATE falls back to today's server date.
     */
    private CustomerLimitDTO toRequest(String recordId, String customerId, String transactionId) {
        EbAlbeTxnDetailsLimitRecord txnRec = repository.readTxnDetails(recordId);
        if (txnRec == null) {
            return null;
        }
        try {
            BigDecimal amount = ValidationUtil.toAmount(txnRec.getTxnAmount().getValue());
            LocalDate countedOn = ValidationUtil.toDate(txnRec.getSystemDate().getValue());
            if (countedOn == null) {
                countedOn = LocalDate.now();
                LogUtil.error(CLASS_NAME, "toRequest", "INVALID", "id=" + recordId
                        + " SYSTEM.DATE blank/invalid, using today "
                        + countedOn);
            }

            CustomerLimitDTO original = new CustomerLimitDTO();
            original.setCustomerId(customerId);
            original.setTransactionId(transactionId);
            original.setTransactionDate(countedOn);
            if (Constants.FLAG_CREDIT.equalsIgnoreCase(txnRec.getCreditDebitFlag().getValue())) {
                original.setCreditAmount(amount);
            } else {
                original.setDebitAmount(amount);
            }
            LogUtil.info(CLASS_NAME, "toRequest", "FOUND", "id=" + recordId + " systemDate=" + countedOn
                    + " flag=" + txnRec.getCreditDebitFlag().getValue() + " amount=" + amount);
            return original;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "toRequest", "FAILED", "unable to use id=" + recordId, e);
            return null;
        }
    }
}
