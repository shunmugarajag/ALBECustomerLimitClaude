package com.temenos.ALBE.CustomerLimit.service.activity;

import java.math.BigDecimal;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.model.Limits;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.t24.api.records.account.AccountRecord;

/*-----------------------------------------------------------------------------
 * Description : Maximum overall CIF balance (HLSD 7.2.1 - 3), credits only. Called by
 *               CustomerLimitEngine with the limits already read and the credit already in the
 *               limit currency.
 *
 *               CUSTOMER.ACCOUNT <customer> -> each ACCOUNT WORKING.BALANCE (in the limit currency)
 *               total + credit ABOVE MAXIMUM.OVERALL.CIF.AMOUNT -> breach by the difference
 *
 *               blank MAXIMUM.OVERALL.CIF.AMOUNT -> "limit not defined" (override)
 *               no CUSTOMER.ACCOUNT record        -> customerAccountMissing (ERROR, blocked)
 *               account in another currency, no rate -> rateMissing (override), not checked (E1)
 *               account not readable             -> balance 0 (logged)
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 * 26-Nov-2026   Development      CUSTOMER.ACCOUNT empty -> error, transaction blocked (D45)
 * 26-Nov-2026   Development      Clean rewrite (D47): one check on the converted request
 *-----------------------------------------------------------------------------
 */
public class MaximumCifLimitService {
    private static final String CLASS_NAME = "MaximumCifLimitService";

    private final CustomerLimitRepository repository;
    private final CurrencyConversionService currencyService;

    public MaximumCifLimitService(CustomerLimitRepository repository) {
        this.repository = repository;
        this.currencyService = new CurrencyConversionService(repository);
    }

    /** Checks the credit against the maximum CIF balance; the result is recorded on the request. */
    public void check(CustomerLimitDTO request, Limits limits) {
        String customerId = request.getCustomerId();
        BigDecimal maximum = limits.maxCifBalance;
        if (maximum == null) {
            request.addNote("max CIF: " + Constants.CIF_FIELD_MAX_CIF_AMOUNT + " is blank -> not checked");
            request.addUndefinedLimit(Constants.CIF_FIELD_MAX_CIF_AMOUNT);
            return;
        }
        BigDecimal total = getTotalBalance(request, limits.ccy);
        if (total == null) {
            return;
        }
        BigDecimal credit = request.getCreditAmount();
        BigDecimal projected = total.add(credit);
        String calc = "max CIF: balance of all accounts " + ValidationUtil.formatAmount(total) + " + this "
                + ValidationUtil.formatAmount(credit) + " = " + ValidationUtil.formatAmount(projected);
        if (projected.compareTo(maximum) > 0) {
            BigDecimal excess = projected.subtract(maximum);
            request.addNote(calc + " > maximum " + ValidationUtil.formatAmount(maximum) + " -> EXCEEDED by "
                    + ValidationUtil.formatAmount(excess));
            request.addBreach(Constants.OVR_MAX_CIF_BAL_EXCEED, excess);
        } else {
            request.addNote(calc + " <= maximum " + ValidationUtil.formatAmount(maximum) + " -> OK");
        }
        LogUtil.info(CLASS_NAME, "check", "CALC", "customer=" + customerId + " total=" + total + " credit="
                + credit + " maximum=" + maximum);
    }

    /**
     * Working balance of every account in CUSTOMER.ACCOUNT, in the limit currency.
     * Null when there is no account (customerAccountMissing) or an account has no rate (rateMissing).
     */
    private BigDecimal getTotalBalance(CustomerLimitDTO request, String limitCcy) {
        String customerId = request.getCustomerId();
        String finMnemonic = repository.getFinancialMnemonic();
        List<String> accountIds = repository.getCustomerAccountIds(finMnemonic, customerId);
        if (accountIds.isEmpty()) {
            LogUtil.info(CLASS_NAME, "getTotalBalance", "BLOCKED", "customer=" + customerId + " no accounts in "
                    + Constants.CUSTOMER_ACCOUNT_FILE + ", raising " + Constants.ERR_CUSTOMER_ACCOUNT_MISSING);
            request.addNote("BLOCKED: no accounts in " + Constants.CUSTOMER_ACCOUNT_FILE + " for customer "
                    + customerId);
            request.setCustomerAccountMissing(true);
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (String accountId : accountIds) {
            AccountRecord accountRec = repository.readAccount(finMnemonic, accountId);
            if (accountRec == null) {
                LogUtil.info(CLASS_NAME, "getTotalBalance", "NOT FOUND", "account=" + accountId
                        + " not readable, balance 0");
                continue;
            }
            BigDecimal balance = ValidationUtil.toAmount(accountRec.getWorkingBalance().getValue());
            String accountCcy = accountRec.getCurrency().getValue();
            BigDecimal converted = currencyService.convert(balance, accountCcy, limitCcy);
            if (converted == null) {
                request.setRateMissing(accountCcy);
                request.addNote("max CIF: account " + accountId + " in " + accountCcy + ", no rate to " + limitCcy
                        + " -> not checked");
                return null;
            }
            total = total.add(converted);
            LogUtil.info(CLASS_NAME, "getTotalBalance", "CALC", "account=" + accountId + " balance=" + balance
                    + " " + accountCcy + " inLimitCcy=" + converted + " total=" + total);
        }
        return total;
    }
}
