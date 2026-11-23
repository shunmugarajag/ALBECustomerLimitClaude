package com.temenos.ALBE.CustomerLimit.controller.customer;

import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.api.TStructure;
import com.temenos.t24.api.complex.eb.servicehook.TransactionData;
import com.temenos.t24.api.complex.eb.templatehook.TransactionContext;
import com.temenos.t24.api.hook.system.RecordLifecycle;
import com.temenos.t24.api.records.ebalbeciflimit.EbAlbeCifLimitRecord;
import com.temenos.t24.api.records.ebalbegenparam.EbAlbeGenParamRecord;
import com.temenos.t24.api.records.ebalbegenparam.KeyNameClass;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.tables.ebalbeciflimit.EbAlbeCifLimitTable;

/*-----------------------------------------------------------------------------
 * Attached as : Auth routine (alternative to CustomerLimitCreationController - attach only one)
 * Attached to : VERSION CUSTOMER,ALBE.CORP
 * Description : The whole customer limit creation (HLSD 7.2.1 - 1) in ONE class, for review
 *               and handover. Same behaviour as CustomerLimitCreationController +
 *               CustomerLimitCreationService + CustomerLimitReader + CustomerLimitRepository:
 *                 1. customer id blank                      -> nothing
 *                 2. EB.ALBE.CIF.LIMIT <customer> exists     -> nothing (amendment, never overwrite)
 *                    read failed (not "not found")          -> nothing (never overwrite)
 *                 3. read EB.ALBE.GEN.PARAM CUSTOMER.LIMIT
 *                 4. copy CURRENCY -> CCY, the 4 daily / monthly limits, and
 *                    MAXIMUM.OVERALL.CIF.BALANCE -> MAX.CIF.BALANCE
 *                 5. write EB.ALBE.CIF.LIMIT <customer>
 *               Exception to the project rule "DB access only in CustomerLimitRepository":
 *               this class reads and writes itself, so it can be read on its own.
 *               Literals still come from Constants; logging still goes through LogUtil.
 *               GEN.PARAM is mandatory config (decision D27): values are copied as they are.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 23-Nov-2026   Development      Initial Version (single-class copy of the creation routine)
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitCreationSingleController extends RecordLifecycle {
    private static final String CLASS_NAME = "CustomerLimitCreationSingleController";
    private static final String METHOD_UPDATE = "postUpdateRequest";
    private static final String LABEL_CUSTOMER = "customer=";

    /** Auth: create the customer's limit record from the generic limits (if it does not exist yet). */
    @Override
    public void postUpdateRequest(String application, String currentRecordId, TStructure currentRecord,
            List<TransactionData> transactionData, List<TStructure> currentRecords,
            TransactionContext transactionContext) {

        long startTime = System.currentTimeMillis();
        try {
            LogUtil.info(CLASS_NAME, METHOD_UPDATE, "START", "application=" + application + " "
                    + LABEL_CUSTOMER + currentRecordId);

            // 1. Customer id must be present.
            if (ValidationUtil.isBlank(currentRecordId)) {
                LogUtil.info(CLASS_NAME, METHOD_UPDATE, "SKIP", "customer id missing");
                return;
            }
            String customerId = currentRecordId.trim();
            DataAccess dataAccess = new DataAccess(this);

            // 2. Never overwrite: create only when T24 says "no record".
            Boolean exists = cifLimitExists(dataAccess, customerId);
            if (exists == null || exists) {
                return;
            }

            // 3. Generic limits.
            EbAlbeGenParamRecord genParamRec = readGenParam(dataAccess);
            if (genParamRec == null) {
                return;
            }

            // 4. Copy the generic limits into a new customer record.
            EbAlbeCifLimitRecord cifLimitRec = buildCifLimit(genParamRec);

            // 5. Write it.
            boolean written = writeCifLimit(customerId, cifLimitRec);

            LogUtil.info(CLASS_NAME, METHOD_UPDATE, "END", LABEL_CUSTOMER + customerId + " created=" + written
                    + " elapsedMs=" + (System.currentTimeMillis() - startTime));

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, METHOD_UPDATE, "ERROR", LABEL_CUSTOMER + currentRecordId, e);
        }
    }

    /**
     * TRUE record exists, FALSE T24 reports no such record, null the read failed for another
     * reason (then nothing is created, so an existing record can never be overwritten).
     */
    private Boolean cifLimitExists(DataAccess dataAccess, String customerId) {
        String key = Constants.CIF_LIMIT_TABLE + ">" + customerId;
        try {
            EbAlbeCifLimitRecord cifLimitRec =
                    new EbAlbeCifLimitRecord(dataAccess.getRecord(Constants.CIF_LIMIT_TABLE, customerId));
            boolean exists = !cifLimitRec.toString().isEmpty();
            LogUtil.info(CLASS_NAME, "cifLimitExists", exists ? "SKIP" : "NOT FOUND",
                    key + (exists ? " already exists (customer amendment, limits kept)" : ""));
            return exists;
        } catch (Exception e) {
            if (isRecordNotFound(e)) {
                LogUtil.info(CLASS_NAME, "cifLimitExists", "NOT FOUND", key);
                return Boolean.FALSE;
            }
            LogUtil.error(CLASS_NAME, "cifLimitExists", "SKIP", key + " read failed, not created", e);
            return null;
        }
    }

    /** True when the exception from a read means "record not found" rather than a real failure. */
    private boolean isRecordNotFound(Exception e) {
        String text = (e.getClass().getSimpleName() + " " + e.getMessage()).toUpperCase();
        for (String marker : Constants.RECORD_NOT_FOUND_MARKERS) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /** EB.ALBE.GEN.PARAM CUSTOMER.LIMIT; null when it cannot be read. */
    private EbAlbeGenParamRecord readGenParam(DataAccess dataAccess) {
        String key = Constants.GEN_PARAM_TABLE + ">" + Constants.GEN_PARAM_ID;
        try {
            EbAlbeGenParamRecord genParamRec = new EbAlbeGenParamRecord(
                    dataAccess.getRecord(Constants.GEN_PARAM_TABLE, Constants.GEN_PARAM_ID));
            LogUtil.info(CLASS_NAME, "readGenParam", "FOUND", key);
            return genParamRec;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "readGenParam", "NOT FOUND", key + ", limit not created", e);
            return null;
        }
    }

    /** New EB.ALBE.CIF.LIMIT record filled from the GEN.PARAM KEY.NAME / KEY.VALUE pairs. */
    private EbAlbeCifLimitRecord buildCifLimit(EbAlbeGenParamRecord genParamRec) {
        EbAlbeCifLimitRecord cifLimitRec = new EbAlbeCifLimitRecord(this);
        for (KeyNameClass keyName : genParamRec.getKeyName()) {
            String key = ValidationUtil.nvl(keyName.getKeyName().getValue()).trim();
            String value = ValidationUtil.nvl(keyName.getKeyValue().getValue()).trim();
            LogUtil.info(CLASS_NAME, "buildCifLimit", "CALC", "key=" + key + " value=" + value);
            switch (key) {
            case Constants.PARAM_KEY_CCY:
                cifLimitRec.setCcy(value);
                break;
            case Constants.PARAM_KEY_DAILY_CREDIT_LIMIT:
                cifLimitRec.setDailyCreditLimit(value);
                break;
            case Constants.PARAM_KEY_DAILY_DEBIT_LIMIT:
                cifLimitRec.setDailyDebitLimit(value);
                break;
            case Constants.PARAM_KEY_MONTHLY_CREDIT_LIMIT:
                cifLimitRec.setMonthlyCreditLimit(value);
                break;
            case Constants.PARAM_KEY_MONTHLY_DEBIT_LIMIT:
                cifLimitRec.setMonthlyDebitLimit(value);
                break;
            case Constants.PARAM_KEY_MAX_CIF_BALANCE:
                cifLimitRec.setMaxCifBalance(value);
                break;
            default:
                // Other keys in the parameter record are not limit values.
            }
        }
        return cifLimitRec;
    }

    /** Writes EB.ALBE.CIF.LIMIT <customer>; false when the write fails (logged). */
    private boolean writeCifLimit(String customerId, EbAlbeCifLimitRecord cifLimitRec) {
        String key = Constants.CIF_LIMIT_TABLE + ">" + customerId;
        try {
            new EbAlbeCifLimitTable(this).write(customerId, cifLimitRec);
            LogUtil.info(CLASS_NAME, "writeCifLimit", "WRITTEN", key);
            return true;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "writeCifLimit", "FAILED", key, e);
            return false;
        }
    }
}
