package com.temenos.ALBE.CustomerLimit.service;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import java.math.BigDecimal;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.model.Limits;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.view.CustomerLimitOverride;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;
import com.temenos.t24.api.records.account.AccountRecord;

/*-----------------------------------------------------------------------------
 * Description : Business rules for the maximum overall CIF balance (HLSD 7.2.1 - 3).
 *               Called by MaximumCifLimitController; all DB access goes through
 *               CustomerLimitRepository.
 *
 *               total working balance of all the customer's accounts + credit amount
 *               above MAX.CIF.BALANCE => breach by the difference.
 *               No EB.ALBE.CIF.LIMIT record => limitMissing; blank MAX.CIF.BALANCE =>
 *               limit not defined. The credit amount and every account balance are converted
 *               to the limit currency (EB.ALBE.CIF.LIMIT CCY) when their currency differs.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class MaximumCifLimitService {

    private static final Logger logger = LoggerFactory.getLogger(Constants.LOGGER_NAME);
    private static final String LOG_PREFIX = "MaximumCifLimitService:";

    private final CustomerLimitRepository repository;
    private final CustomerLimitReader limitReader;
    private final CurrencyConversionService currencyService;

    public MaximumCifLimitService(CustomerLimitRepository repository) {
        this.repository = repository;
        this.limitReader = new CustomerLimitReader(repository);
        this.currencyService = new CurrencyConversionService(repository);
    }

    /** Maximum CIF balance check; records a breach with the HLSD message and amount difference. */
    public void validate(CustomerLimitDTO request) {
        String customerId = request.getCustomerId();
        logger.info(LOG_PREFIX + "validate START " + request);

        if (customerId == null || customerId.trim().isEmpty()) {
            logger.info(LOG_PREFIX + "validate SKIP customer id missing");
            return;
        }
        if (request.getCreditAmount().signum() <= 0) {
            logger.info(LOG_PREFIX + "validate SKIP customer=" + customerId + " no credit amount");
            return;
        }

        // Step 1: customer's maximum balance limit.
        Limits limits = limitReader.getCustomerLimits(customerId);
        if (limits == null) {
            logger.info(LOG_PREFIX + "validate END customer=" + customerId + " no EB.ALBE.CIF.LIMIT record");
            request.setLimitMissing(true);
            return;
        }
        BigDecimal maximumCifLimit = limits.maxCifBalance;
        if (maximumCifLimit == null) {
            logger.info(LOG_PREFIX + "validate NOT DEFINED MAX.CIF.BALANCE is blank for customer=" + customerId);
            request.addUndefinedLimit(Constants.KEY_MAX_CIF_BALANCE);
            return;
        }

        // Credit amount in the limit currency.
        if (!currencyService.convertRequest(request, limits.ccy)) {
            logger.info(LOG_PREFIX + "validate END customer=" + customerId + " no exchange rate for the credit");
            return;
        }
        BigDecimal transactionAmount = request.getCreditAmount();

        // Step 2: total working balance of all the customer's accounts.
        String finMnemonic = repository.getFinancialMnemonic();
        BigDecimal totalBalance = getTotalCifBalance(request, finMnemonic, limits.ccy);
        if (totalBalance == null) {
            logger.info(LOG_PREFIX + "validate END customer=" + customerId + " no exchange rate for account currency "
                    + request.getRateMissingCurrency());
            return;
        }

        // Step 3: compare total + credit amount with the limit (equal to the limit is allowed).
        BigDecimal projectedBalance = totalBalance.add(transactionAmount);
        logger.info(LOG_PREFIX + "validate customer=" + customerId + " totalBalance=" + totalBalance
                + " txnAmount=" + transactionAmount + " projected=" + projectedBalance + " maxLimit=" + maximumCifLimit);

        if (projectedBalance.compareTo(maximumCifLimit) > 0) {
            BigDecimal difference = projectedBalance.subtract(maximumCifLimit);
            logger.info(LOG_PREFIX + "validate BREACH customer=" + customerId + " difference=" + difference);
            request.addBreach(Constants.OVR_MAX_CIF_BAL_EXCEED,
                    String.format(Constants.MSG_MAX_CIF_EXCEEDED, customerId, difference.toPlainString()), difference);
        } else {
            logger.info(LOG_PREFIX + "validate OK customer=" + customerId + " headroom="
                    + maximumCifLimit.subtract(projectedBalance));
        }
        logger.info(LOG_PREFIX + "validate END " + request);
    }

    /**
     * Sums the working balances of every account belonging to the customer, each converted
     * to the limit currency. Null if an account's currency has no exchange rate (rateMissing set).
     */
    private BigDecimal getTotalCifBalance(CustomerLimitDTO request, String finMnemonic, String limitCcy) {
        String customerId = request.getCustomerId();
        BigDecimal total = BigDecimal.ZERO;
        List<String> accountIds = repository.getCustomerAccountIds(finMnemonic, customerId);
        int index = 0;
        for (String accountId : accountIds) {
            index++;
            BigDecimal balance = getAccountBalance(request, finMnemonic, accountId, limitCcy);
            if (balance == null) {
                logger.error(LOG_PREFIX + "getTotalCifBalance account=" + accountId + " no exchange rate to "
                        + limitCcy);
                return null;
            }
            total = total.add(balance);
            logger.info(LOG_PREFIX + "getTotalCifBalance [" + index + "/" + accountIds.size() + "] account="
                    + accountId + " balance=" + balance + " runningTotal=" + total);
        }
        logger.info(LOG_PREFIX + "getTotalCifBalance customer=" + customerId + " accounts=" + accountIds.size()
                + " total=" + total);
        return total;
    }

    /**
     * Working balance of one account in the limit currency; 0 if the account cannot be read,
     * null if its currency has no exchange rate.
     */
    private BigDecimal getAccountBalance(CustomerLimitDTO request, String finMnemonic, String accountId,
            String limitCcy) {
        AccountRecord accountRec = repository.readAccount(finMnemonic, accountId);
        if (accountRec == null) {
            logger.info(LOG_PREFIX + "getAccountBalance account=" + accountId + " not readable, balance=0");
            return BigDecimal.ZERO;
        }
        BigDecimal balance = CustomerLimitUtilizationUpdater.toAmount(accountRec.getWorkingBalance().getValue());
        String accountCcy = accountRec.getCurrency().getValue();
        BigDecimal converted = currencyService.convert(balance, accountCcy, limitCcy);
        if (converted == null) {
            request.setRateMissing(accountCcy);
            return null;
        }
        logger.info(LOG_PREFIX + "getAccountBalance account=" + accountId + " balance=" + balance + " " + accountCcy
                + " inLimitCcy=" + converted + " " + limitCcy);
        return converted;
    }
}
