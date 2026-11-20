package com.temenos.ALBE.CustomerLimit.validation;

import java.math.BigDecimal;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO.LimitBreach;
import com.temenos.ALBE.CustomerLimit.override.CustomerLimitOverride;
import com.temenos.ALBE.CustomerLimit.service.CustomerLimitReader;
import com.temenos.ALBE.CustomerLimit.service.CustomerLimitReader.Limits;
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
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;

/*-----------------------------------------------------------------------------
 * Attached as : ACTIVITY.API
 * Attached to : ACCOUNTS-CREDIT-ARRANGEMENT (validate record)
 * Description : Maximum balance verification routine (HLSD 7.2.1 - 3).
 *
 *   validateRecord (input, activity status UNAUTH, credit activities only):
 *     1. Reads MAX.CIF.BALANCE from EB.ALBE.CIF.LIMIT <customer id>.
 *        No record => override "Customer limit not defined for Customer <id>".
 *        Blank MAX.CIF.BALANCE => override "Customer limit not defined for Customer <id>".
 *     2. Sums the working balance of all the customer's accounts
 *        (CUSTOMER.ACCOUNT, falling back to a SELECT on ACCOUNT).
 *     3. Total + credit amount above MAX.CIF.BALANCE => override
 *        "Overall credit/debit exceed for Customer <customer id> by <amount difference>".
 *
 *   Independent of the daily/monthly check: does not read or update EB.ALBE.UTILISED.LIMIT.
 *   Exchange rate is 1 (LCY only).
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class MaximumCifLimitValidator extends ActivityLifecycle {

    private static final String L3API = "L3API";
    private static final Logger logger = LoggerFactory.getLogger(L3API);
    private static final String LOG_PREFIX = "MaximumCifLimitValidator:";

    private static final String CUSTOMER_ACCOUNT_FILE = "CUSTOMER.ACCOUNT";
    private static final String ACCOUNT_TABLE = "ACCOUNT";
    private static final String COMPANY = "COMPANY";
    private static final String STATUS_UNAUTH = "UNAUTH";
    private static final String DEBIT = "DEBIT";

    /** Exchange rate is fixed at 1 until currency conversion is confirmed. */
    private static final BigDecimal EXCHANGE_RATE = BigDecimal.ONE;

    /** HLSD override text: Overall credit/debit exceed for Customer <customer id> by <amount difference>. */
    public static final String MSG_LIMIT_EXCEEDED =
            "Overall credit/debit exceed for Customer %s by %s";

    /**
     * Input validation. Runs only for UNAUTH credit activities; raises the "limit not defined"
     * or the maximum balance override on TXN.AMOUNT.
     */
    @Override
    public TValidationResponse validateRecord(AaAccountDetailsRecord accountDetailRecord,
            AaArrangementActivityRecord arrangementActivityRecord, ArrangementContext arrangementContext,
            AaArrangementRecord arrangementRecord, AaArrangementActivityRecord masterActivityRecord,
            TStructure productPropertyRecord, AaProductCatalogRecord productRecord, TStructure currRecord) {

        String activityStatus = "";
        try {
            activityStatus = arrangementContext.getActivityStatus();
            String activity = arrangementActivityRecord.getActivity().getValue();
            logger.info(LOG_PREFIX + "validateRecord START status=" + activityStatus + " activity=" + activity);

            // Validation is only needed when the activity is input.
            if (!STATUS_UNAUTH.equalsIgnoreCase(activityStatus)) {
                logger.info(LOG_PREFIX + "validateRecord SKIP status=" + activityStatus + " (not UNAUTH)");
                return arrangementActivityRecord.getValidationResponse();
            }

            // HLSD: maximum balance check is for credit activities only.
            if (activity != null && activity.contains(DEBIT)) {
                logger.info(LOG_PREFIX + "validateRecord SKIP debit activity " + activity);
                return arrangementActivityRecord.getValidationResponse();
            }

            CustomerLimitDTO request = new CustomerLimitDTO();
            request.setCustomerId(arrangementRecord.getCustomer().get(0).getCustomer().getValue());
            request.setTransactionId(arrangementActivityRecord.getArrangement().getValue());
            request.setCreditAmount(toAmount(arrangementActivityRecord.getTxnAmount().getValue(), BigDecimal.ZERO));
            logger.info(LOG_PREFIX + "validateRecord customer=" + request.getCustomerId() + " arrangement="
                    + request.getTransactionId() + " credit=" + request.getCreditAmount());

            validate(request);

            // No EB.ALBE.CIF.LIMIT record: "limit not defined" override; nothing else can be checked.
            if (request.isLimitMissing()) {
                logger.info(LOG_PREFIX + "validateRecord OVERRIDE id=" + CustomerLimitOverride.LIMIT_NOT_DEFINED
                        + " customer=" + request.getCustomerId() + " reason=no EB.ALBE.CIF.LIMIT record");
                CustomerLimitOverride.raise(arrangementActivityRecord.getTxnAmount(),
                        CustomerLimitOverride.LIMIT_NOT_DEFINED, request.getCustomerId());
                return arrangementActivityRecord.getValidationResponse();
            }

            // MAX.CIF.BALANCE blank: "limit not defined" override.
            if (request.isLimitNotDefined()) {
                logger.info(LOG_PREFIX + "validateRecord OVERRIDE id=" + CustomerLimitOverride.LIMIT_NOT_DEFINED
                        + " customer=" + request.getCustomerId() + " blankFields=" + request.getUndefinedLimits());
                CustomerLimitOverride.raise(arrangementActivityRecord.getTxnAmount(),
                        CustomerLimitOverride.LIMIT_NOT_DEFINED, request.getCustomerId());
            }

            for (LimitBreach breach : request.getBreaches()) {
                logger.info(LOG_PREFIX + "validateRecord OVERRIDE id=" + breach.getOverrideId()
                        + " customer=" + request.getCustomerId() + " difference="
                        + breach.getDifference().toPlainString() + " message=" + breach.getMessage());
                CustomerLimitOverride.raise(arrangementActivityRecord.getTxnAmount(), breach.getOverrideId(),
                        request.getCustomerId(), breach.getDifference().toPlainString());
            }

            logger.info(LOG_PREFIX + "validateRecord END customer=" + request.getCustomerId()
                    + " breaches=" + request.getBreaches().size());

        } catch (Exception e) {
            logger.error(LOG_PREFIX + "validateRecord ERROR status=" + activityStatus + " " + e.getMessage());
        }

        return arrangementActivityRecord.getValidationResponse();
    }

    /** Maximum CIF balance check; records a breach with the HLSD message and amount difference. */
    public void validate(CustomerLimitDTO request) {
        String customerId = request.getCustomerId();
        if (isBlank(customerId)) {
            logger.info(LOG_PREFIX + "validate SKIP customer id missing");
            return;
        }

        BigDecimal transactionAmount = getTransactionAmount(request);
        if (transactionAmount.signum() <= 0) {
            // Maximum balance check applies to credit amounts only.
            logger.info(LOG_PREFIX + "validate SKIP customer=" + customerId + " no credit amount");
            return;
        }

        DataAccess daObj = new DataAccess(this);

        // Step 1: customer's maximum balance limit.
        Limits limits = new CustomerLimitReader(daObj).getCustomerLimits(customerId);
        if (limits == null) {
            // No EB.ALBE.CIF.LIMIT record: the caller raises the "limit not defined" override.
            request.setLimitMissing(true);
            return;
        }
        BigDecimal maximumCifLimit = limits.maxCifBalance;
        if (maximumCifLimit == null) {
            logger.info(LOG_PREFIX + "validate NOT DEFINED MAX.CIF.BALANCE is blank for customer=" + customerId);
            request.addUndefinedLimit("MAX.CIF.BALANCE");
            return;
        }

        // Step 2: total working balance of all the customer's accounts.
        String finMnemonic = getFinMnemonic(daObj);
        BigDecimal totalBalance = getTotalCifBalance(daObj, finMnemonic, customerId);

        // Step 3: compare total + credit amount with the limit.
        BigDecimal projectedBalance = totalBalance.add(transactionAmount);
        logger.info(LOG_PREFIX + "validate customer=" + customerId + " totalBalance=" + totalBalance
                + " txnAmount=" + transactionAmount + " projected=" + projectedBalance
                + " maxLimit=" + maximumCifLimit);

        if (isLimitExceeded(projectedBalance, maximumCifLimit)) {
            BigDecimal difference = projectedBalance.subtract(maximumCifLimit);
            logger.info(LOG_PREFIX + "validate BREACH customer=" + customerId + " difference=" + difference);
            request.addBreach(CustomerLimitOverride.MAX_CIF_BAL_EXCEED,
                    String.format(MSG_LIMIT_EXCEEDED, customerId, difference.toPlainString()), difference);
        } else {
            logger.info(LOG_PREFIX + "validate OK customer=" + customerId + " within maximum balance");
        }
    }

    /** Financial mnemonic of the current company, used to read ACCOUNT (same as the FF enquiry routines). */
    private String getFinMnemonic(DataAccess daObj) {
        try {
            Session ssObj = new Session(this);
            String companyId = ssObj.getCompanyId();
            CompanyRecord companyRec = new CompanyRecord(daObj.getRecord(COMPANY, companyId));
            String finMnemonic = companyRec.getFinancialMne().getValue();
            logger.info(LOG_PREFIX + "getFinMnemonic company=" + companyId + " mnemonic=" + finMnemonic);
            return finMnemonic;
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "getFinMnemonic unable to read COMPANY " + e.getMessage());
            return "";
        }
    }

    /** Sums the working balances of every account belonging to the customer. */
    private BigDecimal getTotalCifBalance(DataAccess daObj, String finMnemonic, String customerId) {
        BigDecimal total = BigDecimal.ZERO;
        List<String> accountIds = getCustomerAccountIds(daObj, finMnemonic, customerId);
        for (String accountId : accountIds) {
            total = total.add(getAccountBalance(daObj, finMnemonic, accountId).multiply(EXCHANGE_RATE));
        }
        logger.info(LOG_PREFIX + "getTotalCifBalance customer=" + customerId + " accounts=" + accountIds.size()
                + " total=" + total);
        return total;
    }

    /** Customer's accounts from the CUSTOMER.ACCOUNT concat file; falls back to a SELECT on ACCOUNT. */
    private List<String> getCustomerAccountIds(DataAccess daObj, String finMnemonic, String customerId) {
        try {
            List<String> accountIds = daObj.getConcatValues(CUSTOMER_ACCOUNT_FILE, customerId);
            if (accountIds != null && !accountIds.isEmpty()) {
                logger.info(LOG_PREFIX + "getCustomerAccountIds CUSTOMER.ACCOUNT customer=" + customerId
                        + " accounts=" + accountIds);
                return accountIds;
            }
        } catch (Exception e) {
            logger.info(LOG_PREFIX + "getCustomerAccountIds no CUSTOMER.ACCOUNT entry for " + customerId);
        }
        try {
            List<String> accountIds = daObj.selectRecords(finMnemonic, ACCOUNT_TABLE, "",
                    "WITH CUSTOMER EQ " + customerId);
            logger.info(LOG_PREFIX + "getCustomerAccountIds SELECT ACCOUNT customer=" + customerId
                    + " accounts=" + accountIds);
            return accountIds == null ? List.of() : accountIds;
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "getCustomerAccountIds unable to select ACCOUNT for " + customerId + " "
                    + e.getMessage());
            return List.of();
        }
    }

    /** Working balance of one account; 0 if the account cannot be read. */
    private BigDecimal getAccountBalance(DataAccess daObj, String finMnemonic, String accountId) {
        try {
            AccountRecord accountRec = new AccountRecord(daObj.getRecord(finMnemonic, ACCOUNT_TABLE, "", accountId));
            BigDecimal balance = toAmount(accountRec.getWorkingBalance().getValue(), BigDecimal.ZERO);
            logger.info(LOG_PREFIX + "getAccountBalance account=" + accountId + " workingBalance=" + balance);
            return balance;
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "getAccountBalance unable to read ACCOUNT " + accountId + " " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /** Credit amount of the current transaction in LCY. */
    private BigDecimal getTransactionAmount(CustomerLimitDTO request) {
        return request.getCreditAmount().multiply(EXCHANGE_RATE);
    }

    /** Strictly above the limit is a breach; equal to the limit is allowed. */
    private boolean isLimitExceeded(BigDecimal projectedBalance, BigDecimal maximumCifLimit) {
        return projectedBalance.compareTo(maximumCifLimit) > 0;
    }

    /** Parses a T24 amount; blank or invalid gives the default. */
    private BigDecimal toAmount(String value, BigDecimal defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            logger.error(LOG_PREFIX + "toAmount invalid amount '" + value + "'");
            return defaultValue;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
