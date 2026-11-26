package com.temenos.ALBE.CustomerLimit.service.activity;

import java.math.BigDecimal;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.t24.api.records.ebalbetxndetailslimit.EbAlbeTxnDetailsLimitRecord;

/*-----------------------------------------------------------------------------
 * Description : EB.ALBE.TXN.DETAILS.LIMIT - one record for every transaction counted in
 *               EB.ALBE.UTILISED.LIMIT (written at input), so a re-input, a rejected input
 *               or a reversal takes back exactly what was counted, from the day it was counted on.
 *
 *               Record id : <customer>-<YYYYMMDD>-<transaction key>-<arrangement id>
 *                           YYYYMMDD = the activity's ORG.SYSTEM.DATE (T24 date it was counted on)
 *                           transaction key = FT / TT behind the activity, else the AAA id
 *                           arrangement = makes the two sides of an own-account transfer two
 *                           records, so both are counted (FQ6)
 *               Fields    : ACCOUNT.NO, CREDIT.DEBIT.FLAG (CR/DR), EFF.DATE (value date),
 *                           TXN.AMOUNT (in the limit currency). No other field is written.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 * 23-Nov-2026   Development      Id <customer>-<date>-<FT/AAA>-<arrangement> (FQ6)
 * 26-Nov-2026   Development      Clean rewrite (D47): findCounted returns the one record or null
 *-----------------------------------------------------------------------------
 */
public class TxnDetailsLimitRecorder {
    private static final String CLASS_NAME = "TxnDetailsLimitRecorder";
    private static final String LABEL_ID = "id=";

    private final CustomerLimitRepository repository;

    /** @param repository DB access of the calling routine */
    public TxnDetailsLimitRecorder(CustomerLimitRepository repository) {
        this.repository = repository;
    }

    /** <customer>-<YYYYMMDD>-<transaction key>-<arrangement id> of the request. */
    public static String recordId(CustomerLimitDTO request) {
        return request.getCustomerId() + Constants.TXN_ID_SEPARATOR
                + request.getTransactionDate().format(Constants.DAILY_ID_FORMAT) + Constants.TXN_ID_SEPARATOR
                + request.getTransactionId() + Constants.TXN_ID_SEPARATOR + ValidationUtil.nvl(request.getArrangementId());
    }

    /**
     * Writes (or replaces) the record of a counted transaction.
     * @param effectiveDate value date of the transaction, stored in EFF.DATE
     */
    public void record(CustomerLimitDTO request, String accountNo, String effectiveDate) {
        String recordId = recordId(request);
        try {
            boolean credit = request.isCredit();
            EbAlbeTxnDetailsLimitRecord txnRec = repository.newTxnDetailsRecord();
            txnRec.setAccountNo(ValidationUtil.nvl(accountNo));
            txnRec.setCreditDebitFlag(credit ? Constants.FLAG_CREDIT : Constants.FLAG_DEBIT);
            txnRec.setEffDate(ValidationUtil.nvl(effectiveDate));
            txnRec.setTxnAmount(request.getAmount().toPlainString());
            if (repository.writeTxnDetails(recordId, txnRec)) {
                request.addNote("saved EB.ALBE.TXN.DETAILS.LIMIT " + recordId);
            } else {
                request.addNote("EB.ALBE.TXN.DETAILS.LIMIT " + recordId + " SAVE FAILED");
            }
        } catch (Exception e) {
            LogUtil.info(CLASS_NAME, "record", "FAILED", LABEL_ID + recordId, e);
            request.addNote("EB.ALBE.TXN.DETAILS.LIMIT " + recordId + " SAVE FAILED");
            repository.markSaveFailed(CLASS_NAME, "record " + recordId);
        }
    }

    /**
     * What was counted for this transaction on the request's date (direct read by key).
     * @return the counted transaction (customer, key, arrangement, date, credit or debit amount);
     *         null when nothing was counted under this key
     */
    public CustomerLimitDTO findCounted(CustomerLimitDTO request) {
        String recordId = recordId(request);
        EbAlbeTxnDetailsLimitRecord txnRec = repository.readTxnDetails(recordId);
        if (txnRec == null) {
            return null;
        }
        try {
            BigDecimal amount = ValidationUtil.toAmount(txnRec.getTxnAmount().getValue());
            CustomerLimitDTO counted = new CustomerLimitDTO();
            counted.setCustomerId(request.getCustomerId());
            counted.setTransactionId(request.getTransactionId());
            counted.setArrangementId(request.getArrangementId());
            counted.setTransactionDate(request.getTransactionDate());
            if (Constants.FLAG_CREDIT.equalsIgnoreCase(txnRec.getCreditDebitFlag().getValue())) {
                counted.setCreditAmount(amount);
            } else {
                counted.setDebitAmount(amount);
            }
            LogUtil.info(CLASS_NAME, "findCounted", "FOUND", LABEL_ID + recordId + " flag="
                    + txnRec.getCreditDebitFlag().getValue() + " amount=" + amount);
            return counted;
        } catch (Exception e) {
            LogUtil.info(CLASS_NAME, "findCounted", "FAILED", "unable to use " + LABEL_ID + recordId, e);
            return null;
        }
    }

    /** Removes the record once taken back, so the same transaction is never taken back twice. */
    public void delete(CustomerLimitDTO counted) {
        String recordId = recordId(counted);
        try {
            repository.deleteTxnDetails(recordId);
        } catch (Exception e) {
            LogUtil.info(CLASS_NAME, "delete", "FAILED", LABEL_ID + recordId, e);
            repository.markSaveFailed(CLASS_NAME, "delete " + recordId);
        }
    }
}
