package com.temenos.ALBE.CustomerLimit.Routine;

import java.math.BigDecimal;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.service.CustomerLimitReader;
import com.temenos.ALBE.CustomerLimit.service.CustomerLimitReader.Limits;
import com.temenos.api.TStructure;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;
import com.temenos.t24.api.complex.eb.servicehook.TransactionData;
import com.temenos.t24.api.complex.eb.templatehook.TransactionContext;
import com.temenos.t24.api.hook.system.RecordLifecycle;
import com.temenos.t24.api.records.ebalbeciflimit.EbAlbeCifLimitRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.tables.ebalbeciflimit.EbAlbeCifLimitTable;

/*-----------------------------------------------------------------------------
 * Attached as : After Unau routine
 * Attached to : VERSION CUSTOMER,ALBE.CORP
 * Description : Customer limit creation routine (HLSD 7.2.1 - 1).
 *               On customer input, creates EB.ALBE.CIF.LIMIT <customer id> from the
 *               generic limits in EB.ALBE.GEN.PARAM SYSTEM. An existing customer
 *               record is never overwritten, so limits amended for the customer stay.
 *               On deletion of the unauthorised customer, deletes the
 *               EB.ALBE.CIF.LIMIT record as well.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitCreation extends RecordLifecycle {

    private static final String L3API = "L3API";
    private static final Logger logger = LoggerFactory.getLogger(L3API);
    private static final String LOG_PREFIX = "CustomerLimitCreation:";

    // T24 function codes for delete of the unauthorised customer
    private static final String FUNCTION_DELETE = "D";
    private static final String FUNCTION_DELETE_FULL = "DELETE";

    /**
     * After Unau: create EB.ALBE.CIF.LIMIT from EB.ALBE.GEN.PARAM SYSTEM if the customer
     * has no record; on delete of the unauthorised customer, delete it.
     */
    @Override
    public void postUpdateRequest(String application, String currentRecordId, TStructure currentRecord,
            List<TransactionData> transactionData, List<TStructure> currentRecords,
            TransactionContext transactionContext) {

        long startTime = System.currentTimeMillis();
        try {
            String function = transactionContext == null ? null : transactionContext.getCurrentFunction();
            logger.info(LOG_PREFIX + "postUpdateRequest START customer=" + currentRecordId + " function=" + function);

            EbAlbeCifLimitTable cifLimitTable = new EbAlbeCifLimitTable(this);

            // Customer deleted before authorisation: remove its limit record as well.
            if (isDelete(transactionContext)) {
                deleteCustomerLimit(cifLimitTable, currentRecordId);
                return;
            }

            CustomerLimitReader limitReader = new CustomerLimitReader(new DataAccess(this));

            // Never overwrite: limits may have been amended for this customer.
            if (limitReader.getCustomerLimits(currentRecordId) != null) {
                logger.info(LOG_PREFIX + "postUpdateRequest SKIP EB.ALBE.CIF.LIMIT already exists for "
                        + currentRecordId);
                return;
            }

            Limits genericLimits = limitReader.getGenericLimits();
            if (genericLimits == null) {
                logger.error(LOG_PREFIX + "postUpdateRequest EB.ALBE.GEN.PARAM SYSTEM not found, limit not created for "
                        + currentRecordId);
                return;
            }

            // Copy the generic limits one-to-one into the customer's record.

            EbAlbeCifLimitRecord cifLimitRec = new EbAlbeCifLimitRecord(this);
            cifLimitRec.setCcy(nvl(genericLimits.ccy));
            cifLimitRec.setDailyCreditLimit(toText(genericLimits.dailyCreditLimit));
            cifLimitRec.setDailyDebitLimit(toText(genericLimits.dailyDebitLimit));
            cifLimitRec.setMonthlyCreditLimit(toText(genericLimits.monthlyCreditLimit));
            cifLimitRec.setMonthlyDebitLimit(toText(genericLimits.monthlyDebitLimit));
            cifLimitRec.setMaxCifBalance(toText(genericLimits.maxCifBalance));

            logger.info(LOG_PREFIX + "postUpdateRequest writing EB.ALBE.CIF.LIMIT>" + currentRecordId
                    + " from " + genericLimits.source);
            cifLimitTable.write(currentRecordId, cifLimitRec);
            logger.info(LOG_PREFIX + "postUpdateRequest CREATED EB.ALBE.CIF.LIMIT customer=" + currentRecordId
                    + " ccy=" + genericLimits.ccy + " dailyCredit=" + genericLimits.dailyCreditLimit
                    + " dailyDebit=" + genericLimits.dailyDebitLimit + " monthlyCredit="
                    + genericLimits.monthlyCreditLimit + " monthlyDebit=" + genericLimits.monthlyDebitLimit
                    + " maxCifBalance=" + genericLimits.maxCifBalance + " elapsedMs="
                    + (System.currentTimeMillis() - startTime));

        } catch (Exception e) {
            logger.error(LOG_PREFIX + "postUpdateRequest ERROR customer=" + currentRecordId + " " + e.getMessage());
        }
    }

    /** True when the current function is delete ("D" / "DELETE"). */
    private boolean isDelete(TransactionContext transactionContext) {
        String function = transactionContext == null ? null : transactionContext.getCurrentFunction();
        return FUNCTION_DELETE.equalsIgnoreCase(function) || FUNCTION_DELETE_FULL.equalsIgnoreCase(function);
    }

    private void deleteCustomerLimit(EbAlbeCifLimitTable cifLimitTable, String customerId) {
        try {
            cifLimitTable.delete(customerId);
            logger.info(LOG_PREFIX + "deleteCustomerLimit DELETED EB.ALBE.CIF.LIMIT customer=" + customerId);
        } catch (Exception e) {
            logger.info(LOG_PREFIX + "deleteCustomerLimit no EB.ALBE.CIF.LIMIT to delete for " + customerId);
        }
    }

    /** Amount as text for the record; blank when the generic limit is not configured. */
    private String toText(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
