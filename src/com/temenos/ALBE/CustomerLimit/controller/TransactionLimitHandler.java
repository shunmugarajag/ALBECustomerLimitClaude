package com.temenos.ALBE.CustomerLimit.controller;

import java.time.LocalDate;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.model.TransactionSide;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.service.CustomerLimitEngine;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.ALBE.CustomerLimit.view.CustomerLimitOverride;
import com.temenos.t24.api.records.account.AccountRecord;

/*-----------------------------------------------------------------------------
 * Description : Common flow of the FUNDS.TRANSFER and TELLER version routines
 *               (FundsTransferLimitController, TellerLimitController). Works on the
 *               transaction sides read by TransactionSideReader:
 *
 *                 validate  every customer side: daily / monthly check (debit and credit) and
 *                           maximum CIF check (credit only); overrides on the side's amount field
 *                 update    utilisation by function / record status (actionFor):
 *                             input (I)                    count (earlier input of the same day reversed)
 *                             delete (D)                   take back
 *                             authorise a reversal (A, R*) take back (fallback: T24 date if not found)
 *                             authorise, reverse input     nothing
 *
 *               A side is a customer side when the transaction or its ACCOUNT gives a customer;
 *               internal / cash accounts are skipped. Counted transaction id:
 *               the FT / TT id, so EB.ALBE.TXN.DETAILS.LIMIT id <customer>-<YYYYMMDD>-FT...;
 *               own-account transfer (same customer on both sides) not checked or counted.
 *               No DB access here (through the repository) and no business rules (services).
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 21-Nov-2026   Development      Initial Version
 * 22-Nov-2026   Development      Check / count / reverse through CustomerLimitEngine; common key
 * 22-Nov-2026   Development      Key without .CR/.DR; own-account transfers skipped
 *-----------------------------------------------------------------------------
 */
public class TransactionLimitHandler {
    private static final String CLASS_NAME = "TransactionLimitHandler";

    private final CustomerLimitRepository repository;
    private String finMnemonic;

    public TransactionLimitHandler(CustomerLimitRepository repository) {
        this.repository = repository;
    }

    /**
     * Input check of every customer side (daily / monthly and maximum CIF through
     * CustomerLimitEngine); overrides on the side's amount field.
     * @return number of overrides raised
     */
    public int validate(String transactionId, List<TransactionSide> sides, LocalDate countedDate) {
        int raised = 0;
        if (isOwnTransfer(sides)) {
            return raised;
        }
        CustomerLimitEngine engine = new CustomerLimitEngine(repository);
        for (TransactionSide side : sides) {
            CustomerLimitDTO request = toRequest(transactionId, side, countedDate);
            if (request == null) {
                continue;
            }
            for (CustomerLimitDTO result : engine.checkAll(request)) {
                raised += CustomerLimitOverride.raiseFor(side.amountField, result);
            }
        }
        LogUtil.info(CLASS_NAME, "validate", "END", "txn=" + transactionId + " overridesRaised=" + raised);
        return raised;
    }

    /** Utilisation of every customer side, by function and record status (see actionFor). */
    public void update(String transactionId, List<TransactionSide> sides, String function, String recordStatus,
            LocalDate countedDate) {
        String action = actionFor(function, recordStatus);
        LogUtil.info(CLASS_NAME, "update", "ACTION", "txn=" + transactionId + " function=" + function + " status="
                + recordStatus + " action=" + action);
        if (Constants.UTIL_ACTION_NONE.equals(action) || isOwnTransfer(sides)) {
            return;
        }
        CustomerLimitEngine engine = new CustomerLimitEngine(repository);
        for (TransactionSide side : sides) {
            CustomerLimitDTO request = toRequest(transactionId, side, countedDate);
            if (request == null) {
                continue;
            }
            if (Constants.UTIL_ACTION_COUNT.equals(action)) {
                engine.count(request, side.accountNo, side.valueDate);
            } else {
                engine.reverse(request, Constants.UTIL_ACTION_REVERSE.equals(action));
            }
        }
    }

    /**
     * What happens with the utilisation: input = COUNT, delete = REVERSE.DELETE, authorising a
     * reversal = REVERSE, anything else (authorise, reversal input) = NONE.
     */
    public static String actionFor(String function, String recordStatus) {
        String fn = ValidationUtil.nvl(function).trim().toUpperCase();
        boolean reversal = ValidationUtil.contains(Constants.REVERSAL_STATUSES,
                ValidationUtil.nvl(recordStatus).trim().toUpperCase());
        if (Constants.FUNCTION_DELETE.equals(fn)) {
            return Constants.UTIL_ACTION_REVERSE_DELETE;
        }
        if (Constants.FUNCTION_AUTHORISE.equals(fn)) {
            return reversal ? Constants.UTIL_ACTION_REVERSE : Constants.UTIL_ACTION_NONE;
        }
        if (Constants.FUNCTION_INPUT.equals(fn) || fn.isEmpty()) {
            return reversal ? Constants.UTIL_ACTION_NONE : Constants.UTIL_ACTION_COUNT;
        }
        return Constants.UTIL_ACTION_NONE;
    }

    /** Counted date: the preferred date of the transaction (e.g. FT PROCESSING.DATE), else the T24 date. */
    public LocalDate countedDate(String preferredDate) {
        LocalDate date = ValidationUtil.toDate(preferredDate);
        if (date != null) {
            return date;
        }
        LocalDate today = repository.getT24Today();
        if (today != null) {
            return today;
        }
        LocalDate clock = LocalDate.now();
        LogUtil.error(CLASS_NAME, "countedDate", "FALLBACK", "no T24 date, server clock " + clock + " used");
        return clock;
    }

    /**
     * Request for one side; null when the side is not a customer side (internal / cash account,
     * account not found) or has no amount.
     */
    private CustomerLimitDTO toRequest(String transactionId, TransactionSide side, LocalDate countedDate) {
        if (ValidationUtil.isBlank(side.accountNo) || !ValidationUtil.isPositive(side.amount)) {
            LogUtil.info(CLASS_NAME, "toRequest", "SKIP", "side " + side.sideCode + " no account or amount");
            return null;
        }
        String customerId = ValidationUtil.isBlank(side.customerId) ? accountCustomer(side.accountNo)
                : side.customerId.trim();
        if (ValidationUtil.isBlank(customerId)) {
            LogUtil.info(CLASS_NAME, "toRequest", "SKIP", "side " + side.sideCode + " account=" + side.accountNo
                    + " has no customer (internal account)");
            return null;
        }
        CustomerLimitDTO request = new CustomerLimitDTO();
        request.setCustomerId(customerId);
        request.setTransactionId(CustomerLimitEngine.transactionKey(transactionId));
        request.setTransactionDate(countedDate);
        request.setTransactionCurrency(side.currency);
        request.setAmountLcy(side.amountLcy);
        if (side.credit) {
            request.setCreditAmount(side.amount);
        } else {
            request.setDebitAmount(side.amount);
        }
        LogUtil.info(CLASS_NAME, "toRequest", "CALC", "side " + side.sideCode + " request=" + request);
        return request;
    }

    /**
     * True when the same customer is on the debit and the credit side (own-account transfer):
     * not checked and not counted.
     */
    private boolean isOwnTransfer(List<TransactionSide> sides) {
        String debitCustomer = null;
        String creditCustomer = null;
        for (TransactionSide side : sides) {
            String customer = ValidationUtil.isBlank(side.customerId) ? accountCustomer(side.accountNo)
                    : side.customerId.trim();
            side.customerId = customer;
            if (side.credit) {
                creditCustomer = customer;
            } else {
                debitCustomer = customer;
            }
        }
        boolean own = !ValidationUtil.isBlank(debitCustomer) && debitCustomer.equals(creditCustomer);
        if (own) {
            LogUtil.info(CLASS_NAME, "isOwnTransfer", "SKIP", "customer=" + debitCustomer
                    + " on both sides - own-account transfer, not checked or counted");
        }
        return own;
    }

    /** CUSTOMER of the ACCOUNT; "" for an internal account or an account that cannot be read. */
    private String accountCustomer(String accountNo) {
        if (finMnemonic == null) {
            finMnemonic = repository.getFinancialMnemonic();
        }
        AccountRecord accountRec = repository.readAccount(finMnemonic, accountNo);
        return accountRec == null ? "" : ValidationUtil.nvl(accountRec.getCustomer().getValue()).trim();
    }
}
