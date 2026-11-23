package com.temenos.ALBE.CustomerLimit.controller.activity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.service.activity.CustomerLimitEngine;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.api.TField;
import com.temenos.t24.api.records.aaarrangement.AaArrangementRecord;
import com.temenos.t24.api.records.aaarrangementactivity.AaArrangementActivityRecord;

/*-----------------------------------------------------------------------------
 * Description : Common mapping from ANY arrangement activity (AA.ARRANGEMENT.ACTIVITY) to the
 *               request model, shared by the ACTIVITY.API controllers. The routines can be
 *               attached to any activity; this class decides whether it is counted:
 *
 *                 amount     ORIG.TXN.AMT if filled, else TXN.AMOUNT; no amount => not counted
 *                 direction  ACTION of the system-filled ACTIVITY.CLASS
 *                            (<PRODUCT.LINE>-<ACTION>-<PROPERTY.CLASS>): Constants.CREDIT_ACTIONS =
 *                            credit, Constants.DEBIT_ACTIONS = debit, any product line; other
 *                            actions => not counted (direction unknown)
 *                 initiation INITIATION.TYPE must be in Constants.COUNTED_INITIATION_TYPES
 *                 LCY amount ORIG.TXN.AMT.LCY / TXN.AMOUNT.LCY (used when the limit CCY is the LCY)
 *                 key        root of TXN.CONTRACT.ID (the FT / TT behind it), else the AAA id -
 *                            same key as the FT and accounting-entry routines
 *                 date       REMARKS tag (date counted at input) -> ORG.SYSTEM.DATE (T24 date of the
 *                            activity) -> T24 TODAY -> server clock (last resort, logged)
 *
 *               No business rules and no DB access here (T24 TODAY comes from the repository).
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 21-Nov-2026   Development      Initial Version (common for all activities)
 * 22-Nov-2026   Development      Key = root of TXN.CONTRACT.ID (else AAA id) (CustomerLimitEngine)
 * 22-Nov-2026   Development      AA_SKIP_TXN_SYSTEM_IDS removed - the common key prevents double counting
 * 23-Nov-2026   Development      Arrangement id on the request (part of the TXN.DETAILS id, FQ6)
 *-----------------------------------------------------------------------------
 */
public class ActivityRequestBuilder {
    private static final String CLASS_NAME = "ActivityRequestBuilder";

    private ActivityRequestBuilder() {
    }

    /**
     * Request for the limit checks from any activity: customer, AAA id, counted date, currency,
     * credit or debit amount (and its LCY amount). Null when the activity is not counted
     * (no amount, direction unknown or initiation type not counted) - the reason is logged.
     */
    public static CustomerLimitDTO build(AaArrangementActivityRecord aaaRec, AaArrangementRecord arrangementRecord,
            String aaaId, CustomerLimitRepository repository) {

        String activity = aaaRec.getActivity().getValue();
        String activityClass = aaaRec.getActivityClass().getValue();
        String initiationType = getInitiationType(aaaRec);
        if (!isCountedActivity(activityClass, initiationType, activity)) {
            return null;
        }

        boolean useOriginal = ValidationUtil.isPositive(ValidationUtil.toAmount(aaaRec.getOrigTxnAmt().getValue()));
        BigDecimal amount = getAmount(aaaRec, useOriginal);
        if (!ValidationUtil.isPositive(amount)) {
            LogUtil.info(CLASS_NAME, "build", "SKIP", "activity=" + activity + " class=" + activityClass
                    + " has no TXN.AMOUNT / ORIG.TXN.AMT");
            return null;
        }

        // Same key wherever the routine is attached: the FT / TT behind the activity if there is one.
        boolean credit = ValidationUtil.isCreditActivityClass(activityClass);
        String contractId = ValidationUtil.rootReference(aaaRec.getTxnContractId().getValue());
        String reference = ValidationUtil.isBlank(contractId) ? aaaId : contractId;

        CustomerLimitDTO request = new CustomerLimitDTO();
        request.setCustomerId(arrangementRecord.getCustomer().get(0).getCustomer().getValue());
        request.setTransactionId(CustomerLimitEngine.transactionKey(reference));
        request.setArrangementId(ValidationUtil.nvl(aaaRec.getArrangement().getValue()).trim());
        request.setTransactionDate(getCountedDate(aaaRec, repository));
        request.setTransactionCurrency(getCurrency(aaaRec, arrangementRecord));
        request.setAmountLcy(getAmountLcy(aaaRec, useOriginal));
        if (credit) {
            request.setCreditAmount(amount);
        } else {
            request.setDebitAmount(amount);
        }
        LogUtil.info(CLASS_NAME, "build", "CALC", "activity=" + activity + " class=" + activityClass
                + " initiation=" + initiationType + " amountFrom=" + (useOriginal ? "ORIG.TXN.AMT" : "TXN.AMOUNT")
                + " request=" + request);
        return request;
    }

    /**
     * True when activities of this class and initiation type are counted (amount not checked):
     * the class action is a credit or debit action and the initiation type is counted.
     */
    public static boolean isCountedActivity(String activityClass, String initiationType, String activity) {
        if (!ValidationUtil.isCreditActivityClass(activityClass) && !ValidationUtil.isDebitActivityClass(activityClass)) {
            LogUtil.info(CLASS_NAME, "isCountedActivity", "SKIP", "activity=" + activity + " class=" + activityClass
                    + " action=" + ValidationUtil.activityAction(activityClass)
                    + " is neither a credit nor a debit action");
            return false;
        }
        if (!ValidationUtil.isCountedInitiationType(initiationType)) {
            LogUtil.info(CLASS_NAME, "isCountedActivity", "SKIP", "activity=" + activity + " initiation="
                    + initiationType + " not counted");
            return false;
        }
        return true;
    }

    /** First INITIATION.TYPE of the activity; "" if not there. */
    public static String getInitiationType(AaArrangementActivityRecord aaaRec) {
        try {
            List<TField> types = aaaRec.getInitiationType();
            return types == null || types.isEmpty() ? "" : ValidationUtil.nvl(types.get(0).getValue()).trim();
        } catch (Exception e) {
            LogUtil.info(CLASS_NAME, "getInitiationType", "NOT FOUND", "INITIATION.TYPE not readable ", e);
            return "";
        }
    }

    /**
     * Counted date: the REMARKS tag (date counted at input, so a rejection or reversal on a later
     * day uses the same day and month), else ORG.SYSTEM.DATE, else T24 TODAY, else the server clock.
     */
    public static LocalDate getCountedDate(AaArrangementActivityRecord aaaRec, CustomerLimitRepository repository) {
        LocalDate tagged = dateFromRemarks(aaaRec.getRemarks().getValue());
        if (tagged != null) {
            return tagged;
        }
        return getT24Date(aaaRec, repository);
    }

    /** T24 date of the activity: ORG.SYSTEM.DATE, else T24 TODAY, else the server clock (logged). */
    public static LocalDate getT24Date(AaArrangementActivityRecord aaaRec, CustomerLimitRepository repository) {
        LocalDate orgSystemDate = ValidationUtil.toDate(aaaRec.getOrgSystemDate().getValue());
        if (orgSystemDate != null) {
            LogUtil.info(CLASS_NAME, "getT24Date", "FOUND", "ORG.SYSTEM.DATE=" + orgSystemDate);
            return orgSystemDate;
        }
        LocalDate today = repository.getT24Today();
        if (today != null) {
            LogUtil.info(CLASS_NAME, "getT24Date", "FOUND", "T24 TODAY=" + today);
            return today;
        }
        LocalDate clock = LocalDate.now();
        LogUtil.error(CLASS_NAME, "getT24Date", "FALLBACK", "no ORG.SYSTEM.DATE and no T24 TODAY, server clock "
                + clock + " used");
        return clock;
    }

    /** Date from the REMARKS tag ALBE.LIMIT.DATE=YYYYMMDD; null if not there or not a valid date. */
    public static LocalDate dateFromRemarks(String remarks) {
        String text = ValidationUtil.nvl(remarks);
        int pos = text.indexOf(Constants.REMARKS_DATE_TAG);
        if (pos < 0) {
            LogUtil.info(CLASS_NAME, "dateFromRemarks", "NOT FOUND", "no " + Constants.REMARKS_DATE_TAG + " in REMARKS");
            return null;
        }
        int start = pos + Constants.REMARKS_DATE_TAG.length();
        int end = Math.min(start + Constants.REMARKS_DATE_LENGTH, text.length());
        LocalDate date = ValidationUtil.toDate(text.substring(start, end));
        LogUtil.info(CLASS_NAME, "dateFromRemarks", date == null ? "INVALID" : "FOUND", "countedDate=" + date);
        return date;
    }

    /** ORIG.TXN.AMT (full original amount) when useOriginal, else TXN.AMOUNT. */
    private static BigDecimal getAmount(AaArrangementActivityRecord aaaRec, boolean useOriginal) {
        return ValidationUtil.toAmount(useOriginal ? aaaRec.getOrigTxnAmt().getValue()
                : aaaRec.getTxnAmount().getValue());
    }

    /** ORIG.TXN.AMT.LCY when useOriginal, else TXN.AMOUNT.LCY; 0 if blank. */
    private static BigDecimal getAmountLcy(AaArrangementActivityRecord aaaRec, boolean useOriginal) {
        return ValidationUtil.toAmount(useOriginal ? aaaRec.getOrigTxnAmtLcy().getValue()
                : aaaRec.getTxnAmountLcy().getValue());
    }

    /** Transaction currency: AAA CURRENCY (currency of the arrangement), else AA.ARRANGEMENT CURRENCY. */
    private static String getCurrency(AaArrangementActivityRecord aaaRec, AaArrangementRecord arrangementRecord) {
        String ccy = aaaRec.getCurrency().getValue();
        return ValidationUtil.isBlank(ccy) ? arrangementRecord.getCurrency().getValue() : ccy.trim();
    }
}
