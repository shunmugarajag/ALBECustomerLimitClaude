package com.temenos.ALBE.CustomerLimit.controller;

import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.service.CustomerLimitCreationService;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.validation.CustomerLimitRequestValidator;
import com.temenos.api.TStructure;
import com.temenos.t24.api.complex.eb.servicehook.TransactionData;
import com.temenos.t24.api.complex.eb.templatehook.TransactionContext;
import com.temenos.t24.api.hook.system.RecordLifecycle;

/*-----------------------------------------------------------------------------
 * Attached as : After Unau routine
 * Attached to : VERSION CUSTOMER,ALBE.CORP
 * Description : Controller for the customer limit creation routine (HLSD 7.2.1 - 1).
 *               Customer input  : CustomerLimitCreationService.create
 *               Customer delete : CustomerLimitCreationService.delete
 *               No business rules and no DB access here.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version (was CustomerLimitCreation)
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitCreationController extends RecordLifecycle {
    private static final String CLASS_NAME = "CustomerLimitCreationController";

    /** After Unau: create the customer's limit record, or delete it when the customer is deleted. */
    @Override
    public void postUpdateRequest(String application, String currentRecordId, TStructure currentRecord,
            List<TransactionData> transactionData, List<TStructure> currentRecords,
            TransactionContext transactionContext) {

        long startTime = System.currentTimeMillis();
        try {
            String function = transactionContext == null ? null : transactionContext.getCurrentFunction();
            LogUtil.info(CLASS_NAME, "postUpdateRequest", "START", "application=" + application + " customer="
                    + currentRecordId + " function=" + function);

            if (!CustomerLimitRequestValidator.isValidCustomerId(currentRecordId)) {
                return;
            }

            CustomerLimitCreationService service =
                    new CustomerLimitCreationService(new CustomerLimitRepository(this));

            boolean done;
            if (Constants.FUNCTION_DELETE.equalsIgnoreCase(function)
                    || Constants.FUNCTION_DELETE_FULL.equalsIgnoreCase(function)) {
                LogUtil.info(CLASS_NAME, "postUpdateRequest", "ACTION", "action=DELETE");
                done = service.delete(currentRecordId);
            } else {
                LogUtil.info(CLASS_NAME, "postUpdateRequest", "ACTION", "action=CREATE");
                done = service.create(currentRecordId);
            }

            LogUtil.info(CLASS_NAME, "postUpdateRequest", "END", "customer=" + currentRecordId + " done=" + done
                    + " elapsedMs=" + (System.currentTimeMillis() - startTime));

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "postUpdateRequest", "ERROR", "customer=" + currentRecordId, e);
        }
    }
}
