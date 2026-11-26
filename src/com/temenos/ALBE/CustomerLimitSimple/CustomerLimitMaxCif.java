package com.temenos.ALBE.CustomerLimitSimple;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

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
import com.temenos.t24.api.records.account.AccountRecord;
import com.temenos.t24.api.records.company.CompanyRecord;
import com.temenos.t24.api.records.ebalbeciflimits.EbAlbeCifLimitsRecord;
import com.temenos.t24.api.records.ebalbegenparam.EbAlbeGenParamRecord;
import com.temenos.t24.api.records.ebalbegenparam.KeyNameClass;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;

/*-----------------------------------------------------------------------------
 * Attached as : ACTIVITY.API - Post Routine 2 on CREDIT activities (after ALBE.CL.ACTIVITY.SIMPLE),
 *               method validateRecord
 * EB.API      : ALBE.CL.MAX.CIF.SIMPLE
 * Attached to : activities mapped CREDIT in EB.ALBE.GEN.PARAM CUSTOMER.LIMIT.ACTIVITY
 *               (e.g. ACCOUNTS-TRANSFERIN-ARRANGEMENT)
 * Description : Maximum overall CIF balance, HLSD 7.2.1 - 3 (input only, credits only):
 *
 *   WORKING.BALANCE of every account in CUSTOMER.ACCOUNT <customer> (in the limit currency)
 *   + this credit (in the limit currency)  > MAXIMUM.OVERALL.CIF.AMOUNT -> override, by the excess
 *
 *   no CUSTOMER.ACCOUNT accounts      -> ERROR EB-ALBE.CUSTOMER.ACCOUNT.MISSING (transaction blocked)
 *   account in another currency       -> override EB-ABLE.EXCH.RATE.MISSING, not checked (no rate yet)
 *   MAXIMUM.OVERALL.CIF.AMOUNT blank  -> override EB-ABLE.CIF.LIMIT.NOT.DEFINED
 *   no limit record / blank CURRENCY / amount not convertible / debit / not mapped
 *                                     -> nothing here (CustomerLimitActivity raises those)
 *
 *   Nothing is counted here: counting is done by CustomerLimitActivity. An ERROR here stops the
 *   whole transaction, so T24 also rolls back what CustomerLimitActivity counted.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 26-Nov-2026   Development      Initial Version (max CIF as its own routine, user)
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitMaxCif extends ActivityLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(CustomerLimitConstants.LOGGER_NAME);
    private static final String NAME = "CustomerLimitMaxCif:";

    @Override
    public TValidationResponse validateRecord(AaAccountDetailsRecord accountDetailRecord,
            AaArrangementActivityRecord aaaRec, ArrangementContext arrangementContext,
            AaArrangementRecord arrangementRecord, AaArrangementActivityRecord masterActivityRecord,
            TStructure productPropertyRecord, AaProductCatalogRecord productRecord, TStructure currRecord) {

        try {
            String status = nvl(arrangementContext.getActivityStatus()).trim().toUpperCase();
            if (!CustomerLimitConstants.STATUS_UNAUTH.equals(status)) {
                return aaaRec.getValidationResponse();
            }
            String activity = nvl(aaaRec.getActivity().getValue()).trim();
            String ft = rootReference(aaaRec.getTxnContractId().getValue());
            String key = ft.isEmpty() ? nvl(arrangementContext.getArrangementActivityId()).trim() : ft;

            DataAccess da = new DataAccess(this);
            Session ss = new Session(this);
            if (!CustomerLimitConstants.CREDIT.equals(readDirection(da, activity))) {
                logger.info(CustomerLimitConstants.LOG_TAG + NAME + " SKIP | " +
                        key + " " + activity + " is not a mapped credit - max CIF not checked");
                return aaaRec.getValidationResponse();
            }
            boolean useOrig = toAmount(aaaRec.getOrigTxnAmt().getValue()).signum() > 0;
            BigDecimal credit = toAmount(useOrig ? aaaRec.getOrigTxnAmt().getValue() : aaaRec.getTxnAmount().getValue());
            if (credit.signum() <= 0) {
                return aaaRec.getValidationResponse();
            }
            String customer = arrangementRecord.getCustomer().get(0).getCustomer().getValue();

            // limit and its currency (missing / blank CURRENCY: raised by CustomerLimitActivity)
            EbAlbeCifLimitsRecord limits = readCifLimits(da, customer);
            String limitCcy = limits == null ? "" : nvl(limits.getCurrency().getValue()).trim();
            if (limits == null || limitCcy.isEmpty()) {
                logger.info(CustomerLimitConstants.LOG_TAG + NAME + " SKIP | " +
                        key + " customer " + customer + " no limit record / currency - max CIF not checked");
                return aaaRec.getValidationResponse();
            }
            String maxText = limits.getMaximumOverallCifAmount().getValue();
            if (isBlank(maxText)) {
                aaaRec.getCurrency().setOverride(text(CustomerLimitConstants.OVR_NOT_DEFINED, customer));
                logger.info(CustomerLimitConstants.LOG_TAG + NAME + " RESULT | MAX CIF " + key + " customer " + customer + " | " +
                        CustomerLimitConstants.FIELD_MAX_CIF
                        + " blank -> not checked, raised " + CustomerLimitConstants.OVR_NOT_DEFINED);
                return aaaRec.getValidationResponse();
            }

            // credit in the limit currency (not convertible: CustomerLimitActivity raises the error)
            BigDecimal amount = toLimitCurrency(aaaRec, arrangementRecord, ss, credit, useOrig, limitCcy);
            if (amount == null) {
                logger.info(CustomerLimitConstants.LOG_TAG + NAME + " SKIP | " +
                        key + " credit cannot be converted to " + limitCcy + " - max CIF not checked");
                return aaaRec.getValidationResponse();
            }

            StringBuilder result = new StringBuilder("MAX CIF " + key + " customer " + customer + " CREDIT " + amount
                    + " " + limitCcy);

            // balance of all the customer's accounts
            List<String> accounts = null;
            try {
                accounts = da.getConcatValues(CustomerLimitConstants.CUSTOMER_ACCOUNT_FILE, customer);
            } catch (Exception e) {
                logger.info(CustomerLimitConstants.LOG_TAG + NAME + " NOT FOUND | " +
                        CustomerLimitConstants.CUSTOMER_ACCOUNT_FILE + ">" + customer + " " + e);
            }
            if (accounts == null || accounts.isEmpty()) {
                aaaRec.getTxnAmount().setError(text(CustomerLimitConstants.ERR_NO_ACCOUNTS, customer));
                logger.info(CustomerLimitConstants.LOG_TAG + NAME + " RESULT | " +
                        result + " | BLOCKED: no accounts in " + CustomerLimitConstants.CUSTOMER_ACCOUNT_FILE);
                return aaaRec.getValidationResponse();
            }
            String mnemonic = companyMnemonic(da, ss);
            BigDecimal total = BigDecimal.ZERO;
            for (String accountId : accounts) {
                try {
                    AccountRecord account = new AccountRecord(da.getRecord(mnemonic,
                            CustomerLimitConstants.ACCOUNT_TABLE, "", accountId));
                    String ccy = nvl(account.getCurrency().getValue()).trim();
                    BigDecimal balance = toAmount(account.getWorkingBalance().getValue());
                    result.append(" | ").append(accountId).append(' ').append(balance).append(' ').append(ccy);
                    if (!ccy.isEmpty() && !ccy.equalsIgnoreCase(limitCcy)) {
                        String fromTo = CustomerLimitConstants.FROM + ccy + CustomerLimitConstants.TO + limitCcy;
                        aaaRec.getEffectiveDate().setOverride(text(CustomerLimitConstants.OVR_RATE_MISSING, fromTo));
                        logger.info(CustomerLimitConstants.LOG_TAG + NAME + " RESULT | " +
                                result + " | account in " + ccy + ", no rate -> not checked, raised "
                                + CustomerLimitConstants.OVR_RATE_MISSING);
                        return aaaRec.getValidationResponse();
                    }
                    total = total.add(balance);
                } catch (Exception e) {
                    result.append(" | ").append(accountId).append(" not readable, 0");
                    logger.info(CustomerLimitConstants.LOG_TAG + NAME + " NOT FOUND | " +
                            CustomerLimitConstants.ACCOUNT_TABLE + ">" + accountId + " " + e);
                }
            }

            // total + this credit against the maximum (equal is allowed)
            BigDecimal maximum = toAmount(maxText);
            BigDecimal projected = total.add(amount);
            result.append(" | balance ").append(total).append(" + this ").append(amount).append(" = ").append(projected);
            if (projected.compareTo(maximum) > 0) {
                BigDecimal excess = projected.subtract(maximum).setScale(CustomerLimitConstants.AMOUNT_SCALE,
                        RoundingMode.HALF_UP);
                aaaRec.getOrigTxnAmt().setOverride(text(CustomerLimitConstants.OVR_MAX_CIF,
                        customer + CustomerLimitConstants.BY + excess.toPlainString()));
                result.append(" > ").append(maximum).append(" EXCEEDED by ").append(excess).append(", raised ")
                        .append(CustomerLimitConstants.OVR_MAX_CIF);
            } else {
                result.append(" <= ").append(maximum).append(" OK");
            }
            logger.info(CustomerLimitConstants.LOG_TAG + NAME + " RESULT | " + result.toString());
        } catch (Exception e) {
            logger.info(CustomerLimitConstants.LOG_TAG + NAME + " ERROR | " + e.toString());
        }
        return aaaRec.getValidationResponse();
    }

    /**
     * Credit in the limit currency, the same way CustomerLimitActivity converts it: same currency
     * unchanged; else (limit in LCY) the activity's LCY amount, else x TXN.EXCH.RATE; null otherwise.
     */
    private BigDecimal toLimitCurrency(AaArrangementActivityRecord aaaRec, AaArrangementRecord arrangementRecord,
            Session ss, BigDecimal credit, boolean useOrig, String limitCcy) {
        String txnCcy = nvl(aaaRec.getCurrency().getValue()).trim();
        if (txnCcy.isEmpty()) {
            txnCcy = nvl(arrangementRecord.getCurrency().getValue()).trim();
        }
        if (txnCcy.isEmpty() || txnCcy.equalsIgnoreCase(limitCcy)) {
            return credit.setScale(CustomerLimitConstants.AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        String lcy = nvl(ss.getLocalCurrency()).trim();
        if (lcy.isEmpty() || !limitCcy.equalsIgnoreCase(lcy)) {
            return null;
        }
        BigDecimal lcyAmount = toAmount(useOrig ? aaaRec.getOrigTxnAmtLcy().getValue()
                : aaaRec.getTxnAmountLcy().getValue());
        if (lcyAmount.signum() > 0) {
            return lcyAmount.setScale(CustomerLimitConstants.AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal rate = toAmount(aaaRec.getTxnExchRate().getValue());
        return rate.signum() > 0 ? credit.multiply(rate).setScale(CustomerLimitConstants.AMOUNT_SCALE,
                RoundingMode.HALF_UP) : null;
    }

    private String readDirection(DataAccess da, String activity) {
        try {
            EbAlbeGenParamRecord param = new EbAlbeGenParamRecord(da.getRecord(CustomerLimitConstants.GEN_PARAM_TABLE,
                    CustomerLimitConstants.GEN_PARAM_ACTIVITY_ID));
            for (KeyNameClass k : param.getKeyName()) {
                if (activity.equals(nvl(k.getKeyName().getValue()).trim())) {
                    return nvl(k.getKeyValue().getValue()).trim().toUpperCase();
                }
            }
        } catch (Exception e) {
            logger.info(CustomerLimitConstants.LOG_TAG + NAME + " NOT FOUND | " +
                    CustomerLimitConstants.GEN_PARAM_TABLE + ">" + CustomerLimitConstants.GEN_PARAM_ACTIVITY_ID
                    + " " + e);
        }
        return "";
    }

    private EbAlbeCifLimitsRecord readCifLimits(DataAccess da, String customer) {
        try {
            EbAlbeCifLimitsRecord rec = new EbAlbeCifLimitsRecord(da.getRecord(CustomerLimitConstants.CIF_LIMIT_TABLE,
                    customer));
            return rec.toString().isEmpty() ? null : rec;
        } catch (Exception e) {
            return null;
        }
    }

    private String companyMnemonic(DataAccess da, Session ss) {
        try {
            return new CompanyRecord(da.getRecord(CustomerLimitConstants.COMPANY_TABLE, ss.getCompanyId()))
                    .getFinancialMne().getValue();
        } catch (Exception e) {
            logger.info(CustomerLimitConstants.LOG_TAG + NAME + " FAILED | company mnemonic " + e);
            return "";
        }
    }

    private static String text(String id, String param) {
        String p = nvl(param).replace(",", "").replace("[", "").replace("]", "").trim();
        return "[" + id + "," + p + "]";
    }

    private static String rootReference(String reference) {
        String text = nvl(reference).trim();
        int cut = text.indexOf('\\');
        if (cut >= 0) {
            text = text.substring(0, cut);
        }
        cut = text.indexOf(';');
        if (cut >= 0) {
            text = text.substring(0, cut);
        }
        return text.trim();
    }

    private static BigDecimal toAmount(String value) {
        try {
            return isBlank(value) ? BigDecimal.ZERO : new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
