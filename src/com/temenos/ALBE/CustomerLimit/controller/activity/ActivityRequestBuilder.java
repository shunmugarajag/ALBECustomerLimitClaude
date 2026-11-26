package com.temenos.ALBE.CustomerLimit.controller.activity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.service.CustomerLimitReader;
import com.temenos.ALBE.CustomerLimit.service.activity.CustomerLimitEngine;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.api.TField;
import com.temenos.t24.api.complex.aa.activityhook.ArrangementContext;
import com.temenos.t24.api.records.aaarrangement.AaArrangementRecord;
import com.temenos.t24.api.records.aaarrangementactivity.AaArrangementActivityRecord;

/*-----------------------------------------------------------------------------
 * Description : Common mapping from ANY arrangement activity (AA.ARRANGEMENT.ACTIVITY) to the
 *               request model, shared by the ACTIVITY.API controllers. The routines can be
 *               attached to any activity; this class decides whether it is counted:
 *
 *                 amount     ORIG.TXN.AMT if filled, else TXN.AMOUNT; no amount => not counted
 *                 direction  AAA ACTIVITY mapped in EB.ALBE.GEN.PARAM CUSTOMER.LIMIT.ACTIVITY
 *                            (KEY.VALUE CREDIT / DEBIT, D41); not mapped => not counted here and the
 *                            controllers raise EB-ALBE.ACTIVITY.NOT.MAPPED
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
 * 25-Nov-2026   Development      Direction from GEN.PARAM CUSTOMER.LIMIT.ACTIVITY instead of ACTIVITY.CLASS (D41)
 * 25-Nov-2026   Development      activity name on the request (RESULT log line)
 * 25-Nov-2026   Development      logActivity: one ACTIVITY line with the key activity fields (all hooks)
 * 26-Nov-2026   Development      counted date only from the activity ORG.SYSTEM.DATE / T24 TODAY; dateFromRemarks removed (D42)
 * 26-Nov-2026   Development      build with the direction already read; TXN.EXCH.RATE on the request (D44)
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
        return build(aaaRec, arrangementRecord, aaaId, getDirection(aaaRec, repository), repository);
    }

    /** Same, with the CREDIT / DEBIT direction already read by the caller (no second GEN.PARAM read). */
    public static CustomerLimitDTO build(AaArrangementActivityRecord aaaRec, AaArrangementRecord arrangementRecord,
            String aaaId, String direction, CustomerLimitRepository repository) {

        String activity = aaaRec.getActivity().getValue();
        String activityClass = aaaRec.getActivityClass().getValue();
        String initiationType = getInitiationType(aaaRec);
        if (!isCountedActivity(direction, initiationType, activity)) {
            return null;
        }

        boolean useOriginal = ValidationUtil.isPositive(ValidationUtil.toAmount(aaaRec.getOrigTxnAmt().getValue()));
        BigDecimal amount = getAmount(aaaRec, useOriginal);
        if (!ValidationUtil.isPositive(amount)) {
            LogUtil.detail(CLASS_NAME, "build", "SKIP", "activity=" + activity + " class=" + activityClass
                    + " has no TXN.AMOUNT / ORIG.TXN.AMT");
            return null;
        }

        // Same key wherever the routine is attached: the FT / TT behind the activity if there is one.
        boolean credit = Constants.DIRECTION_CREDIT.equals(direction);
        String contractId = ValidationUtil.rootReference(aaaRec.getTxnContractId().getValue());
        String reference = ValidationUtil.isBlank(contractId) ? aaaId : contractId;

        CustomerLimitDTO request = new CustomerLimitDTO();
        request.setCustomerId(arrangementRecord.getCustomer().get(0).getCustomer().getValue());
        request.setTransactionId(CustomerLimitEngine.transactionKey(reference));
        request.setArrangementId(ValidationUtil.nvl(aaaRec.getArrangement().getValue()).trim());
        request.setActivity(ValidationUtil.nvl(activity).trim());
        request.setTransactionDate(getCountedDate(aaaRec, repository));
        request.setTransactionCurrency(getCurrency(aaaRec, arrangementRecord));
        request.setAmountLcy(getAmountLcy(aaaRec, useOriginal));
        request.setTxnExchangeRate(ValidationUtil.toAmount(aaaRec.getTxnExchRate().getValue()));
        if (credit) {
            request.setCreditAmount(amount);
        } else {
            request.setDebitAmount(amount);
        }
        LogUtil.detail(CLASS_NAME, "build", "CALC", "activity=" + activity + " class=" + activityClass
                + " direction=" + direction + " initiation=" + initiationType + " amountFrom=" + (useOriginal ? "ORIG.TXN.AMT" : "TXN.AMOUNT")
                + " request=" + request);
        return request;
    }

    /**
     * CREDIT / DEBIT of the activity from EB.ALBE.GEN.PARAM CUSTOMER.LIMIT.ACTIVITY (D41); null when
     * the activity is not mapped (the controllers then raise ERR_ACTIVITY_NOT_MAPPED at input).
     */
    public static String getDirection(AaArrangementActivityRecord aaaRec, CustomerLimitRepository repository) {
        return new CustomerLimitReader(repository).getActivityDirection(aaaRec.getActivity().getValue());
    }

    /**
     * True when the activity is counted (amount not checked): it is mapped to CREDIT / DEBIT and
     * its initiation type is counted.
     */
    public static boolean isCountedActivity(String direction, String initiationType, String activity) {
        if (direction == null) {
            LogUtil.detail(CLASS_NAME, "isCountedActivity", "SKIP", "activity=" + activity
                    + " not mapped in " + Constants.GEN_PARAM_ACTIVITY_ID);
            return false;
        }
        if (!ValidationUtil.isCountedInitiationType(initiationType)) {
            LogUtil.detail(CLASS_NAME, "isCountedActivity", "SKIP", "activity=" + activity + " initiation="
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
     * Counted date = the T24 date of the activity (ORG.SYSTEM.DATE, else T24 TODAY). The same
     * activity keeps its ORG.SYSTEM.DATE on authorisation, rejection and reversal, so every step
     * uses the day the amount was counted on (D42; the REMARKS tag was removed).
     */
    public static LocalDate getCountedDate(AaArrangementActivityRecord aaaRec, CustomerLimitRepository repository) {
        return getT24Date(aaaRec, repository);
    }

    /** T24 date of the activity: ORG.SYSTEM.DATE, else T24 TODAY, else the server clock (logged). */
    public static LocalDate getT24Date(AaArrangementActivityRecord aaaRec, CustomerLimitRepository repository) {
        LocalDate orgSystemDate = ValidationUtil.toDate(aaaRec.getOrgSystemDate().getValue());
        if (orgSystemDate != null) {
            LogUtil.detail(CLASS_NAME, "getT24Date", "FOUND", "ORG.SYSTEM.DATE=" + orgSystemDate);
            return orgSystemDate;
        }
        LocalDate today = repository.getT24Today();
        if (today != null) {
            LogUtil.detail(CLASS_NAME, "getT24Date", "FOUND", "T24 TODAY=" + today);
            return today;
        }
        LocalDate clock = LocalDate.now();
        LogUtil.error(CLASS_NAME, "getT24Date", "FALLBACK", "no ORG.SYSTEM.DATE and no T24 TODAY, server clock "
                + clock + " used");
        return clock;
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

    /**
     * One INFO line with the activity as T24 sent it to the hook (event ACTIVITY), e.g.
     * ACTIVITY | hook=validateRecord status=UNAUTH aaa=AAACT.. activity=ACCOUNTS-CREDIT-ARRANGEMENT
     * class=.. arrangement=AA.. customer=100199 contract=FT26093BGWJR txnAmount=11000 ...
     * Never throws.
     */
    public static void logActivity(String className, String hook, AaArrangementActivityRecord aaaRec,
            ArrangementContext context, AaArrangementRecord arrangementRecord) {
        StringBuilder line = new StringBuilder(300).append("hook=").append(hook);
        try {
            line.append(" status=").append(context.getActivityStatus())
                    .append(" aaa=").append(context.getArrangementActivityId())
                    .append(" activity=").append(aaaRec.getActivity().getValue())
                    .append(" class=").append(aaaRec.getActivityClass().getValue())
                    .append(" arrangement=").append(aaaRec.getArrangement().getValue())
                    .append(" customer=").append(arrangementRecord.getCustomer().get(0).getCustomer().getValue())
                    .append(" contract=").append(aaaRec.getTxnContractId().getValue())
                    .append(" txnAmount=").append(aaaRec.getTxnAmount().getValue())
                    .append(" origTxnAmt=").append(aaaRec.getOrigTxnAmt().getValue())
                    .append(" txnAmountLcy=").append(aaaRec.getTxnAmountLcy().getValue())
                    .append(" txnExchRate=").append(aaaRec.getTxnExchRate().getValue())
                    .append(" currency=").append(aaaRec.getCurrency().getValue())
                    .append(" effectiveDate=").append(aaaRec.getEffectiveDate().getValue())
                    .append(" orgSystemDate=").append(aaaRec.getOrgSystemDate().getValue())
                    .append(" initiation=").append(getInitiationType(aaaRec));
        } catch (Exception e) {
            line.append(" (some fields not readable: ").append(e.getClass().getSimpleName()).append(')');
        }
        LogUtil.info(className, hook, Constants.LOG_EVENT_ACTIVITY, line);
    }
}
