package com.temenos.ALBE.CustomerLimit.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.service.DailyMonthlyLimitService;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.ALBE.CustomerLimit.validation.CustomerLimitRequestValidator;
import com.temenos.ALBE.CustomerLimit.view.CustomerLimitOverride;
import com.temenos.api.TStructure;
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
 * Attached to : ACCOUNTS-CREDIT-ARRANGEMENT / ACCOUNTS-DEBIT-ARRANGEMENT
 * Description : Controller for the daily and monthly limit routine (HLSD 7.2.1 - 2).
 *               Reads the activity, builds the request, calls DailyMonthlyLimitService
 *               and lets the view (CustomerLimitOverride) raise the overrides.
 *               No business rules and no DB access here.
 *
 *   defaultFieldValues  UNAUTH   : keeps the server date in the activity REMARKS as
 *                                  ALBE.LIMIT.DATE=YYYYMMDD (first input only), so every later
 *                                  step - also a reversal on another day - uses the date the
 *                                  amount was counted on and rebuilds the
 *                                  EB.ALBE.TXN.DETAILS.LIMIT id <customer>-<date>-<AAA>
 *   validateRecord      UNAUTH   : service.validate, then overrides on TXN.AMOUNT
 *   postCoreTableUpdate UNAUTH   : service.countAtInput  (utilisation counted at input)
 *                       DELETE   : service.reverse       (unauthorised input rejected)
 *                       AUTH     : nothing               (already counted)
 *                       AUTH-REV : service.reverse       (authorised transaction reversed)
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version (was DailyMonthlyLimitValidator)
 * 21-Nov-2026   Development      Counted date in activity REMARKS (no table field)
 *-----------------------------------------------------------------------------
 */
public class DailyMonthlyLimitController extends ActivityLifecycle {
    private static final String CLASS_NAME = "DailyMonthlyLimitController";

    /**
     * Input defaulting: keeps the counted (server) date in the activity REMARKS the first time
     * the activity is input. Existing REMARKS text is kept; a re-input keeps the original date.
     */
    @Override
    public void defaultFieldValues(AaAccountDetailsRecord accountDetailRecord,
            AaArrangementActivityRecord arrangementActivityRecord, ArrangementContext arrangementContext,
            AaArrangementRecord arrangementRecord, AaArrangementActivityRecord masterActivityRecord,
            TStructure productPropertyRecord, AaProductCatalogRecord productRecord, TStructure currRecord) {

        String activityStatus = "";
        try {
            activityStatus = arrangementContext.getActivityStatus();
            LogUtil.info(CLASS_NAME, "defaultFieldValues", "START", "status=" + activityStatus);
            if (!Constants.STATUS_UNAUTH.equalsIgnoreCase(activityStatus)) {
                LogUtil.info(CLASS_NAME, "defaultFieldValues", "SKIP", "status=" + activityStatus + " (not UNAUTH)");
                return;
            }

            String remarks = ValidationUtil.nvl(arrangementActivityRecord.getRemarks().getValue()).trim();
            LocalDate existing = getCountedDate(remarks);
            if (existing != null) {
                LogUtil.info(CLASS_NAME, "defaultFieldValues", "SKIP", "re-input, counted date kept " + existing);
                return;
            }

            String tag = Constants.REMARKS_DATE_TAG + LocalDate.now().format(Constants.DAILY_ID_FORMAT);
            String newRemarks = remarks.isEmpty() ? tag : remarks + Constants.REMARKS_SEPARATOR + tag;
            arrangementActivityRecord.setRemarks(newRemarks);
            LogUtil.info(CLASS_NAME, "defaultFieldValues", "UPDATED", "REMARKS '" + remarks + "' -> '" + newRemarks
                    + "'");

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "defaultFieldValues", "ERROR", "status=" + activityStatus, e);
        }
    }

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
            LogUtil.info(CLASS_NAME, "validateRecord", "START", "status=" + activityStatus);
            logActivity("validateRecord", arrangementActivityRecord, arrangementContext, arrangementRecord);

            if (!Constants.STATUS_UNAUTH.equalsIgnoreCase(activityStatus)) {
                LogUtil.info(CLASS_NAME, "validateRecord", "SKIP", "status=" + activityStatus + " (not UNAUTH)");
                return arrangementActivityRecord.getValidationResponse();
            }

            CustomerLimitDTO request = buildRequest(arrangementActivityRecord, arrangementRecord, arrangementContext);
            if (request == null || !CustomerLimitRequestValidator.isValidForDailyMonthly(request)) {
                return arrangementActivityRecord.getValidationResponse();
            }

            // Model + service
            DailyMonthlyLimitService service = new DailyMonthlyLimitService(new CustomerLimitRepository(this));
            service.validate(request);

            // View
            int raised = CustomerLimitOverride.raiseFor(arrangementActivityRecord.getTxnAmount(), request);

            LogUtil.info(CLASS_NAME, "validateRecord", "END", request.logKey() + " overridesRaised=" + raised
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
        try {
            activityStatus = arrangementContext.getActivityStatus();
            LogUtil.info(CLASS_NAME, "postCoreTableUpdate", "START", "status=" + activityStatus);
            logActivity("postCoreTableUpdate", arrangementActivityRecord, arrangementContext, arrangementRecord);

            String status = activityStatus == null ? "" : activityStatus.trim().toUpperCase();
            if (!isUtilisationStatus(status)) {
                LogUtil.info(CLASS_NAME, "postCoreTableUpdate", "SKIP", "status=" + status
                        + " (only UNAUTH / DELETE / AUTH-REV change utilisation; AUTH was counted at input)");
                return;
            }

            CustomerLimitDTO request = buildRequest(arrangementActivityRecord, arrangementRecord, arrangementContext);
            if (request == null || !CustomerLimitRequestValidator.isValidForDailyMonthly(request)) {
                return;
            }

            DailyMonthlyLimitService service = new DailyMonthlyLimitService(new CustomerLimitRepository(this));
            switch (status) {
                case Constants.STATUS_UNAUTH:   // transaction initiated: count it
                    LogUtil.info(CLASS_NAME, "postCoreTableUpdate", "ACTION", "COUNT " + request.logKey());
                    service.countAtInput(request, getAccountNo(arrangementRecord),
                            arrangementActivityRecord.getEffectiveDate().getValue());
                    break;
                case Constants.STATUS_DELETE:   // unauthorised input rejected: take it back
                    LogUtil.info(CLASS_NAME, "postCoreTableUpdate", "ACTION", "REVERSE rejected input "
                            + request.logKey());
                    service.reverse(request, false);
                    break;
                default:                        // AUTH-REV, committed transaction reversed: take it back
                    LogUtil.info(CLASS_NAME, "postCoreTableUpdate", "ACTION", "REVERSE reversal " + request.logKey());
                    service.reverse(request, true);
                    break;
            }

            LogUtil.info(CLASS_NAME, "postCoreTableUpdate", "END", "status=" + status + " " + request.logKey()
                    + " elapsedMs=" + (System.currentTimeMillis() - startTime));

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "postCoreTableUpdate", "ERROR", "status=" + activityStatus, e);
        }
    }

    /** UNAUTH, DELETE and AUTH-REV change utilisation; AUTH and anything else do not. */
    private boolean isUtilisationStatus(String status) {
        return Constants.STATUS_UNAUTH.equals(status) || Constants.STATUS_DELETE.equals(status)
                || Constants.STATUS_AUTH_REV.equals(status);
    }

    /**
     * Builds the request (model) from the activity: customer, AAA id, server date and the
     * credit or debit amount. Returns null if the activity is neither credit nor debit.
     */
    private CustomerLimitDTO buildRequest(AaArrangementActivityRecord arrangementActivityRecord,
            AaArrangementRecord arrangementRecord, ArrangementContext arrangementContext) {

        String activity = arrangementActivityRecord.getActivity().getValue();
        // Amount in the transaction (arrangement) currency; the service converts it to the limit currency.
        BigDecimal amount = ValidationUtil.toAmount(arrangementActivityRecord.getTxnAmount().getValue());

        CustomerLimitDTO request = new CustomerLimitDTO();
        request.setCustomerId(arrangementRecord.getCustomer().get(0).getCustomer().getValue());
        request.setTransactionId(getAaaId(arrangementContext, arrangementActivityRecord));
        // Counted date from the activity REMARKS (written at input), so a rejection or a reversal on
        // a later day uses the original day and month; today's server date if the tag is not there.
        LocalDate countedDate = getCountedDate(arrangementActivityRecord.getRemarks().getValue());
        request.setTransactionDate(countedDate != null ? countedDate : LocalDate.now());
        request.setTransactionCurrency(arrangementRecord.getCurrency().getValue());

        if (activity != null && activity.contains(Constants.ACTIVITY_CREDIT)) {
            request.setCreditAmount(amount);
        } else if (activity != null && activity.contains(Constants.ACTIVITY_DEBIT)) {
            request.setDebitAmount(amount);
        } else {
            LogUtil.info(CLASS_NAME, "buildRequest", "SKIP", "activity=" + activity + " is neither credit nor debit");
            return null;
        }
        LogUtil.info(CLASS_NAME, "buildRequest", "CALC", "activity=" + activity + " request=" + request);
        return request;
    }

    /** Counted date from the REMARKS tag ALBE.LIMIT.DATE=YYYYMMDD; null if not there or not a valid date. */
    private LocalDate getCountedDate(String remarks) {
        String text = ValidationUtil.nvl(remarks);
        int pos = text.indexOf(Constants.REMARKS_DATE_TAG);
        if (pos < 0) {
            LogUtil.info(CLASS_NAME, "getCountedDate", "NOT FOUND", "no " + Constants.REMARKS_DATE_TAG
                    + " in REMARKS");
            return null;
        }
        int start = pos + Constants.REMARKS_DATE_TAG.length();
        int end = Math.min(start + Constants.REMARKS_DATE_LENGTH, text.length());
        LocalDate date = ValidationUtil.toDate(text.substring(start, end));
        LogUtil.info(CLASS_NAME, "getCountedDate", date == null ? "INVALID" : "FOUND", "countedDate=" + date);
        return date;
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
            LogUtil.info(CLASS_NAME, "getAccountNo", "FOUND", "account=" + accountNo);
            return accountNo;
        } catch (Exception e) {
            LogUtil.info(CLASS_NAME, "getAccountNo", "NOT FOUND", "linked account not found ", e);
            return "";
        }
    }

    /** Logs the raw activity fields received from T24, for tracing. */
    private void logActivity(String hook, AaArrangementActivityRecord arrangementActivityRecord,
            ArrangementContext arrangementContext, AaArrangementRecord arrangementRecord) {
        try {
            LogUtil.info(CLASS_NAME, hook, "CALC", "activity=" + arrangementActivityRecord.getActivity().getValue()
                    + " arrangement=" + arrangementActivityRecord.getArrangement().getValue()
                    + " aaa=" + arrangementContext.getArrangementActivityId()
                    + " customer=" + arrangementRecord.getCustomer().get(0).getCustomer().getValue()
                    + " txnAmount=" + arrangementActivityRecord.getTxnAmount().getValue()
                    + " effectiveDate=" + arrangementActivityRecord.getEffectiveDate().getValue());
        } catch (Exception e) {
            LogUtil.info(CLASS_NAME, hook, "SKIP", "unable to log activity fields ", e);
        }
    }
}
