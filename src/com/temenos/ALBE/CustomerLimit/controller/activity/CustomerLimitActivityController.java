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
 * Attached as : ACTIVITY.API - ONE Post Routine per mapped activity (EB.API ALBE.CL.ACTIVITY.LIMIT,
 *               method validateRecord). Pre / Record / Validate / Pre Validate Rtn stay blank.
 * Attached to : any activity mapped CREDIT / DEBIT in EB.ALBE.GEN.PARAM CUSTOMER.LIMIT.ACTIVITY,
 *               e.g. ACCOUNTS-TRANSFERIN-ARRANGEMENT (ACCOUNT / CR.MOVEMENT) and
 *               ACCOUNTS-TRANSFEROUT-ARRANGEMENT (ACCOUNT / DR.MOVEMENT).
 * Description: Single routine for the whole customer limit flow (HLSD 7.2.1 - 2 and 3), D44:
 *
 *   status UNAUTH   INPUT          daily / monthly check, max CIF check (credit), overrides,
 *                                  then count the utilisation (not when blocked)
 *          DELETE   DELETE         take back (unauthorised input rejected)
 *          AUTH-REV REVERSAL       take back (authorised transaction reversed)
 *          AUTH     AUTHORISE      nothing (counted at input) - no read
 *          REVERSE  REVERSAL INPUT nothing (taken back when the reversal is authorised) - no read
 *
 *   Why validateRecord as a POST routine: for FT-created activities T24 calls only the Post
 *   Routine (DEV 25/26-Nov); only validateRecord hands overrides / errors back to T24 (R25
 *   invokers), and as a Post Routine its override reaches the FT screen (DEV 26-Nov).
 *   Prerequisite checked in DEV: validateRecord is also called for DELETE and AUTH-REV.
 *
 *   One call reads each record once and passes it along (no cache): activity mapping,
 *   EB.ALBE.CIF.LIMITS, daily + monthly EB.ALBE.UTILISED.LIMIT, EB.ALBE.TXN.DETAILS.LIMIT,
 *   and - credits with MAX.CIF filled only - the customer's accounts.
 *
 *   Blocking errors (EB.ERROR, on TXN.AMOUNT): activity not mapped, blank limit CCY, amount not
 *   convertible (D44), utilisation save failed (D37, setError - a thrown exception would only be
 *   logged by the validate invoker). Any other technical failure is logged and does not block.
 *
 *   DailyMonthlyLimitController / MaximumCifLimitController stay for rollback by configuration.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 26-Nov-2026   Development      Initial Version (D44)
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitActivityController extends ActivityLifecycle {
    private static final String CLASS_NAME = "CustomerLimitActivityController";
    private static final String HOOK = "validateRecord";

    @Override
    public TValidationResponse validateRecord(AaAccountDetailsRecord accountDetailRecord,
            AaArrangementActivityRecord arrangementActivityRecord, ArrangementContext arrangementContext,
            AaArrangementRecord arrangementRecord, AaArrangementActivityRecord masterActivityRecord,
            TStructure productPropertyRecord, AaProductCatalogRecord productRecord, TStructure currRecord) {

        String status = "";
        long startTime = System.currentTimeMillis();
        CustomerLimitRepository repository = null;
        try {
            status = ValidationUtil.nvl(arrangementContext.getActivityStatus()).trim().toUpperCase();
            ActivityRequestBuilder.logActivity(CLASS_NAME, HOOK, arrangementActivityRecord, arrangementContext,
                    arrangementRecord);
            String action = actionOf(status);
            String transaction = ValidationUtil.rootReference(arrangementActivityRecord.getTxnContractId().getValue())
                    + " " + ValidationUtil.nvl(arrangementActivityRecord.getActivity().getValue()) + " arrangement "
                    + ValidationUtil.nvl(arrangementActivityRecord.getArrangement().getValue());

            if (!Constants.STATUS_UNAUTH.equals(status) && !Constants.STATUS_DELETE.equals(status)
                    && !Constants.STATUS_AUTH_REV.equals(status)) {
                logAction(action, transaction, status, "nothing to do");
                return arrangementActivityRecord.getValidationResponse();
            }

            repository = new CustomerLimitRepository(this);
            String direction = ActivityRequestBuilder.getDirection(arrangementActivityRecord, repository);
            if (direction == null) {
                notMapped(arrangementActivityRecord, action, transaction, status);
                return arrangementActivityRecord.getValidationResponse();
            }
            CustomerLimitDTO request = ActivityRequestBuilder.build(arrangementActivityRecord, arrangementRecord,
                    getAaaId(arrangementContext, arrangementActivityRecord), direction, repository);
            if (request == null) {
                logAction(action, transaction, status, "not counted (no amount, or initiation type not counted)");
                return arrangementActivityRecord.getValidationResponse();
            }

            CustomerLimitEngine engine = new CustomerLimitEngine(repository);
            if (Constants.STATUS_UNAUTH.equals(status)) {
                logAction(action, request.describe(), status, "check + count");
                input(engine, request, arrangementActivityRecord, arrangementRecord);
            } else {
                logAction(action, request.describe(), status, "take back");
                engine.reverse(request, Constants.STATUS_AUTH_REV.equals(status));
                LogUtil.result(CLASS_NAME, HOOK, (Constants.STATUS_DELETE.equals(status) ? "TAKEN BACK (input rejected) "
                        : "TAKEN BACK (reversal) ") + request.describe()
                        + (request.getNotes().isEmpty() ? "" : " | " + String.join(" | ", request.getNotes())));
            }
            LogUtil.detail(CLASS_NAME, HOOK, "END", "status=" + status + " " + request.logKey() + " elapsedMs="
                    + (System.currentTimeMillis() - startTime));

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, HOOK, "ERROR", "status=" + status, e);
        }

        // D37: a failed utilisation / TXN.DETAILS save blocks the transaction.
        if (repository != null && repository.isSaveFailed()) {
            LogUtil.error(CLASS_NAME, HOOK, "BLOCK", "status=" + status + " save failed, raising "
                    + Constants.ERR_LIMIT_UPDATE_FAILED);
            CustomerLimitOverride.raiseError(arrangementActivityRecord.getTxnAmount(), Constants.ERR_LIMIT_UPDATE_FAILED);
        }
        return arrangementActivityRecord.getValidationResponse();
    }

    /** INPUT: check + overrides + count in one engine call; RESULT line with every number. */
    private void input(CustomerLimitEngine engine, CustomerLimitDTO request,
            AaArrangementActivityRecord arrangementActivityRecord, AaArrangementRecord arrangementRecord) {
        CustomerLimitDTO result = engine.processInput(request, getAccountNo(arrangementRecord),
                arrangementActivityRecord.getEffectiveDate().getValue());
        if (result == null) {
            LogUtil.result(CLASS_NAME, HOOK, "INPUT " + request.describe() + " | transaction info not valid"
                    + " -> not checked, not counted");
            return;
        }
        CustomerLimitOverride.raiseFor(arrangementActivityRecord, result);
        LogUtil.result(CLASS_NAME, HOOK, "INPUT " + result.describe()
                + (result.getNotes().isEmpty() ? "" : " | " + String.join(" | ", result.getNotes()))
                + " | raised: " + CustomerLimitOverride.ids(result));
    }

    /** Activity not in GEN.PARAM CUSTOMER.LIMIT.ACTIVITY: error at input (D41), logged otherwise. */
    private void notMapped(AaArrangementActivityRecord arrangementActivityRecord, String action, String transaction,
            String status) {
        String activity = ValidationUtil.nvl(arrangementActivityRecord.getActivity().getValue()).trim();
        if (Constants.STATUS_UNAUTH.equals(status)) {
            LogUtil.error(CLASS_NAME, HOOK, "NOT MAPPED", "activity=" + activity + " not in "
                    + Constants.GEN_PARAM_TABLE + ">" + Constants.GEN_PARAM_ACTIVITY_ID + ", raising "
                    + Constants.ERR_ACTIVITY_NOT_MAPPED);
            CustomerLimitOverride.raiseError(arrangementActivityRecord.getTxnAmount(),
                    Constants.ERR_ACTIVITY_NOT_MAPPED, activity);
            logAction(action, transaction, status, "ERROR " + Constants.ERR_ACTIVITY_NOT_MAPPED + " (input blocked)");
        } else {
            LogUtil.error(CLASS_NAME, HOOK, "NOT MAPPED", "status=" + status + " activity=" + activity + " not in "
                    + Constants.GEN_PARAM_ACTIVITY_ID + " - nothing taken back, check the utilisation");
        }
    }

    /** ACTION line: what happens to the transaction, in plain words (INPUT / DELETE / REVERSAL ...). */
    private void logAction(String action, String transaction, String status, String what) {
        LogUtil.info(CLASS_NAME, HOOK, Constants.LOG_EVENT_ACTION, action + " " + transaction + " (status " + status
                + ") -> " + what);
    }

    /** Plain-language action of an activity status. */
    public static String actionOf(String status) {
        switch (ValidationUtil.nvl(status)) {
            case Constants.STATUS_UNAUTH:
                return Constants.ACTION_INPUT;
            case Constants.STATUS_DELETE:
                return Constants.ACTION_DELETE;
            case Constants.STATUS_AUTH_REV:
                return Constants.ACTION_REVERSAL;
            case Constants.STATUS_AUTH:
                return Constants.ACTION_AUTHORISE;
            case Constants.STATUS_REVERSE:
                return Constants.ACTION_REVERSAL_INPUT;
            default:
                return status;
        }
    }

    /** AAA id of the current activity; arrangement id only if the context does not supply it. */
    private String getAaaId(ArrangementContext arrangementContext,
            AaArrangementActivityRecord arrangementActivityRecord) {
        String aaaId = arrangementContext.getArrangementActivityId();
        if (!ValidationUtil.isBlank(aaaId)) {
            return aaaId.trim();
        }
        LogUtil.error(CLASS_NAME, "getAaaId", "NOT FOUND", "AAA id not available, using arrangement id");
        return arrangementActivityRecord.getArrangement().getValue();
    }

    /** First linked application id of the arrangement (the account number). */
    private String getAccountNo(AaArrangementRecord arrangementRecord) {
        try {
            return arrangementRecord.getLinkedAppl().get(0).getLinkedApplId().getValue();
        } catch (Exception e) {
            LogUtil.info(CLASS_NAME, "getAccountNo", "NOT FOUND", "linked account not found ", e);
            return "";
        }
    }
}
