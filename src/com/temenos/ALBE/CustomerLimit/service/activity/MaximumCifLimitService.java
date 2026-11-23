package com.temenos.ALBE.CustomerLimit.service.activity;

import java.math.BigDecimal;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.model.Limits;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.service.CustomerLimitReader;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
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
 * 22-Nov-2026   Development      Blank CIF.LIMIT CCY = error (limitCcyMissing)
 *-----------------------------------------------------------------------------
 */
public class MaximumCifLimitService {
    private static final String CLASS_NAME = "MaximumCifLimitService";

    private final CustomerLimitRepository repository;
    private final CustomerLimitReader limitReader;
    private final CurrencyConversionService currencyService;

    public MaximumCifLimitService(CustomerLimitRepository repository) {
        this.repository = repository;
        this.limitReader = new CustomerLimitReader(repository);
        this.currencyService = new CurrencyConversionService(repository);
    }

    /**
     * Maximum CIF balance check; records a breach with the HLSD message and amount difference.
     * The request has been checked by CustomerLimitRequestValidator (customer, credit > 0).
     */
    public void validate(CustomerLimitDTO request) {
        String customerId = request.getCustomerId();
        LogUtil.info(CLASS_NAME, "validate", "START", request);

        // Step 1: customer's maximum balance limit.
        Limits limits = limitReader.getCustomerLimits(customerId);
        if (limits == null) {
            LogUtil.info(CLASS_NAME, "validate", "END", "customer=" + customerId + " no EB.ALBE.CIF.LIMIT record");
            request.setLimitMissing(true);
            return;
        }
        if (ValidationUtil.isBlank(limits.ccy)) {
            LogUtil.error(CLASS_NAME, "validate", "NO CCY", "customer=" + customerId
                    + " EB.ALBE.CIF.LIMIT CCY is blank - error, transaction blocked");
            request.setLimitCcyMissing(true);
            return;
        }
        BigDecimal maximumCifLimit = limits.maxCifBalance;
        if (maximumCifLimit == null) {
            LogUtil.info(CLASS_NAME, "validate", "NOT DEFINED", "MAX.CIF.BALANCE is blank for customer=" + customerId);
            request.addUndefinedLimit(Constants.KEY_MAX_CIF_BALANCE);
            return;
        }

        // Credit amount in the limit currency.
        if (!currencyService.convertRequest(request, limits.ccy)) {
            LogUtil.info(CLASS_NAME, "validate", "END", "customer=" + customerId + " no exchange rate for the credit");
            return;
        }
        BigDecimal transactionAmount = request.getCreditAmount();

        // Step 2: total working balance of all the customer's accounts.
        String finMnemonic = repository.getFinancialMnemonic();
        BigDecimal totalBalance = getTotalCifBalance(request, finMnemonic, limits.ccy);
        if (totalBalance == null) {
            LogUtil.info(CLASS_NAME, "validate", "END", "customer=" + customerId
                    + " no exchange rate for account currency "
                    + request.getRateMissingCurrency());
            return;
        }

        // Step 3: compare total + credit amount with the limit (equal to the limit is allowed).
        BigDecimal projectedBalance = totalBalance.add(transactionAmount);
        LogUtil.info(CLASS_NAME, "validate", "CALC", "customer=" + customerId + " totalBalance=" + totalBalance
                + " txnAmount=" + transactionAmount + " projected=" + projectedBalance + " maxLimit="
                        + maximumCifLimit);

        if (projectedBalance.compareTo(maximumCifLimit) > 0) {
            BigDecimal difference = projectedBalance.subtract(maximumCifLimit);
            LogUtil.info(CLASS_NAME, "validate", "BREACH", "customer=" + customerId + " difference=" + difference);
            request.addBreach(Constants.OVR_MAX_CIF_BAL_EXCEED,
                    String.format(Constants.MSG_MAX_CIF_EXCEEDED, customerId, difference.toPlainString()), difference);
        } else {
            LogUtil.info(CLASS_NAME, "validate", "OK", "customer=" + customerId + " headroom="
                    + maximumCifLimit.subtract(projectedBalance));
        }
        LogUtil.info(CLASS_NAME, "validate", "END", request);
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
                LogUtil.error(CLASS_NAME, "getTotalCifBalance", "NO RATE", "account=" + accountId
                        + " no exchange rate to "
                        + limitCcy);
                return null;
            }
            total = total.add(balance);
            LogUtil.info(CLASS_NAME, "getTotalCifBalance", "CALC", "[" + index + "/" + accountIds.size() + "] account="
                    + accountId + " balance=" + balance + " runningTotal=" + total);
        }
        LogUtil.info(CLASS_NAME, "getTotalCifBalance", "CALC", "customer=" + customerId + " accounts="
                + accountIds.size()
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
            LogUtil.info(CLASS_NAME, "getAccountBalance", "NOT FOUND", "account=" + accountId
                    + " not readable, balance=0");
            return BigDecimal.ZERO;
        }
        BigDecimal balance = ValidationUtil.toAmount(accountRec.getWorkingBalance().getValue());
        String accountCcy = accountRec.getCurrency().getValue();
        BigDecimal converted = currencyService.convert(balance, accountCcy, limitCcy);
        if (converted == null) {
            request.setRateMissing(accountCcy);
            return null;
        }
        LogUtil.info(CLASS_NAME, "getAccountBalance", "CALC", "account=" + accountId + " balance=" + balance + " "
                + accountCcy
                + " inLimitCcy=" + converted + " " + limitCcy);
        return converted;
    }
}
