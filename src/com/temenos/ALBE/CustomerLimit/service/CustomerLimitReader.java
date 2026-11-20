package com.temenos.ALBE.CustomerLimit.service;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import java.math.BigDecimal;

import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;
import com.temenos.t24.api.records.ebalbeciflimit.EbAlbeCifLimitRecord;
import com.temenos.t24.api.records.ebalbegenparam.EbAlbeGenParamRecord;
import com.temenos.t24.api.records.ebalbegenparam.KeyNameClass;
import com.temenos.ALBE.CustomerLimit.model.Limits;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;

/*-----------------------------------------------------------------------------
 * Description : Builds Limits from the limit records (DB access via CustomerLimitRepository).
 *               getCustomerLimits : EB.ALBE.CIF.LIMIT, record id = customer id
 *                                   (used by the validators; no record => "limit not defined" override).
 *               getGenericLimits  : EB.ALBE.GEN.PARAM, record SYSTEM (used only by
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

    private static final Logger logger = LoggerFactory.getLogger(Constants.LOGGER_NAME);
    private static final String LOG_PREFIX = "CustomerLimitReader:";

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
        logger.info(LOG_PREFIX + "getCustomerLimits START reading " + Constants.CIF_LIMIT_TABLE + ">" + customerId);
        try {
            EbAlbeCifLimitRecord cifLimitRec = repository.readCifLimit(customerId);
            if (cifLimitRec == null) {
                logger.info(LOG_PREFIX + "getCustomerLimits no EB.ALBE.CIF.LIMIT record for " + customerId);
                return null;
            }
            Limits limits = new Limits();
            limits.ccy = cifLimitRec.getCcy().getValue();
            limits.dailyCreditLimit = toLimit(cifLimitRec.getDailyCreditLimit().getValue());
            limits.dailyDebitLimit = toLimit(cifLimitRec.getDailyDebitLimit().getValue());
            limits.monthlyCreditLimit = toLimit(cifLimitRec.getMonthlyCreditLimit().getValue());
            limits.monthlyDebitLimit = toLimit(cifLimitRec.getMonthlyDebitLimit().getValue());
            limits.maxCifBalance = toLimit(cifLimitRec.getMaxCifBalance().getValue());
            limits.source = Constants.CIF_LIMIT_TABLE + ">" + customerId;
            logLimits("getCustomerLimits", limits);
            return limits;
        } catch (Exception e) {
            logger.info(LOG_PREFIX + "getCustomerLimits no EB.ALBE.CIF.LIMIT record for " + customerId);
            return null;
        }
    }

    /**
     * Generic limits from EB.ALBE.GEN.PARAM SYSTEM (KEY.NAME / KEY.VALUE pairs).
     * Used only by CustomerLimitCreationService to build a new customer's record.
     * @return the limits, or null if the record or its keys are missing
     */
    public Limits getGenericLimits() {
        logger.info(LOG_PREFIX + "getGenericLimits START reading " + Constants.GEN_PARAM_TABLE + ">" + Constants.GEN_PARAM_ID);
        try {
            EbAlbeGenParamRecord genParamRec = repository.readGenParam(Constants.GEN_PARAM_ID);
            if (genParamRec == null) {
                logger.error(LOG_PREFIX + "getGenericLimits no EB.ALBE.GEN.PARAM SYSTEM record");
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
                logger.info(LOG_PREFIX + "getGenericLimits key=" + key + " value=" + value);
                found = true;
                switch (key.trim()) {
                case Constants.KEY_CCY:
                    limits.ccy = value;
                    break;
                case Constants.KEY_DAILY_CREDIT_LIMIT:
                    limits.dailyCreditLimit = toLimit(value);
                    break;
                case Constants.KEY_DAILY_DEBIT_LIMIT:
                    limits.dailyDebitLimit = toLimit(value);
                    break;
                case Constants.KEY_MONTHLY_CREDIT_LIMIT:
                    limits.monthlyCreditLimit = toLimit(value);
                    break;
                case Constants.KEY_MONTHLY_DEBIT_LIMIT:
                    limits.monthlyDebitLimit = toLimit(value);
                    break;
                case Constants.KEY_MAX_CIF_BALANCE:
                    limits.maxCifBalance = toLimit(value);
                    break;
                default:
                    // Other keys in the parameter record are not limit values.
                }
            }
            if (!found) {
                logger.error(LOG_PREFIX + "getGenericLimits EB.ALBE.GEN.PARAM SYSTEM has no keys");
                return null;
            }
            limits.source = Constants.GEN_PARAM_TABLE + ">" + Constants.GEN_PARAM_ID;
            logLimits("getGenericLimits", limits);
            return limits;
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "getGenericLimits no EB.ALBE.GEN.PARAM SYSTEM record " + e.getMessage());
            return null;
        }
    }

    private void logLimits(String method, Limits limits) {
        logger.info(LOG_PREFIX + method + " source=" + limits.source + " ccy=" + limits.ccy
                + " dailyCredit=" + limits.dailyCreditLimit + " dailyDebit=" + limits.dailyDebitLimit
                + " monthlyCredit=" + limits.monthlyCreditLimit + " monthlyDebit=" + limits.monthlyDebitLimit
                + " maxCifBalance=" + limits.maxCifBalance);
    }

    /** Blank limit => null (not configured, check skipped); otherwise the amount. */
    private BigDecimal toLimit(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return CustomerLimitUtilizationUpdater.toAmount(value);
    }
}
