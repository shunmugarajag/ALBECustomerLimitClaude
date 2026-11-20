package com.temenos.ALBE.CustomerLimit.controller;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.service.CustomerLimitUtilizationUpdater;
import com.temenos.ALBE.CustomerLimit.service.DailyMonthlyLimitService;
import com.temenos.ALBE.CustomerLimit.view.CustomerLimitOverride;
import com.temenos.api.TStructure;
import com.temenos.api.TValidationResponse;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;
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
 *   validateRecord      UNAUTH   : service.validate, then overrides on TXN.AMOUNT
 *   postCoreTableUpdate UNAUTH   : service.countAtInput  (utilisation counted at input)
 *                       DELETE   : service.reverse       (unauthorised input rejected)
 *                       AUTH     : nothing               (already counted)
 *                       AUTH-REV : service.reverse       (authorised transaction reversed)
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version (was DailyMonthlyLimitValidator)
 *-----------------------------------------------------------------------------
 */
public class DailyMonthlyLimitController extends ActivityLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(Constants.LOGGER_NAME);
    private static final String LOG_PREFIX = "DailyMonthlyLimitController:";

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
            logger.info(LOG_PREFIX + "validateRecord START status=" + activityStatus);
            logActivity("validateRecord", arrangementActivityRecord, arrangementContext, arrangementRecord);

            if (!Constants.STATUS_UNAUTH.equalsIgnoreCase(activityStatus)) {
                logger.info(LOG_PREFIX + "validateRecord SKIP status=" + activityStatus + " (not UNAUTH)");
                return arrangementActivityRecord.getValidationResponse();
            }

            CustomerLimitDTO request = buildRequest(arrangementActivityRecord, arrangementRecord, arrangementContext);
            if (request == null) {
                return arrangementActivityRecord.getValidationResponse();
            }

            // Model + service
            DailyMonthlyLimitService service = new DailyMonthlyLimitService(new CustomerLimitRepository(this));
            service.validate(request);

            // View
            int raised = CustomerLimitOverride.raiseFor(arrangementActivityRecord.getTxnAmount(), request);

            logger.info(LOG_PREFIX + "validateRecord END customer=" + request.getCustomerId() + " aaa="
                    + request.getTransactionId() + " overridesRaised=" + raised + " elapsedMs="
                    + (System.currentTimeMillis() - startTime));

        } catch (Exception e) {
            logger.error(LOG_PREFIX + "validateRecord ERROR status=" + activityStatus + " " + e.getMessage());
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
            logger.info(LOG_PREFIX + "postCoreTableUpdate START status=" + activityStatus);
            logActivity("postCoreTableUpdate", arrangementActivityRecord, arrangementContext, arrangementRecord);

            boolean isInput = Constants.STATUS_UNAUTH.equalsIgnoreCase(activityStatus);
            boolean isDelete = Constants.STATUS_DELETE.equalsIgnoreCase(activityStatus);
            boolean isReversal = Constants.STATUS_AUTH_REV.equalsIgnoreCase(activityStatus);

            if (Constants.STATUS_AUTH.equalsIgnoreCase(activityStatus)) {
                logger.info(LOG_PREFIX + "postCoreTableUpdate SKIP AUTH (utilisation already counted at input)");
                return;
            }
            if (!isInput && !isDelete && !isReversal) {
                logger.info(LOG_PREFIX + "postCoreTableUpdate SKIP status=" + activityStatus
                        + " (only UNAUTH / DELETE / AUTH-REV change utilisation)");
                return;
            }

            CustomerLimitDTO request = buildRequest(arrangementActivityRecord, arrangementRecord, arrangementContext);
            if (request == null) {
                return;
            }

            DailyMonthlyLimitService service = new DailyMonthlyLimitService(new CustomerLimitRepository(this));
            if (isInput) {
                logger.info(LOG_PREFIX + "postCoreTableUpdate action=COUNT");
                service.countAtInput(request, getAccountNo(arrangementRecord),
                        arrangementActivityRecord.getEffectiveDate().getValue());
            } else if (isDelete) {
                logger.info(LOG_PREFIX + "postCoreTableUpdate action=REVERSE (rejected input)");
                service.reverse(request, false);
            } else {
                logger.info(LOG_PREFIX + "postCoreTableUpdate action=REVERSE (reversal)");
                service.reverse(request, true);
            }

            logger.info(LOG_PREFIX + "postCoreTableUpdate END status=" + activityStatus + " customer="
                    + request.getCustomerId() + " aaa=" + request.getTransactionId() + " elapsedMs="
                    + (System.currentTimeMillis() - startTime));

        } catch (Exception e) {
            logger.error(LOG_PREFIX + "postCoreTableUpdate ERROR status=" + activityStatus + " " + e.getMessage());
        }
    }

    /**
     * Builds the request (model) from the activity: customer, AAA id, server date and the
     * credit or debit amount. Returns null if the activity is neither credit nor debit.
     */
    private CustomerLimitDTO buildRequest(AaArrangementActivityRecord arrangementActivityRecord,
            AaArrangementRecord arrangementRecord, ArrangementContext arrangementContext) {

        String activity = arrangementActivityRecord.getActivity().getValue();
        BigDecimal amount = CustomerLimitUtilizationUpdater
                .toAmount(arrangementActivityRecord.getTxnAmount().getValue()).multiply(Constants.EXCHANGE_RATE);

        CustomerLimitDTO request = new CustomerLimitDTO();
        request.setCustomerId(arrangementRecord.getCustomer().get(0).getCustomer().getValue());
        request.setTransactionId(getAaaId(arrangementContext, arrangementActivityRecord));
        request.setTransactionDate(LocalDate.now()); // server date, as agreed

        if (activity != null && activity.contains(Constants.ACTIVITY_CREDIT)) {
            request.setCreditAmount(amount);
        } else if (activity != null && activity.contains(Constants.ACTIVITY_DEBIT)) {
            request.setDebitAmount(amount);
        } else {
            logger.info(LOG_PREFIX + "buildRequest SKIP activity=" + activity + " is neither credit nor debit");
            return null;
        }
        logger.info(LOG_PREFIX + "buildRequest activity=" + activity + " request=" + request);
        return request;
    }

    /** AAA id of the current activity; arrangement id only if the context does not supply it. */
    private String getAaaId(ArrangementContext arrangementContext,
            AaArrangementActivityRecord arrangementActivityRecord) {
        String aaaId = arrangementContext.getArrangementActivityId();
        if (aaaId != null && !aaaId.trim().isEmpty()) {
            return aaaId.trim();
        }
        logger.error(LOG_PREFIX + "getAaaId AAA id not available, using arrangement id");
        return arrangementActivityRecord.getArrangement().getValue();
    }

    /** First linked application id of the arrangement (the account number). */
    private String getAccountNo(AaArrangementRecord arrangementRecord) {
        try {
            String accountNo = arrangementRecord.getLinkedAppl().get(0).getLinkedApplId().getValue();
            logger.info(LOG_PREFIX + "getAccountNo account=" + accountNo);
            return accountNo;
        } catch (Exception e) {
            logger.info(LOG_PREFIX + "getAccountNo linked account not found " + e.getMessage());
            return "";
        }
    }

    /** Logs the raw activity fields received from T24, for tracing. */
    private void logActivity(String hook, AaArrangementActivityRecord arrangementActivityRecord,
            ArrangementContext arrangementContext, AaArrangementRecord arrangementRecord) {
        try {
            logger.info(LOG_PREFIX + hook + " activity=" + arrangementActivityRecord.getActivity().getValue()
                    + " arrangement=" + arrangementActivityRecord.getArrangement().getValue()
                    + " aaa=" + arrangementContext.getArrangementActivityId()
                    + " customer=" + arrangementRecord.getCustomer().get(0).getCustomer().getValue()
                    + " txnAmount=" + arrangementActivityRecord.getTxnAmount().getValue()
                    + " effectiveDate=" + arrangementActivityRecord.getEffectiveDate().getValue());
        } catch (Exception e) {
            logger.info(LOG_PREFIX + hook + " unable to log activity fields " + e.getMessage());
        }
    }
}
