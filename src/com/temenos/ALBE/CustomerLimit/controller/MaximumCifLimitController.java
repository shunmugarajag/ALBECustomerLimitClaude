package com.temenos.ALBE.CustomerLimit.controller;

import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.ALBE.CustomerLimit.validation.CustomerLimitRequestValidator;
import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.service.MaximumCifLimitService;
import com.temenos.ALBE.CustomerLimit.view.CustomerLimitOverride;
import com.temenos.api.TStructure;
import com.temenos.api.TValidationResponse;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;
import com.temenos.t24.api.complex.aa.activityhook.ArrangementContext;
import com.temenos.t24.api.hook.arrangement.ActivityLifecycle;
import com.temenos.t24.api.records.aaaccountdetails.AaAccountDetailsRecord;
import com.temenos.t24.api.records.aaarrangement.AaArrangementRecord;
import com.temenos.t24.api.records.aaarrangementactivity.AaArrangementActivityRecord;
import com.temenos.t24.api.records.aaproductcatalog.AaProductCatalogRecord;

/*-----------------------------------------------------------------------------
 * Attached as : ACTIVITY.API
 * Attached to : ACCOUNTS-CREDIT-ARRANGEMENT (validate record)
 * Description : Controller for the maximum overall CIF balance routine (HLSD 7.2.1 - 3).
 *               Reads the activity, builds the request, calls MaximumCifLimitService
 *               and lets the view (CustomerLimitOverride) raise the overrides.
 *               Credit activities only. No business rules and no DB access here.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version (was MaximumCifLimitValidator)
 *-----------------------------------------------------------------------------
 */
public class MaximumCifLimitController extends ActivityLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(Constants.LOGGER_NAME);
    private static final String LOG_PREFIX = "MaximumCifLimitController:";

    /** Input validation for credit activities: maximum CIF balance check, overrides on TXN.AMOUNT. */
    @Override
    public TValidationResponse validateRecord(AaAccountDetailsRecord accountDetailRecord,
            AaArrangementActivityRecord arrangementActivityRecord, ArrangementContext arrangementContext,
            AaArrangementRecord arrangementRecord, AaArrangementActivityRecord masterActivityRecord,
            TStructure productPropertyRecord, AaProductCatalogRecord productRecord, TStructure currRecord) {

        String activityStatus = "";
        long startTime = System.currentTimeMillis();
        try {
            activityStatus = arrangementContext.getActivityStatus();
            String activity = arrangementActivityRecord.getActivity().getValue();
            logger.info(LOG_PREFIX + "validateRecord START status=" + activityStatus + " activity=" + activity
                    + " arrangement=" + arrangementActivityRecord.getArrangement().getValue()
                    + " txnAmount=" + arrangementActivityRecord.getTxnAmount().getValue()
                    + " effectiveDate=" + arrangementActivityRecord.getEffectiveDate().getValue());

            if (!Constants.STATUS_UNAUTH.equalsIgnoreCase(activityStatus)) {
                logger.info(LOG_PREFIX + "validateRecord SKIP status=" + activityStatus + " (not UNAUTH)");
                return arrangementActivityRecord.getValidationResponse();
            }
            // HLSD: maximum balance check is for credit activities only.
            if (activity != null && activity.contains(Constants.ACTIVITY_DEBIT)) {
                logger.info(LOG_PREFIX + "validateRecord SKIP debit activity " + activity);
                return arrangementActivityRecord.getValidationResponse();
            }

            // Model
            CustomerLimitDTO request = new CustomerLimitDTO();
            request.setCustomerId(arrangementRecord.getCustomer().get(0).getCustomer().getValue());
            request.setTransactionId(arrangementActivityRecord.getArrangement().getValue());
            request.setTransactionCurrency(arrangementRecord.getCurrency().getValue());
            request.setCreditAmount(
                    ValidationUtil.toAmount(arrangementActivityRecord.getTxnAmount().getValue()));
            logger.info(LOG_PREFIX + "validateRecord request=" + request);
            if (!CustomerLimitRequestValidator.isValidForMaxCif(request)) {
                return arrangementActivityRecord.getValidationResponse();
            }

            // Service
            MaximumCifLimitService service = new MaximumCifLimitService(new CustomerLimitRepository(this));
            service.validate(request);

            // View
            int raised = CustomerLimitOverride.raiseFor(arrangementActivityRecord.getTxnAmount(), request);

            logger.info(LOG_PREFIX + "validateRecord END customer=" + request.getCustomerId() + " overridesRaised="
                    + raised + " elapsedMs=" + (System.currentTimeMillis() - startTime));

        } catch (Exception e) {
            logger.error(LOG_PREFIX + "validateRecord ERROR status=" + activityStatus + " " + e.getMessage());
        }
        return arrangementActivityRecord.getValidationResponse();
    }
}
