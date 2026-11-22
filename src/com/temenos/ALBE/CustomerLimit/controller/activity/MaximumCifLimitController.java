package com.temenos.ALBE.CustomerLimit.controller.activity;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.service.activity.CustomerLimitEngine;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.ALBE.CustomerLimit.view.CustomerLimitOverride;
import com.temenos.api.TStructure;
import com.temenos.api.TValidationResponse;
import com.temenos.t24.api.complex.aa.activityhook.ArrangementContext;
import com.temenos.t24.api.hook.arrangement.ActivityLifecycle;
import com.temenos.t24.api.records.aaaccountdetails.AaAccountDetailsRecord;
import com.temenos.t24.api.records.aaarrangement.AaArrangementRecord;
import com.temenos.t24.api.records.aaarrangementactivity.AaArrangementActivityRecord;
import com.temenos.t24.api.records.aaproductcatalog.AaProductCatalogRecord;

/*-----------------------------------------------------------------------------
 * Attached as : ACTIVITY.API
 * Attached to : any activity - normally ACCOUNTS-CREDIT-ARRANGEMENT (validate record);
 *               checks counted credits only (ActivityRequestBuilder)
 * Description : Controller for the maximum overall CIF balance routine (HLSD 7.2.1 - 3).
 *               Reads the activity, builds the request, calls MaximumCifLimitService
 *               and lets the view (CustomerLimitOverride) raise the overrides.
 *               Credit activities only. No business rules and no DB access here.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version (was MaximumCifLimitValidator)
 * 21-Nov-2026   Development      Credit only by ACTIVITY.CLASS (was: name without DEBIT) (G9)
 * 21-Nov-2026   Development      Common activity mapping (ActivityRequestBuilder): ORIG.TXN.AMT, LCY amount
 * 22-Nov-2026   Development      Check through CustomerLimitEngine
 *-----------------------------------------------------------------------------
 */
public class MaximumCifLimitController extends ActivityLifecycle {
    private static final String CLASS_NAME = "MaximumCifLimitController";

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
            String activityClass = arrangementActivityRecord.getActivityClass().getValue();
            LogUtil.info(CLASS_NAME, "validateRecord", "START", "status=" + activityStatus + " activity=" + activity
                    + " class=" + activityClass
                    + " arrangement=" + arrangementActivityRecord.getArrangement().getValue()
                    + " txnAmount=" + arrangementActivityRecord.getTxnAmount().getValue()
                    + " effectiveDate=" + arrangementActivityRecord.getEffectiveDate().getValue());

            if (!Constants.STATUS_UNAUTH.equalsIgnoreCase(activityStatus)) {
                LogUtil.info(CLASS_NAME, "validateRecord", "SKIP", "status=" + activityStatus + " (not UNAUTH)");
                return arrangementActivityRecord.getValidationResponse();
            }
            // Common activity mapping; HLSD: maximum balance check is for credit activities only.
            CustomerLimitRepository repository = new CustomerLimitRepository(this);
            String aaaId = ValidationUtil.isBlank(arrangementContext.getArrangementActivityId())
                    ? arrangementActivityRecord.getArrangement().getValue()
                    : arrangementContext.getArrangementActivityId().trim();
            CustomerLimitDTO request = ActivityRequestBuilder.build(arrangementActivityRecord, arrangementRecord, aaaId,
                    repository);
            if (request == null || !request.isCredit()) {
                LogUtil.info(CLASS_NAME, "validateRecord", "SKIP", "activity=" + activity + " class=" + activityClass
                        + " (not a counted credit)");
                return arrangementActivityRecord.getValidationResponse();
            }
            LogUtil.info(CLASS_NAME, "validateRecord", "CALC", "request=" + request);

            // Engine (transaction info + customer limit info) + view
            CustomerLimitDTO result = new CustomerLimitEngine(repository).checkMaxCif(request);
            int raised = result == null ? 0
                    : CustomerLimitOverride.raiseFor(arrangementActivityRecord.getTxnAmount(), result);

            LogUtil.info(CLASS_NAME, "validateRecord", "END", "customer=" + request.getCustomerId()
                    + " overridesRaised="
                    + raised + " elapsedMs=" + (System.currentTimeMillis() - startTime));

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "validateRecord", "ERROR", "status=" + activityStatus, e);
        }
        return arrangementActivityRecord.getValidationResponse();
    }
}
