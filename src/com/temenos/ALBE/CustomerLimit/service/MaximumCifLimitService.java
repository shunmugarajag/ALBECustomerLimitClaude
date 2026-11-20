package com.temenos.ALBE.CustomerLimit.service;

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
 *               limit not defined. Exchange rate is 1 (LCY only).
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class MaximumCifLimitService {

    private static final Logger logger = LoggerFactory.getLogger("L3API");
    private static final String LOG_PREFIX = "MaximumCifLimitService:";

    /** Exchange rate is fixed at 1 until currency conversion is confirmed. */
    private static final BigDecimal EXCHANGE_RATE = BigDecimal.ONE;

    /** HLSD override text: Overall credit/debit exceed for Customer <customer id> by <amount difference>. */
    public static final String MSG_LIMIT_EXCEEDED = "Overall credit/debit exceed for Customer %s by %s";

    private final CustomerLimitRepository repository;
    private final CustomerLimitReader limitReader;

    public MaximumCifLimitService(CustomerLimitRepository repository) {
        this.repository = repository;
        this.limitReader = new CustomerLimitReader(repository);
    }

    /** Maximum CIF balance check; records a breach with the HLSD message and amount difference. */
    public void validate(CustomerLimitDTO request) {
        String customerId = request.getCustomerId();
        logger.info(LOG_PREFIX + "validate START " + request);

        if (customerId == null || customerId.trim().isEmpty()) {
            logger.info(LOG_PREFIX + "validate SKIP customer id missing");
            return;
        }
        BigDecimal transactionAmount = request.getCreditAmount().multiply(EXCHANGE_RATE);
        if (transactionAmount.signum() <= 0) {
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
            request.addUndefinedLimit("MAX.CIF.BALANCE");
            return;
        }

        // Step 2: total working balance of all the customer's accounts.
        String finMnemonic = repository.getFinancialMnemonic();
        BigDecimal totalBalance = getTotalCifBalance(finMnemonic, customerId);

        // Step 3: compare total + credit amount with the limit (equal to the limit is allowed).
        BigDecimal projectedBalance = totalBalance.add(transactionAmount);
        logger.info(LOG_PREFIX + "validate customer=" + customerId + " totalBalance=" + totalBalance
                + " txnAmount=" + transactionAmount + " projected=" + projectedBalance + " maxLimit=" + maximumCifLimit);

        if (projectedBalance.compareTo(maximumCifLimit) > 0) {
            BigDecimal difference = projectedBalance.subtract(maximumCifLimit);
            logger.info(LOG_PREFIX + "validate BREACH customer=" + customerId + " difference=" + difference);
            request.addBreach(CustomerLimitOverride.MAX_CIF_BAL_EXCEED,
                    String.format(MSG_LIMIT_EXCEEDED, customerId, difference.toPlainString()), difference);
        } else {
            logger.info(LOG_PREFIX + "validate OK customer=" + customerId + " headroom="
                    + maximumCifLimit.subtract(projectedBalance));
        }
        logger.info(LOG_PREFIX + "validate END " + request);
    }

    /** Sums the working balances of every account belonging to the customer. */
    private BigDecimal getTotalCifBalance(String finMnemonic, String customerId) {
        BigDecimal total = BigDecimal.ZERO;
        List<String> accountIds = repository.getCustomerAccountIds(finMnemonic, customerId);
        int index = 0;
        for (String accountId : accountIds) {
            index++;
            BigDecimal balance = getAccountBalance(finMnemonic, accountId).multiply(EXCHANGE_RATE);
            total = total.add(balance);
            logger.info(LOG_PREFIX + "getTotalCifBalance [" + index + "/" + accountIds.size() + "] account="
                    + accountId + " balance=" + balance + " runningTotal=" + total);
        }
        logger.info(LOG_PREFIX + "getTotalCifBalance customer=" + customerId + " accounts=" + accountIds.size()
                + " total=" + total);
        return total;
    }

    /** Working balance of one account; 0 if the account cannot be read. */
    private BigDecimal getAccountBalance(String finMnemonic, String accountId) {
        AccountRecord accountRec = repository.readAccount(finMnemonic, accountId);
        if (accountRec == null) {
            logger.info(LOG_PREFIX + "getAccountBalance account=" + accountId + " not readable, balance=0");
            return BigDecimal.ZERO;
        }
        return CustomerLimitUtilizationUpdater.toAmount(accountRec.getWorkingBalance().getValue());
    }
}
