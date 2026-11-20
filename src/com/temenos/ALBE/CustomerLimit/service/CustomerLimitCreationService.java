package com.temenos.ALBE.CustomerLimit.service;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.Limits;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.t24.api.records.ebalbeciflimit.EbAlbeCifLimitRecord;

/*-----------------------------------------------------------------------------
 * Description : Business rules for the customer limit creation (HLSD 7.2.1 - 1).
 *               Called by CustomerLimitCreationController; all DB access goes through
 *               CustomerLimitRepository.
 *
 *               create : copies EB.ALBE.GEN.PARAM CUSTOMER.LIMIT into EB.ALBE.CIF.LIMIT <customer>
 *                        if the customer has no record (an existing record is never
 *                        overwritten, so limits amended for the customer stay).
 *               Called at customer authorisation, so there is no delete flow.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitCreationService {
    private static final String CLASS_NAME = "CustomerLimitCreationService";

    private final CustomerLimitRepository repository;
    private final CustomerLimitReader limitReader;

    public CustomerLimitCreationService(CustomerLimitRepository repository) {
        this.repository = repository;
        this.limitReader = new CustomerLimitReader(repository);
    }

    /**
     * Creates the customer's limit record from the generic limits.
     * The customer id has been checked by CustomerLimitRequestValidator.
     * @return true if a record was created; false if it already existed or could not be created
     */
    public boolean create(String customerId) {
        LogUtil.info(CLASS_NAME, "create", "START", "customer=" + customerId);

        // Never overwrite: the customer is authorised again after every amendment, and the
        // limits may have been changed for this customer. Create only when T24 says "no record".
        Boolean exists = repository.cifLimitExists(customerId);
        if (exists == null) {
            LogUtil.error(CLASS_NAME, "create", "SKIP", "EB.ALBE.CIF.LIMIT read failed for " + customerId
                    + ", not created so an existing record is not overwritten");
            return false;
        }
        if (exists) {
            LogUtil.info(CLASS_NAME, "create", "SKIP", "EB.ALBE.CIF.LIMIT already exists for " + customerId
                    + " (customer amendment, limits kept)");
            return false;
        }

        Limits genericLimits = limitReader.getGenericLimits();
        if (genericLimits == null) {
            LogUtil.error(CLASS_NAME, "create", "NOT FOUND",
                    Constants.GEN_PARAM_TABLE + " " + Constants.GEN_PARAM_ID + " not found, limit not created for "
                    + customerId);
            return false;
        }
        LogUtil.info(CLASS_NAME, "create", "CALC", "generic " + genericLimits);

        // Copy the generic limits one-to-one into the customer's record.
        EbAlbeCifLimitRecord cifLimitRec = repository.newCifLimitRecord();
        cifLimitRec.setCcy(ValidationUtil.nvl(genericLimits.ccy));
        cifLimitRec.setDailyCreditLimit(ValidationUtil.toText(genericLimits.dailyCreditLimit));
        cifLimitRec.setDailyDebitLimit(ValidationUtil.toText(genericLimits.dailyDebitLimit));
        cifLimitRec.setMonthlyCreditLimit(ValidationUtil.toText(genericLimits.monthlyCreditLimit));
        cifLimitRec.setMonthlyDebitLimit(ValidationUtil.toText(genericLimits.monthlyDebitLimit));
        cifLimitRec.setMaxCifBalance(ValidationUtil.toText(genericLimits.maxCifBalance));

        boolean written = repository.writeCifLimit(customerId, cifLimitRec);
        LogUtil.info(CLASS_NAME, "create", "END", "customer=" + customerId + " created=" + written);
        return written;
    }
}
