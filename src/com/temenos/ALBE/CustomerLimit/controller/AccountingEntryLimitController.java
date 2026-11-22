package com.temenos.ALBE.CustomerLimit.controller;

import java.util.ArrayList;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.service.CustomerLimitEngine;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.ALBE.CustomerLimit.view.CustomerLimitOverride;
import com.temenos.t24.api.hook.accounting.AccountingEntry;
import com.temenos.t24.api.records.stmtentry.StmtEntryRecord;

/*-----------------------------------------------------------------------------
 * Attached as : ACCOUNT.PARAMETER ACCT.NAU.SUBRTN (raiseOverrides) and
 *               ACCOUNTING.SUBRTN (exportEntries)
 * Attached to : every transaction that posts to an account - AA, FT, TELLER, standing orders,
 *               payments, ... (no dependency on the application)
 * Description : Customer limit management from the accounting entries (HLSD 7.2.1 - 2 and 3).
 *               Each customer-account entry is turned into transaction info
 *               (StatementEntryRequestBuilder) and given to CustomerLimitEngine:
 *               raiseOverrides  input of any transaction: daily / monthly and maximum CIF check,
 *                               returns the acceptable overrides
 *               exportEntries   accounting of any transaction: count (VAL, VALAUT, ADD, ADD.AUT,
 *                               CHG) or take back (DEL, REV, RSAO); other action types nothing
 *               Never blocks a transaction on a technical failure (logged).
 *               No business rules and no DB access here.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 22-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class AccountingEntryLimitController extends AccountingEntry {
    private static final String CLASS_NAME = "AccountingEntryLimitController";

    /** ACCT.NAU.SUBRTN - input phase: limit checks; the overrides go back to T24 as a list. */
    @Override
    public List<String> raiseOverrides(String entryType, String actionType, String forward, StmtEntryRecord entry) {
        List<String> overrides = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        try {
            if (ValidationUtil.contains(Constants.STMT_REVERSE_ACTIONS, ValidationUtil.nvl(actionType).trim())) {
                return overrides;
            }
            CustomerLimitRepository repository = new CustomerLimitRepository(this);
            CustomerLimitDTO transaction = StatementEntryRequestBuilder.build(entryType, entry, false, repository);
            if (transaction == null) {
                return overrides;
            }
            for (CustomerLimitDTO result : new CustomerLimitEngine(repository).checkAll(transaction)) {
                overrides.addAll(CustomerLimitOverride.texts(result));
            }
            LogUtil.info(CLASS_NAME, "raiseOverrides", "END", transaction.logKey() + " action=" + actionType
                    + " overrides=" + overrides.size() + " elapsedMs=" + (System.currentTimeMillis() - startTime));
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "raiseOverrides", "ERROR", "action=" + actionType, e);
        }
        return overrides;
    }

    /** ACCOUNTING.SUBRTN - accounting phase: count the entry or take it back. */
    @Override
    public void exportEntries(String entryType, String actionType, String statementId, StmtEntryRecord entry) {
        long startTime = System.currentTimeMillis();
        String action = ValidationUtil.nvl(actionType).trim();
        try {
            boolean count = ValidationUtil.contains(Constants.STMT_COUNT_ACTIONS, action);
            boolean reverse = ValidationUtil.contains(Constants.STMT_REVERSE_ACTIONS, action);
            if (!count && !reverse) {
                LogUtil.detail(CLASS_NAME, "exportEntries", "SKIP", "action=" + action + " entry=" + statementId);
                return;
            }
            CustomerLimitRepository repository = new CustomerLimitRepository(this);
            CustomerLimitEngine engine = new CustomerLimitEngine(repository);
            if (count) {
                CustomerLimitDTO transaction = StatementEntryRequestBuilder.build(entryType, entry, false, repository);
                if (transaction != null) {
                    engine.count(transaction, entry.getAccountNumber().getValue(), entry.getValueDate().getValue());
                }
            } else {
                // A reversing entry has the opposite sign of the original: look for the original side
                // first, then the entry's own side (e.g. a delete that repeats the original entry).
                CustomerLimitDTO original = StatementEntryRequestBuilder.build(entryType, entry, true, repository);
                if (original != null && engine.reverse(original, false) == 0) {
                    engine.reverse(StatementEntryRequestBuilder.build(entryType, entry, false, repository), false);
                }
            }
            LogUtil.info(CLASS_NAME, "exportEntries", "END", "action=" + action + " entry=" + statementId
                    + " elapsedMs=" + (System.currentTimeMillis() - startTime));
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "exportEntries", "ERROR", "action=" + action + " entry=" + statementId, e);
        }
    }
}
