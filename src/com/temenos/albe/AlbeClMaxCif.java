package com.temenos.albe;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.temenos.api.TField;
import com.temenos.api.TNumber;
import com.temenos.api.TStructure;
import com.temenos.api.TValidationResponse;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;
import com.temenos.t24.api.complex.aa.activityhook.ArrangementContext;
import com.temenos.t24.api.complex.st.currencyapi.ExchangeAmount;
import com.temenos.t24.api.hook.arrangement.ActivityLifecycle;
import com.temenos.t24.api.party.Account;
import com.temenos.t24.api.rates.Currency;
import com.temenos.t24.api.records.aaaccountdetails.AaAccountDetailsRecord;
import com.temenos.t24.api.records.aaarrangement.AaArrangementRecord;
import com.temenos.t24.api.records.aaarrangementactivity.AaArrangementActivityRecord;
import com.temenos.t24.api.records.aaproductcatalog.AaProductCatalogRecord;
import com.temenos.t24.api.records.account.AccountRecord;
import com.temenos.t24.api.records.company.CompanyRecord;
import com.temenos.t24.api.records.ebalbeciflimit.EbAlbeCifLimitRecord;
import com.temenos.t24.api.records.ebalbegenparam.EbAlbeGenParamRecord;
import com.temenos.t24.api.records.ebalbegenparam.KeyNameClass;
import com.temenos.t24.api.records.ebcontractbalances.EbContractBalancesRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;

/*-----------------------------------------------------------------------------
 * Attached as : ACTIVITY.API - Post Routine 2 on CREDIT activities (after ALBE.CL.ACTIVITY.LIMIT),
 *               method validateRecord
 * EB.API      : ALBE.CL.MAX.CIF
 * Attached to : activities mapped CREDIT in EB.ALBE.GEN.PARAM CUSTOMER.LIMIT.ACTIVITY
 *               (e.g. ACCOUNTS-TRANSFERIN-ARRANGEMENT)
 * Description : Maximum overall CIF balance, HLSD 7.2.1 - 3 (input only, credits only):
 *
 *   WORKING.BALANCE of every account in CUSTOMER.ACCOUNT <customer> (in the limit currency)
 *   + this credit (in the limit currency)  > MAXIMUM.OVERALL.CIF.AMOUNT -> override, by the excess
 *
 *   balance  : EB.CONTRACT.BALANCES WORKING.BALANCE, read with the Account API (the ACCOUNT record
 *              of an AA account holds no balance, D54); account currency from the ACCOUNT record
 *   currency : account in another currency -> converted with the T24 Currency API (EXCHRATE,
 *              CURRENCY.MARKET 1, today's rate)
 *
 *   no CUSTOMER.ACCOUNT accounts      -> ERROR EB-ALBE.CUSTOMER.ACCOUNT.MISSING (transaction blocked)
 *   no rate / conversion failed       -> override EB-ALBE.EXCH.RATE.MISSING, not checked
 *   MAXIMUM.OVERALL.CIF.AMOUNT blank  -> not checked, no override (D52)
 *   no limit record / blank CURRENCY / amount not convertible / debit / not mapped
 *                                     -> nothing here (AlbeClActivityLimit raises those)
 *
 *   Nothing is counted here: counting is done by AlbeClActivityLimit. An ERROR here stops the
 *   whole transaction, so T24 also rolls back what AlbeClActivityLimit counted.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 26-Nov-2026   Development      Initial Version (max CIF as its own routine, user)
 * 26-Nov-2026   Development      Package com.temenos.albe, class named after the EB.API (MVC version removed)
 * 27-Nov-2026   Development      Blank MAXIMUM.OVERALL.CIF.AMOUNT -> not checked, no override (user, D52)
 * 27-Nov-2026   Development      Old EB-ALBE overrides removed before each new check on input (re-validate /
 *                                amendment showed old and new overrides together, FT26093000F5) (user, D56)
 * 27-Nov-2026   Development      Balance from EB.CONTRACT.BALANCES via the Account API; other-currency
 *                                accounts converted with the Currency API, no rate -> override (user, D54)
 *-----------------------------------------------------------------------------
 */
public class AlbeClMaxCif extends ActivityLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(AlbeClConstants.LOGGER_NAME);
    private static final String NAME = "AlbeClMaxCif:";

    @Override
    public TValidationResponse validateRecord(AaAccountDetailsRecord accountDetailRecord,
            AaArrangementActivityRecord aaaRec, ArrangementContext arrangementContext,
            AaArrangementRecord arrangementRecord, AaArrangementActivityRecord masterActivityRecord,
            TStructure productPropertyRecord, AaProductCatalogRecord productRecord, TStructure currRecord) {

        try {
            String status = nvl(arrangementContext.getActivityStatus()).trim().toUpperCase();
            if (!AlbeClConstants.STATUS_UNAUTH.equals(status)) {
                return aaaRec.getValidationResponse();
            }
            String activity = nvl(aaaRec.getActivity().getValue()).trim();
            String ft = rootReference(aaaRec.getTxnContractId().getValue());
            String key = ft.isEmpty() ? nvl(arrangementContext.getArrangementActivityId()).trim() : ft;
            int removed = removeOldOverrides(aaaRec, AlbeClConstants.OVR_MAX_CIF);
            if (removed > 0) {
                logger.info(AlbeClConstants.LOG_TAG + NAME + " OVERRIDES | " + key + " removed " + removed +
                        " old " + AlbeClConstants.OVR_MAX_CIF + " override(s) before the new check");
            }

            DataAccess da = new DataAccess(this);
            Session ss = new Session(this);
            if (!AlbeClConstants.CREDIT.equals(readDirection(da, activity))) {
                logger.info(AlbeClConstants.LOG_TAG + NAME + " SKIP | " +
                        key + " " + activity + " is not a mapped credit - max CIF not checked");
                return aaaRec.getValidationResponse();
            }
            boolean useOrig = toAmount(aaaRec.getOrigTxnAmt().getValue()).signum() > 0;
            BigDecimal credit = toAmount(useOrig ? aaaRec.getOrigTxnAmt().getValue() : aaaRec.getTxnAmount().getValue());
            if (credit.signum() <= 0) {
                return aaaRec.getValidationResponse();
            }
            String customer = arrangementRecord.getCustomer().get(0).getCustomer().getValue();

            // limit and its currency (missing / blank CURRENCY: raised by AlbeClActivityLimit)
            EbAlbeCifLimitRecord limits = readCifLimits(da, customer);
            String limitCcy = limits == null ? "" : nvl(limits.getCurrency().getValue()).trim();
            if (limits == null || limitCcy.isEmpty()) {
                logger.info(AlbeClConstants.LOG_TAG + NAME + " SKIP | " +
                        key + " customer " + customer + " no limit record / currency - max CIF not checked");
                return aaaRec.getValidationResponse();
            }
            String maxText = limits.getMaximumOverallCifAmount().getValue();
            if (isBlank(maxText)) {
                logger.info(AlbeClConstants.LOG_TAG + NAME + " SKIP | MAX CIF " + key + " customer " + customer + " | " +
                        AlbeClConstants.FIELD_MAX_CIF + " blank -> not checked");
                return aaaRec.getValidationResponse();
            }

            // credit in the limit currency (not convertible: AlbeClActivityLimit raises the error)
            BigDecimal amount = toLimitCurrency(aaaRec, arrangementRecord, ss, credit, useOrig, limitCcy);
            if (amount == null) {
                logger.info(AlbeClConstants.LOG_TAG + NAME + " SKIP | " +
                        key + " credit cannot be converted to " + limitCcy + " - max CIF not checked");
                return aaaRec.getValidationResponse();
            }

            StringBuilder result = new StringBuilder("MAX CIF " + key + " customer " + customer + " CREDIT " + amount
                    + " " + limitCcy);

            // balance of all the customer's accounts
            List<String> accounts = null;
            try {
                accounts = da.getConcatValues(AlbeClConstants.CUSTOMER_ACCOUNT_FILE, customer);
            } catch (Exception e) {
                logger.info(AlbeClConstants.LOG_TAG + NAME + " NOT FOUND | " +
                        AlbeClConstants.CUSTOMER_ACCOUNT_FILE + ">" + customer + " " + e);
            }
            if (accounts == null || accounts.isEmpty()) {
                aaaRec.getTxnAmount().setError(text(AlbeClConstants.ERR_NO_ACCOUNTS, customer));
                logger.info(AlbeClConstants.LOG_TAG + NAME + " RESULT | " +
                        result + " | BLOCKED: no accounts in " + AlbeClConstants.CUSTOMER_ACCOUNT_FILE);
                return aaaRec.getValidationResponse();
            }
            String mnemonic = companyMnemonic(da, ss);
            BigDecimal total = BigDecimal.ZERO;
            for (String accountId : accounts) {
                try {
                    AccountRecord account = new AccountRecord(da.getRecord(mnemonic,
                            AlbeClConstants.ACCOUNT_TABLE, "", accountId));
                    String ccy = nvl(account.getCurrency().getValue()).trim();
                    BigDecimal balance = workingBalance(accountId);
                    result.append(" | ").append(accountId).append(' ').append(balance).append(' ').append(ccy);
                    if (!ccy.isEmpty() && !ccy.equalsIgnoreCase(limitCcy)) {
                        BigDecimal converted = convert(balance, ccy, limitCcy);
                        if (converted == null) {
                            String fromTo = AlbeClConstants.FROM + ccy + AlbeClConstants.TO + limitCcy;
                            aaaRec.getEffectiveDate().setOverride(text(AlbeClConstants.OVR_RATE_MISSING, fromTo));
                            logger.info(AlbeClConstants.LOG_TAG + NAME + " RESULT | " +
                                    result + " | no rate " + fromTo + " -> not checked, raised "
                                    + AlbeClConstants.OVR_RATE_MISSING);
                            return aaaRec.getValidationResponse();
                        }
                        result.append(" = ").append(converted).append(' ').append(limitCcy);
                        balance = converted;
                    }
                    total = total.add(balance);
                } catch (Exception e) {
                    result.append(" | ").append(accountId).append(" not readable, 0");
                    logger.info(AlbeClConstants.LOG_TAG + NAME + " NOT FOUND | " + AlbeClConstants.ACCOUNT_TABLE
                            + " / " + AlbeClConstants.CONTRACT_BALANCES_TABLE + ">" + accountId + " " + e);
                }
            }

            // total + this credit against the maximum (equal is allowed)
            BigDecimal maximum = toAmount(maxText);
            BigDecimal projected = total.add(amount);
            result.append(" | balance ").append(total).append(" + this ").append(amount).append(" = ").append(projected);
            if (projected.compareTo(maximum) > 0) {
                BigDecimal excess = projected.subtract(maximum).setScale(AlbeClConstants.AMOUNT_SCALE,
                        RoundingMode.HALF_UP);
                aaaRec.getOrigTxnAmt().setOverride(text(AlbeClConstants.OVR_MAX_CIF,
                        customer + AlbeClConstants.BY + excess.toPlainString()));
                result.append(" > ").append(maximum).append(" EXCEEDED by ").append(excess).append(", raised ")
                        .append(AlbeClConstants.OVR_MAX_CIF);
            } else {
                result.append(" <= ").append(maximum).append(" OK");
            }
            logger.info(AlbeClConstants.LOG_TAG + NAME + " RESULT | " + result.toString());
        } catch (Exception e) {
            logger.info(AlbeClConstants.LOG_TAG + NAME + " ERROR | " + e.toString());
        }
        return aaaRec.getValidationResponse();
    }

    /**
     * Credit in the limit currency, the same way AlbeClActivityLimit converts it: same currency
     * unchanged; else (limit in LCY) the activity's LCY amount, else x TXN.EXCH.RATE; null otherwise.
     */
    private BigDecimal toLimitCurrency(AaArrangementActivityRecord aaaRec, AaArrangementRecord arrangementRecord,
            Session ss, BigDecimal credit, boolean useOrig, String limitCcy) {
        String txnCcy = nvl(aaaRec.getCurrency().getValue()).trim();
        if (txnCcy.isEmpty()) {
            txnCcy = nvl(arrangementRecord.getCurrency().getValue()).trim();
        }
        if (txnCcy.isEmpty() || txnCcy.equalsIgnoreCase(limitCcy)) {
            return credit.setScale(AlbeClConstants.AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        String lcy = nvl(ss.getLocalCurrency()).trim();
        if (lcy.isEmpty() || !limitCcy.equalsIgnoreCase(lcy)) {
            return null;
        }
        BigDecimal lcyAmount = toAmount(useOrig ? aaaRec.getOrigTxnAmtLcy().getValue()
                : aaaRec.getTxnAmountLcy().getValue());
        if (lcyAmount.signum() > 0) {
            return lcyAmount.setScale(AlbeClConstants.AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal rate = toAmount(aaaRec.getTxnExchRate().getValue());
        return rate.signum() > 0 ? credit.multiply(rate).setScale(AlbeClConstants.AMOUNT_SCALE,
                RoundingMode.HALF_UP) : null;
    }

    /**
     * WORKING.BALANCE of EB.CONTRACT.BALANCES for the account (Account API); 0 when blank.
     * A read failure is thrown to the caller (account logged as not readable, counted as 0).
     */
    private BigDecimal workingBalance(String accountId) {
        Account accountApi = new Account(this);
        accountApi.setAccountId(accountId);
        EbContractBalancesRecord balances = accountApi.getContractBalancesRecord();
        return balances == null ? BigDecimal.ZERO : toAmount(balances.getWorkingBalance().getValue());
    }

    /**
     * Balance in the limit currency with the T24 Currency API (EXCHRATE): buy = limit currency,
     * sell = account currency, CURRENCY.MARKET 1, today's rate. Zero stays zero; the sign is kept
     * (overdrawn account). null when no rate is found or the call fails.
     */
    private BigDecimal convert(BigDecimal balance, String accountCcy, String limitCcy) {
        if (balance.signum() == 0) {
            return BigDecimal.ZERO.setScale(AlbeClConstants.AMOUNT_SCALE);
        }
        try {
            Currency currency = new Currency(this);
            currency.setCurrencyId(accountCcy);
            ExchangeAmount exchange = currency.calculateBuyAmount(limitCcy, accountCcy,
                    new TNumber(balance.abs().toPlainString()), AlbeClConstants.CURRENCY_MARKET, null, null);
            BigDecimal value = exchange == null || exchange.getDealAmount() == null
                    || exchange.getDealAmount().getValue() == null ? BigDecimal.ZERO
                    : toAmount(exchange.getDealAmount().getValue().get());
            if (value.signum() <= 0) {
                logger.info(AlbeClConstants.LOG_TAG + NAME + " FAILED | rate " + accountCcy + " to " + limitCcy
                        + " market " + AlbeClConstants.CURRENCY_MARKET + " gave no amount");
                return null;
            }
            value = value.setScale(AlbeClConstants.AMOUNT_SCALE, RoundingMode.HALF_UP);
            return balance.signum() < 0 ? value.negate() : value;
        } catch (Exception e) {
            logger.info(AlbeClConstants.LOG_TAG + NAME + " FAILED | rate " + accountCcy + " to " + limitCcy
                    + " " + e);
            return null;
        }
    }

    private String readDirection(DataAccess da, String activity) {
        try {
            EbAlbeGenParamRecord param = new EbAlbeGenParamRecord(da.getRecord(AlbeClConstants.GEN_PARAM_TABLE,
                    AlbeClConstants.GEN_PARAM_ACTIVITY_ID));
            for (KeyNameClass k : param.getKeyName()) {
                if (activity.equals(nvl(k.getKeyName().getValue()).trim())) {
                    return nvl(k.getKeyValue().getValue()).trim().toUpperCase();
                }
            }
        } catch (Exception e) {
            logger.info(AlbeClConstants.LOG_TAG + NAME + " NOT FOUND | " +
                    AlbeClConstants.GEN_PARAM_TABLE + ">" + AlbeClConstants.GEN_PARAM_ACTIVITY_ID
                    + " " + e);
        }
        return "";
    }

    private EbAlbeCifLimitRecord readCifLimits(DataAccess da, String customer) {
        try {
            EbAlbeCifLimitRecord rec = new EbAlbeCifLimitRecord(da.getRecord(AlbeClConstants.CIF_LIMIT_TABLE,
                    customer));
            return rec.toString().isEmpty() ? null : rec;
        } catch (Exception e) {
            return null;
        }
    }

    private String companyMnemonic(DataAccess da, Session ss) {
        try {
            return new CompanyRecord(da.getRecord(AlbeClConstants.COMPANY_TABLE, ss.getCompanyId()))
                    .getFinancialMne().getValue();
        } catch (Exception e) {
            logger.info(AlbeClConstants.LOG_TAG + NAME + " FAILED | company mnemonic " + e);
            return "";
        }
    }

    /**
     * Removes this module's overrides left on the activity by an earlier validation (re-validate, amendment),
     * so only the overrides of the current check are shown (D56). Other modules' overrides are kept.
     */
    private static int removeOldOverrides(AaArrangementActivityRecord aaaRec, String prefix) {
        int removed = 0;
        try {
            List<TField> list = aaaRec.getOverride();
            for (int i = list.size() - 1; i >= 0; i--) {
                TField entry = list.get(i);
                if (entry != null && nvl(entry.getValue()).trim().startsWith(prefix)) {
                    aaaRec.removeOverride(i);
                    removed++;
                }
            }
        } catch (Exception e) {
            logger.info(AlbeClConstants.LOG_TAG + NAME + " FAILED | removing old overrides " + e);
        }
        return removed;
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
