package com.temenos.ALBE.CustomerLimit.controller;

import java.util.List;

import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.service.CustomerLimitCreationService;
import com.temenos.api.TStructure;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger("L3API");
    private static final String LOG_PREFIX = "CustomerLimitCreationController:";

    // T24 function codes for delete of the unauthorised customer
    private static final String FUNCTION_DELETE = "D";
    private static final String FUNCTION_DELETE_FULL = "DELETE";

    /** After Unau: create the customer's limit record, or delete it when the customer is deleted. */
    @Override
    public void postUpdateRequest(String application, String currentRecordId, TStructure currentRecord,
            List<TransactionData> transactionData, List<TStructure> currentRecords,
            TransactionContext transactionContext) {

        long startTime = System.currentTimeMillis();
        try {
            String function = transactionContext == null ? null : transactionContext.getCurrentFunction();
            logger.info(LOG_PREFIX + "postUpdateRequest START application=" + application + " customer="
                    + currentRecordId + " function=" + function);

            CustomerLimitCreationService service =
                    new CustomerLimitCreationService(new CustomerLimitRepository(this));

            boolean done;
            if (FUNCTION_DELETE.equalsIgnoreCase(function) || FUNCTION_DELETE_FULL.equalsIgnoreCase(function)) {
                logger.info(LOG_PREFIX + "postUpdateRequest action=DELETE");
                done = service.delete(currentRecordId);
            } else {
                logger.info(LOG_PREFIX + "postUpdateRequest action=CREATE");
                done = service.create(currentRecordId);
            }

            logger.info(LOG_PREFIX + "postUpdateRequest END customer=" + currentRecordId + " done=" + done
                    + " elapsedMs=" + (System.currentTimeMillis() - startTime));

        } catch (Exception e) {
            logger.error(LOG_PREFIX + "postUpdateRequest ERROR customer=" + currentRecordId + " " + e.getMessage());
        }
    }
}
