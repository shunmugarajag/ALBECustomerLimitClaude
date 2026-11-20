package com.temenos.ALBE.CustomerLimit.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;
import com.temenos.t24.api.hook.arrangement.ActivityLifecycle;
import com.temenos.t24.api.records.ebalbetxndetailslimit.EbAlbeTxnDetailsLimitRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.tables.ebalbetxndetailslimit.EbAlbeTxnDetailsLimitTable;

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

    private static final String L3API = "L3API";
    private static final Logger logger = LoggerFactory.getLogger(L3API);
    private static final String LOG_PREFIX = "TxnDetailsLimitRecorder:";

    public static final String TXN_DETAILS_TABLE = "EB.ALBE.TXN.DETAILS.LIMIT";
    public static final String FLAG_CREDIT = "CR";
    public static final String FLAG_DEBIT = "DR";

    private static final DateTimeFormatter ID_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ActivityLifecycle hook;
    private final EbAlbeTxnDetailsLimitTable txnDetailsTable;

    /** @param hook the calling ACTIVITY.API routine; gives the T24 context for table access */
    public TxnDetailsLimitRecorder(ActivityLifecycle hook) {
        this.hook = hook;
        this.txnDetailsTable = new EbAlbeTxnDetailsLimitTable(hook);
    }

    /** <customer id>-<YYYYMMDD booking (server) date>-<AAA id> */
    public static String recordId(CustomerLimitDTO request) {
        return request.getCustomerId() + "-" + request.getTransactionDate().format(ID_DATE_FORMAT) + "-"
                + request.getTransactionId();
    }

    /**
     * Writes the details of a transaction that was added to utilisation (called on input, UNAUTH).
     * @param effectiveDate value date of the transaction, stored in EFF.DATE
     */
    public void record(CustomerLimitDTO request, String accountNo, String effectiveDate) {
        String recordId = recordId(request);
        try {
            boolean credit = request.getCreditAmount().signum() > 0;
            EbAlbeTxnDetailsLimitRecord txnRec = new EbAlbeTxnDetailsLimitRecord(hook);
            txnRec.setAccountNo(accountNo == null ? "" : accountNo);
            txnRec.setCreditDebitFlag(credit ? FLAG_CREDIT : FLAG_DEBIT);
            txnRec.setEffDate(effectiveDate == null ? "" : effectiveDate);
            txnRec.setTxnAmount((credit ? request.getCreditAmount() : request.getDebitAmount()).toPlainString());
            txnDetailsTable.write(recordId, txnRec);
            logger.info(LOG_PREFIX + "record WRITTEN id=" + recordId + " account=" + accountNo + " flag="
                    + (credit ? FLAG_CREDIT : FLAG_DEBIT) + " effDate=" + effectiveDate + " amount="
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
            DataAccess daObj = new DataAccess(hook);
            // The booking date in the id is not known at reversal time, so match on customer + AAA id.
            String selection = "WITH @ID LIKE " + customerId + "-...-" + transactionId;
            List<String> recordIds = daObj.selectRecords("", TXN_DETAILS_TABLE, "", selection);
            logger.info(LOG_PREFIX + "findOriginal selection=[" + selection + "] found=" + recordIds);
            if (recordIds == null) {
                return originals;
            }
            for (String recordId : recordIds) {
                CustomerLimitDTO original = toRequest(daObj, recordId, customerId, transactionId);
                if (original != null) {
                    originals.add(original);
                }
            }
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
            txnDetailsTable.delete(recordId);
            logger.info(LOG_PREFIX + "delete DELETED id=" + recordId);
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "delete FAILED id=" + recordId + " " + e.getMessage());
        }
    }

    /**
     * Rebuilds the original transaction from its record: date from the id,
     * flag and amount from the fields. Null if the id does not match or cannot be read.
     */
    private CustomerLimitDTO toRequest(DataAccess daObj, String recordId, String customerId, String transactionId) {
        String prefix = customerId + "-";
        String suffix = "-" + transactionId;
        if (!recordId.startsWith(prefix) || !recordId.endsWith(suffix)) {
            logger.info(LOG_PREFIX + "toRequest SKIP id=" + recordId + " does not match customer / AAA id");
            return null;
        }
        String datePart = recordId.substring(prefix.length(), recordId.length() - suffix.length());
        try {
            EbAlbeTxnDetailsLimitRecord txnRec =
                    new EbAlbeTxnDetailsLimitRecord(daObj.getRecord(TXN_DETAILS_TABLE, recordId));
            BigDecimal amount = CustomerLimitUtilizationUpdater.toAmount(txnRec.getTxnAmount().getValue());

            CustomerLimitDTO original = new CustomerLimitDTO();
            original.setCustomerId(customerId);
            original.setTransactionId(transactionId);
            original.setTransactionDate(LocalDate.parse(datePart, ID_DATE_FORMAT));
            if (FLAG_CREDIT.equalsIgnoreCase(txnRec.getCreditDebitFlag().getValue())) {
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
