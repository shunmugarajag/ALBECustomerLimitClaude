package com.temenos.ALBE.CustomerLimit.controller.customer;

import java.util.List;

import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.service.customer.CustomerLimitCreationService;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.validation.CustomerLimitRequestValidator;
import com.temenos.api.TStructure;
import com.temenos.t24.api.complex.eb.servicehook.TransactionData;
import com.temenos.t24.api.complex.eb.templatehook.TransactionContext;
import com.temenos.t24.api.hook.system.RecordLifecycle;

/*-----------------------------------------------------------------------------
 * Attached as : Auth routine
 * Attached to : VERSION CUSTOMER,ALBE.CORP
 * Description : Controller for the customer limit creation routine (HLSD 7.2.1 - 1).
 *               Customer authorised : CustomerLimitCreationService.create
 *               The limit record is created only once the customer is authorised, so an
 *               unauthorised customer that is deleted never had one: no delete flow needed.
 *               (HLSD 7.2.1 says After Unau + delete on customer delete; changed to Auth.)
 *               No business rules and no DB access here.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version (was CustomerLimitCreation)
 * 20-Nov-2026   Development      Moved from After Unau to Auth routine; delete flow removed
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitCreationController extends RecordLifecycle {
    private static final String CLASS_NAME = "CustomerLimitCreationController";

    /** Auth: create the customer's limit record from the generic limits (if it does not exist yet). */
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
            boolean created = service.create(currentRecordId);

            LogUtil.info(CLASS_NAME, "postUpdateRequest", "END", "customer=" + currentRecordId + " created=" + created
                    + " elapsedMs=" + (System.currentTimeMillis() - startTime));

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "postUpdateRequest", "ERROR", "customer=" + currentRecordId, e);
        }
    }
}
