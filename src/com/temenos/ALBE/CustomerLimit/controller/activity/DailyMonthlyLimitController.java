package com.temenos.ALBE.CustomerLimit.controller.activity;

import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.service.activity.CustomerLimitEngine;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.ALBE.CustomerLimit.view.CustomerLimitOverride;
import com.temenos.api.TStructure;
import com.temenos.api.exceptions.T24CoreException;
import com.temenos.api.TValidationResponse;
import com.temenos.t24.api.complex.aa.activityhook.ArrangementContext;
import com.temenos.t24.api.complex.aa.activityhook.TransactionData;
import com.temenos.t24.api.hook.arrangement.ActivityLifecycle;
import com.temenos.t24.api.records.aaaccountdetails.AaAccountDetailsRecord;
import com.temenos.t24.api.records.aaarrangement.AaArrangementRecord;
import com.temenos.t24.api.records.aaarrangementactivity.AaArrangementActivityRecord;
import com.temenos.t24.api.records.aaproductcatalog.AaProductCatalogRecord;

/*-----------------------------------------------------------------------------
 * Attached as : ACTIVITY.API
 * Attached to : any activity - normally ACCOUNTS-CREDIT-ARRANGEMENT / ACCOUNTS-DEBIT-ARRANGEMENT.
 *               Which activities are counted, the amount and the date: ActivityRequestBuilder
 *               (credit / debit by the ACTIVITY.CLASS action; others skipped).
 * Description: Controller for the daily and monthly limit routine (HLSD 7.2.1 - 2).
 *               Reads the activity, builds the request, calls DailyMonthlyLimitService
 *               and lets the view (CustomerLimitOverride) raise the overrides.
 *               No business rules and no DB access here.
 *
 *   validateRecord      UNAUTH   : service.validate, then overrides on TXN.AMOUNT
 *   postCoreTableUpdate UNAUTH   : service.countAtInput  (utilisation counted at input)
 *   Counted date = the activity's ORG.SYSTEM.DATE (else T24 TODAY), see ActivityRequestBuilder.
 *                       DELETE   : service.reverse       (unauthorised input rejected)
 *                       AUTH     : nothing               (already counted)
 *                       AUTH-REV : service.reverse       (authorised transaction reversed)
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version (was DailyMonthlyLimitValidator)
 * 21-Nov-2026   Development      Counted date in activity REMARKS (no table field)
 * 21-Nov-2026   Development      Credit / debit by ACTIVITY.CLASS instead of activity name (G9)
 * 21-Nov-2026   Development      Common for all activities (ActivityRequestBuilder): class action,
 *                                ORIG.TXN.AMT, TXN.AMOUNT.LCY, T24 date instead of server clock (G1)
 * 22-Nov-2026   Development      Check / count / reverse through CustomerLimitEngine
 * 23-Nov-2026   Development      Block (EB-ALBE.LIMIT.UPDATE.FAILED) when a utilisation save fails (D37)
 * 25-Nov-2026   Development      Credit / debit from GEN.PARAM CUSTOMER.LIMIT.ACTIVITY; not mapped -> error (D41)
 * 25-Nov-2026   Development      ACTIVITY line per hook; RESULT lines (check numbers, overrides / errors, counted / taken back)
 * 26-Nov-2026   Development      defaultFieldValues removed (D42): date from the activity ORG.SYSTEM.DATE, no REMARKS tag
 *-----------------------------------------------------------------------------
 */
public class DailyMonthlyLimitController extends ActivityLifecycle {
    private static final String CLASS_NAME = "DailyMonthlyLimitController";

    /** Input validation: daily and monthly limit check, overrides on TXN.AMOUNT. */
    @Override
    public TValidationResponse validateRecord(AaAccountDetailsRecord accountDetailRecord,
            AaArrangementActivityRecord arrangementActivityRecord, ArrangementContext arrangementContext,
            AaArrangementRecord arrangementRecord, AaArrangementActivityRecord masterActivityRecord,
            TStructure productPropertyRecord, AaProductCatalogRecord productRecord, TStructure currRecord) {

        String activityStatus = "";
        long startTime = System.currentTimeMillis();
        try {
            activityStatus = arrangementContext.getActivityStatus();
            LogUtil.detail(CLASS_NAME, "validateRecord", "START", "status=" + activityStatus);
            ActivityRequestBuilder.logActivity(CLASS_NAME, "validateRecord", arrangementActivityRecord, arrangementContext,
                    arrangementRecord);

            if (!Constants.STATUS_UNAUTH.equalsIgnoreCase(activityStatus)) {
                LogUtil.detail(CLASS_NAME, "validateRecord", "SKIP", "status=" + activityStatus + " (not UNAUTH)");
                return arrangementActivityRecord.getValidationResponse();
            }

            CustomerLimitRepository repository = new CustomerLimitRepository(this);
            // D41: the activity must be mapped to CREDIT / DEBIT in GEN.PARAM CUSTOMER.LIMIT.ACTIVITY.
            if (ActivityRequestBuilder.getDirection(arrangementActivityRecord, repository) == null) {
                raiseNotMapped(arrangementActivityRecord);
                return arrangementActivityRecord.getValidationResponse();
            }
            CustomerLimitDTO request = ActivityRequestBuilder.build(arrangementActivityRecord, arrangementRecord,
                    getAaaId(arrangementContext, arrangementActivityRecord), repository);
            if (request == null) {
                LogUtil.result(CLASS_NAME, "validateRecord", "CHECK " + arrangementActivityRecord.getActivity().getValue()
                        + " arrangement " + arrangementActivityRecord.getArrangement().getValue()
                        + ": not counted (no amount, or initiation type not counted) -> no check");
                return arrangementActivityRecord.getValidationResponse();
            }

            // Engine (transaction info + customer limit info) + view
            CustomerLimitDTO result = new CustomerLimitEngine(repository).checkDailyMonthly(request);
            int raised = result == null ? 0
                    : CustomerLimitOverride.raiseFor(arrangementActivityRecord.getTxnAmount(), result);
            logCheckResult("validateRecord", "CHECK daily/monthly ", request, result);

            LogUtil.detail(CLASS_NAME, "validateRecord", "END", request.logKey() + " overridesRaised=" + raised
                    + " elapsedMs="
                    + (System.currentTimeMillis() - startTime));

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "validateRecord", "ERROR", "status=" + activityStatus, e);
        }
        return arrangementActivityRecord.getValidationResponse();
    }

    /** Utilisation: UNAUTH counts, DELETE and AUTH-REV reverse, AUTH changes nothing. */
    @Override
    public void postCoreTableUpdate(AaAccountDetailsRecord accountDetailRecord,
            AaArrangementActivityRecord arrangementActivityRecord, ArrangementContext arrangementContext,
            AaArrangementRecord arrangementRecord, AaArrangementActivityRecord masterActivityRecord,
            TStructure productPropertyRecord, AaProductCatalogRecord productRecord, TStructure currRecord,
            List<TransactionData> transactionData, List<TStructure> transactionRecord) {

        String activityStatus = "";
        long startTime = System.currentTimeMillis();
        CustomerLimitRepository repository = null;
        try {
            activityStatus = arrangementContext.getActivityStatus();
            LogUtil.detail(CLASS_NAME, "postCoreTableUpdate", "START", "status=" + activityStatus);
            ActivityRequestBuilder.logActivity(CLASS_NAME, "postCoreTableUpdate", arrangementActivityRecord,
                    arrangementContext, arrangementRecord);

            String status = activityStatus == null ? "" : activityStatus.trim().toUpperCase();
            if (!isUtilisationStatus(status)) {
                LogUtil.detail(CLASS_NAME, "postCoreTableUpdate", "SKIP", "status=" + status
                        + " (only UNAUTH / DELETE / AUTH-REV change utilisation; AUTH was counted at input)");
                LogUtil.result(CLASS_NAME, "postCoreTableUpdate", "status " + status + " "
                        + arrangementActivityRecord.getActivity().getValue() + " arrangement "
                        + arrangementActivityRecord.getArrangement().getValue()
                        + ": nothing to change (the transaction was counted at input)");
                return;
            }

            repository = new CustomerLimitRepository(this);
            CustomerLimitDTO request = ActivityRequestBuilder.build(arrangementActivityRecord, arrangementRecord,
                    getAaaId(arrangementContext, arrangementActivityRecord), repository);
            if (request == null) {
                if (!Constants.STATUS_UNAUTH.equals(status)
                        && ActivityRequestBuilder.getDirection(arrangementActivityRecord, repository) == null) {
                    LogUtil.error(CLASS_NAME, "postCoreTableUpdate", "NOT MAPPED", "status=" + status + " activity="
                            + arrangementActivityRecord.getActivity().getValue() + " not in "
                            + Constants.GEN_PARAM_ACTIVITY_ID + " - nothing taken back, check the utilisation");
                } else {
                    LogUtil.result(CLASS_NAME, "postCoreTableUpdate", "status " + status + " "
                            + arrangementActivityRecord.getActivity().getValue() + " arrangement "
                            + arrangementActivityRecord.getArrangement().getValue()
                            + ": not counted (no amount, or initiation type not counted) -> nothing to change");
                }
                return;
            }

            CustomerLimitEngine engine = new CustomerLimitEngine(repository);
            switch (status) {
                case Constants.STATUS_UNAUTH:   // transaction initiated: count it
                    LogUtil.detail(CLASS_NAME, "postCoreTableUpdate", "ACTION", "COUNT " + request.logKey());
                    engine.count(request, getAccountNo(arrangementRecord),
                            arrangementActivityRecord.getEffectiveDate().getValue());
                    break;
                case Constants.STATUS_DELETE:   // unauthorised input rejected: take it back
                    LogUtil.detail(CLASS_NAME, "postCoreTableUpdate", "ACTION", "REVERSE rejected input "
                            + request.logKey());
                    engine.reverse(request, false);
                    break;
                default:                        // AUTH-REV, committed transaction reversed: take it back
                    LogUtil.detail(CLASS_NAME, "postCoreTableUpdate", "ACTION", "REVERSE reversal " + request.logKey());
                    engine.reverse(request, true);
                    break;
            }

            String action = Constants.STATUS_UNAUTH.equals(status) ? "COUNTED "
                    : Constants.STATUS_DELETE.equals(status) ? "TAKEN BACK (input rejected) "
                    : "TAKEN BACK (reversal) ";
            LogUtil.result(CLASS_NAME, "postCoreTableUpdate", action + request.describe()
                    + (request.getNotes().isEmpty() ? "" : " | " + String.join(" | ", request.getNotes()))
                    + (repository.isSaveFailed() ? " | SAVE FAILED -> ERROR " + Constants.ERR_LIMIT_UPDATE_FAILED
                            + " (transaction cancelled)" : ""));
            LogUtil.detail(CLASS_NAME, "postCoreTableUpdate", "END", "status=" + status + " " + request.logKey()
                    + " elapsedMs=" + (System.currentTimeMillis() - startTime));

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "postCoreTableUpdate", "ERROR", "status=" + activityStatus, e);
        }

        // D37: a failed utilisation / TXN.DETAILS save cancels the transaction, so the
        // customer's usage never silently misses it.
        if (repository != null && repository.isSaveFailed()) {
            LogUtil.error(CLASS_NAME, "postCoreTableUpdate", "BLOCK", "status=" + activityStatus
                    + " save failed, raising " + Constants.ERR_LIMIT_UPDATE_FAILED);
            throw new T24CoreException("", Constants.ERR_LIMIT_UPDATE_FAILED);
        }
    }

    /** RESULT line of a check: transaction, every check with its numbers, overrides / errors raised. */
    private void logCheckResult(String hook, String prefix, CustomerLimitDTO request, CustomerLimitDTO result) {
        if (result == null) {
            LogUtil.result(CLASS_NAME, hook, prefix + request.describe() + " | transaction info not valid -> no check");
            return;
        }
        LogUtil.result(CLASS_NAME, hook, prefix + result.describe()
                + (result.getNotes().isEmpty() ? "" : " | " + String.join(" | ", result.getNotes()))
                + " | raised: " + CustomerLimitOverride.ids(result));
    }

    /** D41: activity not in GEN.PARAM CUSTOMER.LIMIT.ACTIVITY - error on TXN.AMOUNT (blocks the input). */
    private void raiseNotMapped(AaArrangementActivityRecord arrangementActivityRecord) {
        String activity = ValidationUtil.nvl(arrangementActivityRecord.getActivity().getValue()).trim();
        LogUtil.error(CLASS_NAME, "validateRecord", "NOT MAPPED", "activity=" + activity + " not in "
                + Constants.GEN_PARAM_TABLE + ">" + Constants.GEN_PARAM_ACTIVITY_ID + ", raising "
                + Constants.ERR_ACTIVITY_NOT_MAPPED);
        CustomerLimitOverride.raiseError(arrangementActivityRecord.getTxnAmount(), Constants.ERR_ACTIVITY_NOT_MAPPED,
                activity);
    }

    /** UNAUTH, DELETE and AUTH-REV change utilisation; AUTH and anything else do not. */
    private boolean isUtilisationStatus(String status) {
        return Constants.STATUS_UNAUTH.equals(status) || Constants.STATUS_DELETE.equals(status)
                || Constants.STATUS_AUTH_REV.equals(status);
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
            String accountNo = arrangementRecord.getLinkedAppl().get(0).getLinkedApplId().getValue();
            LogUtil.detail(CLASS_NAME, "getAccountNo", "FOUND", "account=" + accountNo);
            return accountNo;
        } catch (Exception e) {
            LogUtil.info(CLASS_NAME, "getAccountNo", "NOT FOUND", "linked account not found ", e);
            return "";
        }
    }
}
