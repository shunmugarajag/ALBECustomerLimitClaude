package com.temenos.ALBE.CustomerLimit.service.activity;

import java.math.BigDecimal;
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
 *               Record id : <customer id>-<YYYYMMDD>-<transaction key>   (HLSD format)
 *                           YYYYMMDD = T24 date the amount was counted on. It is kept
 *                           in the activity's REMARKS (ALBE.LIMIT.DATE=YYYYMMDD, written by
 *                           DailyMonthlyLimitController.defaultFieldValues), so every later
 *                           step - also a reversal on another day - rebuilds the id and
 *                           reads the record directly by key.
 *               Fields    : ACCOUNT.NO, CREDIT.DEBIT.FLAG (CR/DR), EFF.DATE (value date),
 *                           TXN.AMOUNT (in the limit currency). No other field is written.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 * 21-Nov-2026   Development      HLSD id <customer>-<date>-<AAA>; date from AAA REMARKS
 *-----------------------------------------------------------------------------
 */
public class TxnDetailsLimitRecorder {
    private static final String CLASS_NAME = "TxnDetailsLimitRecorder";

    private final CustomerLimitRepository repository;

    /** @param repository DB access of the calling routine */
    public TxnDetailsLimitRecorder(CustomerLimitRepository repository) {
        this.repository = repository;
    }

    /** <customer id>-<YYYYMMDD counted on>-<transaction key: root reference of the FT / TT / AAA / entry> */
    public static String recordId(CustomerLimitDTO request) {
        return request.getCustomerId() + "-" + request.getTransactionDate().format(Constants.DAILY_ID_FORMAT)
                + "-" + request.getTransactionId();
    }

    /**
     * Writes the details of a transaction that was added to utilisation (called on input, UNAUTH).
     * @param effectiveDate value date of the transaction, stored in EFF.DATE
     */
    public void record(CustomerLimitDTO request, String accountNo, String effectiveDate) {
        String recordId = recordId(request);
        LogUtil.info(CLASS_NAME, "record", "START", "id=" + recordId);
        try {
            boolean credit = request.isCredit();
            EbAlbeTxnDetailsLimitRecord txnRec = repository.newTxnDetailsRecord();
            txnRec.setAccountNo(ValidationUtil.nvl(accountNo));
            txnRec.setCreditDebitFlag(credit ? Constants.FLAG_CREDIT : Constants.FLAG_DEBIT);
            txnRec.setEffDate(ValidationUtil.nvl(effectiveDate));
            txnRec.setTxnAmount(request.getAmount().toPlainString());
            repository.writeTxnDetails(recordId, txnRec);
            LogUtil.info(CLASS_NAME, "record", "WRITTEN", "id=" + recordId + " account=" + accountNo + " flag="
                    + (credit ? Constants.FLAG_CREDIT : Constants.FLAG_DEBIT) + " effDate=" + effectiveDate
                    + " amount=" + request.getAmount());
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "record", "FAILED", "id=" + recordId, e);
        }
    }

    /**
     * Finds what was recorded for this transaction: direct read of <customer>-<date>-<transaction key>,
     * the date being the one the request carries (from the activity REMARKS).
     * The returned entry carries the original date (from the id), flag and amount.
     * @return 0 or 1 entries
     */
    public List<CustomerLimitDTO> findOriginal(CustomerLimitDTO request) {
        List<CustomerLimitDTO> originals = new ArrayList<>();
        String recordId = recordId(request);
        LogUtil.info(CLASS_NAME, "findOriginal", "START", "direct read id=" + recordId);
        CustomerLimitDTO original = toRequest(recordId, request);
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

    /** Rebuilds the original transaction from its record (flag, amount); the date is the id's. */
    private CustomerLimitDTO toRequest(String recordId, CustomerLimitDTO request) {
        EbAlbeTxnDetailsLimitRecord txnRec = repository.readTxnDetails(recordId);
        if (txnRec == null) {
            return null;
        }
        try {
            BigDecimal amount = ValidationUtil.toAmount(txnRec.getTxnAmount().getValue());
            CustomerLimitDTO original = new CustomerLimitDTO();
            original.setCustomerId(request.getCustomerId());
            original.setTransactionId(request.getTransactionId());
            original.setTransactionDate(request.getTransactionDate());
            if (Constants.FLAG_CREDIT.equalsIgnoreCase(txnRec.getCreditDebitFlag().getValue())) {
                original.setCreditAmount(amount);
            } else {
                original.setDebitAmount(amount);
            }
            LogUtil.info(CLASS_NAME, "toRequest", "FOUND", "id=" + recordId + " countedOn="
                    + original.getTransactionDate() + " flag=" + txnRec.getCreditDebitFlag().getValue()
                    + " amount=" + amount);
            return original;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "toRequest", "FAILED", "unable to use id=" + recordId, e);
            return null;
        }
    }
}
