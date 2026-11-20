package com.temenos.ALBE.CustomerLimit.service;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.t24.api.records.ebalbetxndetailslimit.EbAlbeTxnDetailsLimitRecord;

/*-----------------------------------------------------------------------------
 * Description : Keeps EB.ALBE.TXN.DETAILS.LIMIT for every transaction counted in
 *               EB.ALBE.UTILISED.LIMIT (written at input), so a re-input, a rejected
 *               input or a reversal subtracts from the original day and month.
 *
 *               Record id : <customer id>-<YYYYMMDD server date>-<AAA id>
 *               Fields    : ACCOUNT.NO, CREDIT.DEBIT.FLAG (CR/DR), EFF.DATE, TXN.AMOUNT
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class TxnDetailsLimitRecorder {

    private static final Logger logger = LoggerFactory.getLogger(Constants.LOGGER_NAME);
    private static final String LOG_PREFIX = "TxnDetailsLimitRecorder:";

    private final CustomerLimitRepository repository;

    /** @param repository DB access of the calling routine */
    public TxnDetailsLimitRecorder(CustomerLimitRepository repository) {
        this.repository = repository;
    }

    /** <customer id>-<YYYYMMDD booking (server) date>-<AAA id> */
    public static String recordId(CustomerLimitDTO request) {
        return request.getCustomerId() + "-" + request.getTransactionDate().format(Constants.DAILY_ID_FORMAT) + "-"
                + request.getTransactionId();
    }

    /**
     * Writes the details of a transaction that was added to utilisation (called on input, UNAUTH).
     * @param effectiveDate value date of the transaction, stored in EFF.DATE
     */
    public void record(CustomerLimitDTO request, String accountNo, String effectiveDate) {
        String recordId = recordId(request);
        logger.info(LOG_PREFIX + "record START id=" + recordId);
        try {
            boolean credit = request.getCreditAmount().signum() > 0;
            EbAlbeTxnDetailsLimitRecord txnRec = repository.newTxnDetailsRecord();
            txnRec.setAccountNo(accountNo == null ? "" : accountNo);
            txnRec.setCreditDebitFlag(credit ? Constants.FLAG_CREDIT : Constants.FLAG_DEBIT);
            txnRec.setEffDate(effectiveDate == null ? "" : effectiveDate);
            txnRec.setTxnAmount((credit ? request.getCreditAmount() : request.getDebitAmount()).toPlainString());
            repository.writeTxnDetails(recordId, txnRec);
            logger.info(LOG_PREFIX + "record WRITTEN id=" + recordId + " account=" + accountNo + " flag="
                    + (credit ? Constants.FLAG_CREDIT : Constants.FLAG_DEBIT) + " effDate=" + effectiveDate + " amount="
                    + (credit ? request.getCreditAmount() : request.getDebitAmount()));
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "record FAILED id=" + recordId + " " + e.getMessage());
        }
    }

    /**
     * Finds the original transaction(s) recorded for this customer and transaction id,
     * whatever the booking date. Each result carries the original date, flag and amount.
     */
    public List<CustomerLimitDTO> findOriginal(String customerId, String transactionId) {
        List<CustomerLimitDTO> originals = new ArrayList<>();
        try {
            // The booking date in the id is not known at reversal time, so match on customer + AAA id.
            List<String> recordIds = repository.selectTxnDetailsIds(customerId, transactionId);
            for (String recordId : recordIds) {
                CustomerLimitDTO original = toRequest(recordId, customerId, transactionId);
                if (original != null) {
                    originals.add(original);
                }
            }
            logger.info(LOG_PREFIX + "findOriginal END customer=" + customerId + " aaa=" + transactionId
                    + " matched=" + originals.size());
        } catch (Exception e) {
            logger.info(LOG_PREFIX + "findOriginal no record for customer=" + customerId + " aaa=" + transactionId
                    + " " + e.getMessage());
        }
        return originals;
    }

    /** Removes the record once reversed, so the same transaction is never reversed twice. */
    public void delete(CustomerLimitDTO original) {
        String recordId = recordId(original);
        try {
            repository.deleteTxnDetails(recordId);
            logger.info(LOG_PREFIX + "delete DONE id=" + recordId);
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "delete FAILED id=" + recordId + " " + e.getMessage());
        }
    }

    /**
     * Rebuilds the original transaction from its record: date from the id,
     * flag and amount from the fields. Null if the id does not match or cannot be read.
     */
    private CustomerLimitDTO toRequest(String recordId, String customerId, String transactionId) {
        String prefix = customerId + "-";
        String suffix = "-" + transactionId;
        if (!recordId.startsWith(prefix) || !recordId.endsWith(suffix)) {
            logger.info(LOG_PREFIX + "toRequest SKIP id=" + recordId + " does not match customer / AAA id");
            return null;
        }
        String datePart = recordId.substring(prefix.length(), recordId.length() - suffix.length());
        try {
            EbAlbeTxnDetailsLimitRecord txnRec = repository.readTxnDetails(recordId);
            if (txnRec == null) {
                return null;
            }
            BigDecimal amount = CustomerLimitUtilizationUpdater.toAmount(txnRec.getTxnAmount().getValue());

            CustomerLimitDTO original = new CustomerLimitDTO();
            original.setCustomerId(customerId);
            original.setTransactionId(transactionId);
            original.setTransactionDate(LocalDate.parse(datePart, Constants.DAILY_ID_FORMAT));
            if (Constants.FLAG_CREDIT.equalsIgnoreCase(txnRec.getCreditDebitFlag().getValue())) {
                original.setCreditAmount(amount);
            } else {
                original.setDebitAmount(amount);
            }
            logger.info(LOG_PREFIX + "toRequest id=" + recordId + " date=" + original.getTransactionDate()
                    + " flag=" + txnRec.getCreditDebitFlag().getValue() + " amount=" + amount);
            return original;
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "toRequest unable to read id=" + recordId + " " + e.getMessage());
            return null;
        }
    }
}
