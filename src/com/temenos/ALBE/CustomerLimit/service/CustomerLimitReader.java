package com.temenos.ALBE.CustomerLimit.service;

import java.math.BigDecimal;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.Limits;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.t24.api.records.ebalbeciflimit.EbAlbeCifLimitRecord;
import com.temenos.t24.api.records.ebalbegenparam.EbAlbeGenParamRecord;
import com.temenos.t24.api.records.ebalbegenparam.KeyNameClass;

/*-----------------------------------------------------------------------------
 * Description : Builds Limits from the limit records (DB access via CustomerLimitRepository).
 *               getCustomerLimits : EB.ALBE.CIF.LIMIT, record id = customer id
 *                                   (used by the validators; no record => "limit not defined" override).
 *               getGenericLimits  : EB.ALBE.GEN.PARAM, record CUSTOMER.LIMIT (used only by
 *                                   CustomerLimitCreationService), KEY.NAME / KEY.VALUE pairs:
 *                    CCY, DAILY.CREDIT.LIMIT, DAILY.DEBIT.LIMIT, MONTHLY.CREDIT.LIMIT,
 *                    MONTHLY.DEBIT.LIMIT, MAX.CIF.BALANCE
 *               A blank limit means "no limit".
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitReader {
    private static final String CLASS_NAME = "CustomerLimitReader";

    private final CustomerLimitRepository repository;

    /** @param repository DB access of the calling routine */
    public CustomerLimitReader(CustomerLimitRepository repository) {
        this.repository = repository;
    }

    /**
     * Customer's limits from EB.ALBE.CIF.LIMIT <customer id>.
     * @return the limits, or null if the customer has no record (the validators then raise an override)
     */
    public Limits getCustomerLimits(String customerId) {
        LogUtil.info(CLASS_NAME, "getCustomerLimits", "START", "reading " + Constants.CIF_LIMIT_TABLE + ">"
                + customerId);
        try {
            EbAlbeCifLimitRecord cifLimitRec = repository.readCifLimit(customerId);
            if (cifLimitRec == null) {
                LogUtil.info(CLASS_NAME, "getCustomerLimits", "NOT FOUND", "no EB.ALBE.CIF.LIMIT record for "
                        + customerId);
                return null;
            }
            Limits limits = new Limits();
            limits.ccy = cifLimitRec.getCcy().getValue();
            limits.dailyCreditLimit = ValidationUtil.toOptionalAmount(cifLimitRec.getDailyCreditLimit().getValue());
            limits.dailyDebitLimit = ValidationUtil.toOptionalAmount(cifLimitRec.getDailyDebitLimit().getValue());
            limits.monthlyCreditLimit = ValidationUtil.toOptionalAmount(cifLimitRec.getMonthlyCreditLimit().getValue());
            limits.monthlyDebitLimit = ValidationUtil.toOptionalAmount(cifLimitRec.getMonthlyDebitLimit().getValue());
            limits.maxCifBalance = ValidationUtil.toOptionalAmount(cifLimitRec.getMaxCifBalance().getValue());
            limits.source = Constants.CIF_LIMIT_TABLE + ">" + customerId;
            logLimits("getCustomerLimits", limits);
            return limits;
        } catch (Exception e) {
            LogUtil.info(CLASS_NAME, "getCustomerLimits", "NOT FOUND", "no EB.ALBE.CIF.LIMIT record for " + customerId);
            return null;
        }
    }

    /**
     * Generic limits from EB.ALBE.GEN.PARAM CUSTOMER.LIMIT (KEY.NAME / KEY.VALUE pairs).
     * Used only by CustomerLimitCreationService to build a new customer's record.
     * @return the limits, or null if the record or its keys are missing
     */
    public Limits getGenericLimits() {
        LogUtil.info(CLASS_NAME, "getGenericLimits", "START", "reading " + Constants.GEN_PARAM_TABLE + ">"
                + Constants.GEN_PARAM_ID);
        try {
            EbAlbeGenParamRecord genParamRec = repository.readGenParam(Constants.GEN_PARAM_ID);
            if (genParamRec == null) {
                LogUtil.error(CLASS_NAME, "getGenericLimits", "NOT FOUND", "no " + Constants.GEN_PARAM_TABLE + " " + Constants.GEN_PARAM_ID + " record");
                return null;
            }
            Limits limits = new Limits();
            boolean found = false;
            for (KeyNameClass keyName : genParamRec.getKeyName()) {
                String key = keyName.getKeyName().getValue();
                String value = keyName.getKeyValue().getValue();
                if (key == null) {
                    continue;
                }
                LogUtil.info(CLASS_NAME, "getGenericLimits", "CALC", "key=" + key + " value=" + value);
                found = true;
                switch (key.trim()) {
                case Constants.KEY_CCY:
                    limits.ccy = value;
                    break;
                case Constants.KEY_DAILY_CREDIT_LIMIT:
                    limits.dailyCreditLimit = ValidationUtil.toOptionalAmount(value);
                    break;
                case Constants.KEY_DAILY_DEBIT_LIMIT:
                    limits.dailyDebitLimit = ValidationUtil.toOptionalAmount(value);
                    break;
                case Constants.KEY_MONTHLY_CREDIT_LIMIT:
                    limits.monthlyCreditLimit = ValidationUtil.toOptionalAmount(value);
                    break;
                case Constants.KEY_MONTHLY_DEBIT_LIMIT:
                    limits.monthlyDebitLimit = ValidationUtil.toOptionalAmount(value);
                    break;
                case Constants.KEY_MAX_CIF_BALANCE:
                    limits.maxCifBalance = ValidationUtil.toOptionalAmount(value);
                    break;
                default:
                    // Other keys in the parameter record are not limit values.
                }
            }
            if (!found) {
                LogUtil.error(CLASS_NAME, "getGenericLimits", "INVALID", Constants.GEN_PARAM_TABLE + " " + Constants.GEN_PARAM_ID + " has no keys");
                return null;
            }
            limits.source = Constants.GEN_PARAM_TABLE + ">" + Constants.GEN_PARAM_ID;
            logLimits("getGenericLimits", limits);
            return limits;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "getGenericLimits", "NOT FOUND", "no " + Constants.GEN_PARAM_TABLE + " " + Constants.GEN_PARAM_ID + " record ", e);
            return null;
        }
    }

    private void logLimits(String method, Limits limits) {
        LogUtil.info(CLASS_NAME, method, "CALC", "source=" + limits.source + " ccy=" + limits.ccy
                + " dailyCredit=" + limits.dailyCreditLimit + " dailyDebit=" + limits.dailyDebitLimit
                + " monthlyCredit=" + limits.monthlyCreditLimit + " monthlyDebit=" + limits.monthlyDebitLimit
                + " maxCifBalance=" + limits.maxCifBalance);
    }
}
