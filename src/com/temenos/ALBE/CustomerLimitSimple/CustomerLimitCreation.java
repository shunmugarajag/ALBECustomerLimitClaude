package com.temenos.ALBE.CustomerLimitSimple;

import java.util.List;

import com.temenos.api.TStructure;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;
import com.temenos.t24.api.complex.eb.servicehook.TransactionData;
import com.temenos.t24.api.complex.eb.templatehook.TransactionContext;
import com.temenos.t24.api.hook.system.RecordLifecycle;
import com.temenos.t24.api.records.ebalbeciflimits.EbAlbeCifLimitsRecord;
import com.temenos.t24.api.records.ebalbegenparam.EbAlbeGenParamRecord;
import com.temenos.t24.api.records.ebalbegenparam.KeyNameClass;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.tables.ebalbeciflimits.EbAlbeCifLimitsTable;

/*-----------------------------------------------------------------------------
 * Attached as : Auth Routine
 * EB.API      : ALBE.CL.LIMIT.CREATE.SIMPLE
 * Attached to : VERSION CUSTOMER,ALBE.CORP
 * Description : When a customer is authorised, creates EB.ALBE.CIF.LIMITS <customer> from
 *               EB.ALBE.GEN.PARAM CUSTOMER.LIMIT (KEY.NAME / KEY.VALUE), only if it does not exist.
 *               An existing record (customer amended and authorised again) is never overwritten;
 *               if the read fails for any other reason than "not found", nothing is written.
 *               Simple one-class version of CustomerLimitCreationController (same behaviour).
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 26-Nov-2026   Development      Initial Version (simple 2-class version)
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitCreation extends RecordLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(CustomerLimitConstants.LOGGER_NAME);
    private static final String NAME = "CustomerLimitCreation:";

    @Override
    public void postUpdateRequest(String application, String currentRecordId, TStructure currentRecord,
            List<TransactionData> transactionData, List<TStructure> currentRecords,
            TransactionContext transactionContext) {
        String customer = currentRecordId == null ? "" : currentRecordId.trim();
        try {
            if (customer.isEmpty()) {
                log("SKIP", "customer id missing");
                return;
            }
            DataAccess da = new DataAccess(this);

            // never overwrite: create only when T24 says "no record"
            Boolean exists = cifLimitExists(da, customer);
            if (exists == null) {
                log("SKIP", customer + " " + CustomerLimitConstants.CIF_LIMIT_TABLE
                        + " read failed - not created, so an existing record is not overwritten");
                return;
            }
            if (exists) {
                log("SKIP", customer + " " + CustomerLimitConstants.CIF_LIMIT_TABLE + " already exists, limits kept");
                return;
            }

            EbAlbeGenParamRecord param;
            try {
                param = new EbAlbeGenParamRecord(da.getRecord(CustomerLimitConstants.GEN_PARAM_TABLE,
                        CustomerLimitConstants.GEN_PARAM_LIMIT_ID));
            } catch (Exception e) {
                log("NOT FOUND", CustomerLimitConstants.GEN_PARAM_TABLE + ">" + CustomerLimitConstants.GEN_PARAM_LIMIT_ID
                        + " - limit not created for " + customer);
                return;
            }

            EbAlbeCifLimitsRecord limits = new EbAlbeCifLimitsRecord(this);
            boolean found = false;
            for (KeyNameClass k : param.getKeyName()) {
                String key = k.getKeyName().getValue() == null ? "" : k.getKeyName().getValue().trim();
                String value = k.getKeyValue().getValue() == null ? "" : k.getKeyValue().getValue().trim();
                found = found || !key.isEmpty();
                switch (key) {
                    case CustomerLimitConstants.KEY_CURRENCY:
                        limits.setCurrency(value);
                        break;
                    case CustomerLimitConstants.KEY_DAILY_CREDIT:
                        limits.setDailyCreditLimit(value);
                        break;
                    case CustomerLimitConstants.KEY_DAILY_DEBIT:
                        limits.setDailyDebitLimit(value);
                        break;
                    case CustomerLimitConstants.KEY_MONTHLY_CREDIT:
                        limits.setMonthlyCreditLimit(value);
                        break;
                    case CustomerLimitConstants.KEY_MONTHLY_DEBIT:
                        limits.setMonthlyDebitLimit(value);
                        break;
                    case CustomerLimitConstants.KEY_MAX_CIF:
                        limits.setMaximumOverallCifAmount(value);
                        break;
                    default:
                }
            }
            if (!found) {
                log("NOT FOUND", CustomerLimitConstants.GEN_PARAM_LIMIT_ID + " has no keys - limit not created for "
                        + customer);
                return;
            }
            new EbAlbeCifLimitsTable(this).write(customer, limits);
            log("CREATED", CustomerLimitConstants.CIF_LIMIT_TABLE + ">" + customer + " from "
                    + CustomerLimitConstants.GEN_PARAM_LIMIT_ID);
        } catch (Exception e) {
            log("ERROR", "customer " + customer + " " + e);
        }
    }

    /** TRUE record exists, FALSE T24 says not found, null the read failed for another reason. */
    private Boolean cifLimitExists(DataAccess da, String customer) {
        try {
            EbAlbeCifLimitsRecord rec = new EbAlbeCifLimitsRecord(da.getRecord(CustomerLimitConstants.CIF_LIMIT_TABLE,
                    customer));
            return !rec.toString().isEmpty();
        } catch (Exception e) {
            String text = (e.getClass().getSimpleName() + " " + e.getMessage()).toUpperCase();
            for (String marker : CustomerLimitConstants.NOT_FOUND_MARKERS) {
                if (text.contains(marker)) {
                    return Boolean.FALSE;
                }
            }
            log("FAILED", CustomerLimitConstants.CIF_LIMIT_TABLE + ">" + customer + " existence unknown " + e);
            return null;
        }
    }

    /** One INFO line: ALBE.CUSTOMER.LIMIT | CustomerLimitCreation: EVENT | details. Never throws. */
    private static void log(String event, String details) {
        try {
            logger.info(CustomerLimitConstants.LOG_TAG + NAME + " " + event + " | " + details);
        } catch (Exception e) {
            // logging must never stop the transaction
        }
    }
}
