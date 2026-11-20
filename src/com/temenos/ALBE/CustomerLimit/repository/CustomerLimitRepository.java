package com.temenos.ALBE.CustomerLimit.repository;

import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.t24.api.hook.arrangement.ActivityLifecycle;
import com.temenos.t24.api.hook.system.RecordLifecycle;
import com.temenos.t24.api.records.account.AccountRecord;
import com.temenos.t24.api.records.company.CompanyRecord;
import com.temenos.t24.api.records.ebalbeciflimit.EbAlbeCifLimitRecord;
import com.temenos.t24.api.records.ebalbegenparam.EbAlbeGenParamRecord;
import com.temenos.t24.api.records.ebalbetxndetailslimit.EbAlbeTxnDetailsLimitRecord;
import com.temenos.t24.api.records.ebalbeutilisedlimit.EbAlbeUtilisedLimitRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;
import com.temenos.t24.api.tables.ebalbeciflimit.EbAlbeCifLimitTable;
import com.temenos.t24.api.tables.ebalbetxndetailslimit.EbAlbeTxnDetailsLimitTable;
import com.temenos.t24.api.tables.ebalbeutilisedlimit.EbAlbeUtilisedLimitTable;

/*-----------------------------------------------------------------------------
 * Description : Single place for every database read and write of the customer
 *               limit routines. No business logic here: each method reads, writes,
 *               selects or deletes one thing and logs what it did.
 *
 *               Tables / files used:
 *                 EB.ALBE.CIF.LIMIT          read, write           (id: customer id)
 *                 EB.ALBE.GEN.PARAM          read                  (id: SYSTEM)
 *                 EB.ALBE.UTILISED.LIMIT     read, write           (id: customer-YYYYMMDD / customer-YYYYMM)
 *                 EB.ALBE.TXN.DETAILS.LIMIT  read, write, delete (id: customer-AAA id)
 *                 ACCOUNT, CUSTOMER.ACCOUNT, COMPANY  read / select
 *
 *               Read methods return null (or an empty list) when nothing is found;
 *               write / delete methods return true on success. Callers decide what
 *               a missing record means.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitRepository {
    private static final String CLASS_NAME = "CustomerLimitRepository";

    private final DataAccess daObj;
    private final Session ssObj;
    private final EbAlbeCifLimitTable cifLimitTable;
    private final EbAlbeUtilisedLimitTable utilisedLimitTable;
    private final EbAlbeTxnDetailsLimitTable txnDetailsTable;

    // Exactly one of these is set; used only to create new (empty) generated records.
    private final ActivityLifecycle activityHook;
    private final RecordLifecycle recordHook;

    /** Constructor for the ACTIVITY.API routines (DailyMonthlyLimitController, MaximumCifLimitController). */
    public CustomerLimitRepository(ActivityLifecycle hook) {
        this.daObj = new DataAccess(hook);
        this.ssObj = new Session(hook);
        this.cifLimitTable = new EbAlbeCifLimitTable(hook);
        this.utilisedLimitTable = new EbAlbeUtilisedLimitTable(hook);
        this.txnDetailsTable = new EbAlbeTxnDetailsLimitTable(hook);
        this.activityHook = hook;
        this.recordHook = null;
        LogUtil.info(CLASS_NAME, "init", "CREATED", "for ACTIVITY.API hook " + hook.getClass().getSimpleName());
    }

    /** Constructor for the version routine (CustomerLimitCreationController). */
    public CustomerLimitRepository(RecordLifecycle hook) {
        this.daObj = new DataAccess(hook);
        this.ssObj = new Session(hook);
        this.cifLimitTable = new EbAlbeCifLimitTable(hook);
        this.utilisedLimitTable = new EbAlbeUtilisedLimitTable(hook);
        this.txnDetailsTable = new EbAlbeTxnDetailsLimitTable(hook);
        this.activityHook = null;
        this.recordHook = hook;
        LogUtil.info(CLASS_NAME, "init", "CREATED", "for version hook " + hook.getClass().getSimpleName());
    }

    // =========================================================================
    // EB.ALBE.CIF.LIMIT
    // =========================================================================

    /** Reads EB.ALBE.CIF.LIMIT <customer id>; null if there is no record. */
    public EbAlbeCifLimitRecord readCifLimit(String customerId) {
        LogUtil.detail(CLASS_NAME, "readCifLimit", "READ", Constants.CIF_LIMIT_TABLE + ">" + customerId);
        try {
            EbAlbeCifLimitRecord cifLimitRec =
                    new EbAlbeCifLimitRecord(daObj.getRecord(Constants.CIF_LIMIT_TABLE, customerId));
            if (cifLimitRec.toString().isEmpty()) {
                LogUtil.info(CLASS_NAME, "readCifLimit", "NOT FOUND", "empty record " + Constants.CIF_LIMIT_TABLE
                        + ">" + customerId);
                return null;
            }
            LogUtil.info(CLASS_NAME, "readCifLimit", "FOUND", Constants.CIF_LIMIT_TABLE + ">" + customerId);
            return cifLimitRec;
        } catch (Exception e) {
            LogUtil.info(CLASS_NAME, "readCifLimit", "NOT FOUND", Constants.CIF_LIMIT_TABLE + ">" + customerId, e);
            return null;
        }
    }

    /** New empty EB.ALBE.CIF.LIMIT record, to be filled and written. */
    public EbAlbeCifLimitRecord newCifLimitRecord() {
        LogUtil.info(CLASS_NAME, "newCifLimitRecord", "NEW", Constants.CIF_LIMIT_TABLE + " record");
        return activityHook != null ? new EbAlbeCifLimitRecord(activityHook) : new EbAlbeCifLimitRecord(recordHook);
    }

    /** Writes EB.ALBE.CIF.LIMIT <customer id>. */
    public boolean writeCifLimit(String customerId, EbAlbeCifLimitRecord cifLimitRec) {
        LogUtil.detail(CLASS_NAME, "writeCifLimit", "WRITE", Constants.CIF_LIMIT_TABLE + ">" + customerId);
        try {
            cifLimitTable.write(customerId, cifLimitRec);
            LogUtil.info(CLASS_NAME, "writeCifLimit", "WRITTEN", Constants.CIF_LIMIT_TABLE + ">" + customerId);
            return true;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "writeCifLimit", "FAILED", Constants.CIF_LIMIT_TABLE + ">" + customerId, e);
            return false;
        }
    }

    // =========================================================================
    // EB.ALBE.GEN.PARAM
    // =========================================================================

    /** Reads EB.ALBE.GEN.PARAM <id>; null if there is no record. */
    public EbAlbeGenParamRecord readGenParam(String paramId) {
        LogUtil.detail(CLASS_NAME, "readGenParam", "READ", Constants.GEN_PARAM_TABLE + ">" + paramId);
        try {
            EbAlbeGenParamRecord genParamRec = new EbAlbeGenParamRecord(daObj.getRecord(Constants.GEN_PARAM_TABLE,
                    paramId));
            LogUtil.info(CLASS_NAME, "readGenParam", "FOUND", Constants.GEN_PARAM_TABLE + ">" + paramId);
            return genParamRec;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "readGenParam", "NOT FOUND", Constants.GEN_PARAM_TABLE + ">" + paramId, e);
            return null;
        }
    }

    // =========================================================================
    // EB.ALBE.UTILISED.LIMIT
    // =========================================================================

    /** Reads EB.ALBE.UTILISED.LIMIT <id>; null if there is no utilisation yet. */
    public EbAlbeUtilisedLimitRecord readUtilisedLimit(String recordId) {
        LogUtil.detail(CLASS_NAME, "readUtilisedLimit", "READ", Constants.UTILISED_LIMIT_TABLE + ">" + recordId);
        try {
            EbAlbeUtilisedLimitRecord utilRec = utilisedLimitTable.read(recordId);
            LogUtil.info(CLASS_NAME, "readUtilisedLimit", "FOUND", Constants.UTILISED_LIMIT_TABLE + ">" + recordId
                    + " credit=" + utilRec.getUtilisedCreditLimit().getValue()
                    + " debit=" + utilRec.getUtilisedDebitLimit().getValue()
                    + " migratedCredit=" + utilRec.getUtilisedMigratedCreditLimit().getValue()
                    + " migratedDebit=" + utilRec.getUtilisedMigratedDebitLimit().getValue());
            return utilRec;
        } catch (Exception e) {
            LogUtil.info(CLASS_NAME, "readUtilisedLimit", "NOT FOUND", Constants.UTILISED_LIMIT_TABLE + ">" + recordId);
            return null;
        }
    }

    /** New empty EB.ALBE.UTILISED.LIMIT record (first transaction of the day / month). */
    public EbAlbeUtilisedLimitRecord newUtilisedLimitRecord() {
        LogUtil.info(CLASS_NAME, "newUtilisedLimitRecord", "NEW", Constants.UTILISED_LIMIT_TABLE + " record");
        return activityHook != null ? new EbAlbeUtilisedLimitRecord(activityHook)
                : new EbAlbeUtilisedLimitRecord(recordHook);
    }

    /** Writes EB.ALBE.UTILISED.LIMIT <id>. */
    public boolean writeUtilisedLimit(String recordId, EbAlbeUtilisedLimitRecord utilRec) {
        LogUtil.detail(CLASS_NAME, "writeUtilisedLimit", "WRITE", Constants.UTILISED_LIMIT_TABLE + ">" + recordId
                + " credit=" + utilRec.getUtilisedCreditLimit().getValue()
                + " debit=" + utilRec.getUtilisedDebitLimit().getValue());
        try {
            utilisedLimitTable.write(recordId, utilRec);
            LogUtil.info(CLASS_NAME, "writeUtilisedLimit", "WRITTEN", Constants.UTILISED_LIMIT_TABLE + ">" + recordId);
            return true;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "writeUtilisedLimit", "FAILED", Constants.UTILISED_LIMIT_TABLE + ">" + recordId,
                    e);
            return false;
        }
    }

    // =========================================================================
    // EB.ALBE.TXN.DETAILS.LIMIT
    // =========================================================================

    /** Reads EB.ALBE.TXN.DETAILS.LIMIT <id>; null if there is no record. */
    public EbAlbeTxnDetailsLimitRecord readTxnDetails(String recordId) {
        LogUtil.detail(CLASS_NAME, "readTxnDetails", "READ", Constants.TXN_DETAILS_TABLE + ">" + recordId);
        try {
            EbAlbeTxnDetailsLimitRecord txnRec =
                    new EbAlbeTxnDetailsLimitRecord(daObj.getRecord(Constants.TXN_DETAILS_TABLE, recordId));
            LogUtil.info(CLASS_NAME, "readTxnDetails", "FOUND", Constants.TXN_DETAILS_TABLE + ">" + recordId
                    + " flag=" + txnRec.getCreditDebitFlag().getValue() + " amount=" + txnRec.getTxnAmount().getValue()
                    + " systemDate=" + txnRec.getSystemDate().getValue());
            return txnRec;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "readTxnDetails", "NOT FOUND", Constants.TXN_DETAILS_TABLE + ">" + recordId, e);
            return null;
        }
    }

    /** New empty EB.ALBE.TXN.DETAILS.LIMIT record, to be filled and written. */
    public EbAlbeTxnDetailsLimitRecord newTxnDetailsRecord() {
        LogUtil.info(CLASS_NAME, "newTxnDetailsRecord", "NEW", Constants.TXN_DETAILS_TABLE + " record");
        return activityHook != null ? new EbAlbeTxnDetailsLimitRecord(activityHook)
                : new EbAlbeTxnDetailsLimitRecord(recordHook);
    }

    /** Writes EB.ALBE.TXN.DETAILS.LIMIT <id>. */
    public boolean writeTxnDetails(String recordId, EbAlbeTxnDetailsLimitRecord txnRec) {
        LogUtil.detail(CLASS_NAME, "writeTxnDetails", "WRITE", Constants.TXN_DETAILS_TABLE + ">" + recordId);
        try {
            txnDetailsTable.write(recordId, txnRec);
            LogUtil.info(CLASS_NAME, "writeTxnDetails", "WRITTEN", Constants.TXN_DETAILS_TABLE + ">" + recordId);
            return true;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "writeTxnDetails", "FAILED", Constants.TXN_DETAILS_TABLE + ">" + recordId, e);
            return false;
        }
    }

    /** Deletes EB.ALBE.TXN.DETAILS.LIMIT <id>. */
    public boolean deleteTxnDetails(String recordId) {
        LogUtil.detail(CLASS_NAME, "deleteTxnDetails", "DELETE", Constants.TXN_DETAILS_TABLE + ">" + recordId);
        try {
            txnDetailsTable.delete(recordId);
            LogUtil.info(CLASS_NAME, "deleteTxnDetails", "DELETED", Constants.TXN_DETAILS_TABLE + ">" + recordId);
            return true;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "deleteTxnDetails", "FAILED", Constants.TXN_DETAILS_TABLE + ">" + recordId, e);
            return false;
        }
    }

    // =========================================================================
    // Exchange rate
    // =========================================================================

    /**
     * Rate to convert 1 unit of fromCcy into toCcy, from the bank's exchange rate table.
     * Table and field are set in Constants.EXCH_RATE_TABLE / EXCH_RATE_FIELD.
     * @return the rate, or null if the table is not configured or no rate is found
     */
    public java.math.BigDecimal readExchangeRate(String fromCcy, String toCcy) {
        LogUtil.detail(CLASS_NAME, "readExchangeRate", "READ", Constants.EXCH_RATE_TABLE + " field="
                + Constants.EXCH_RATE_FIELD + " from=" + fromCcy + " to=" + toCcy);
        if (Constants.EXCH_RATE_TABLE.isEmpty() || Constants.EXCH_RATE_FIELD.isEmpty()) {
            LogUtil.error(CLASS_NAME, "readExchangeRate", "NOT CONFIGURED", "set Constants.EXCH_RATE_TABLE and "
                    + "EXCH_RATE_FIELD and read the rate here (from=" + fromCcy + " to=" + toCcy + ")");
            return null;
        }
        // To be completed when the exchange rate table is confirmed: read the record for
        // fromCcy / toCcy with daObj.getRecord(Constants.EXCH_RATE_TABLE, <record id>),
        // wrap it in the table's generated record class, and return the EXCH_RATE_FIELD value.
        LogUtil.error(CLASS_NAME, "readExchangeRate", "NOT CONFIGURED", "table " + Constants.EXCH_RATE_TABLE
                + " configured but the read is not implemented yet");
        return null;
    }

    // =========================================================================
    // COMPANY, CUSTOMER.ACCOUNT, ACCOUNT
    // =========================================================================

    /** Financial mnemonic of the current company (needed to read ACCOUNT); "" if it cannot be read. */
    public String getFinancialMnemonic() {
        String companyId = "";
        try {
            companyId = ssObj.getCompanyId();
            LogUtil.detail(CLASS_NAME, "getFinancialMnemonic", "READ", Constants.COMPANY_TABLE + ">" + companyId);
            CompanyRecord companyRec = new CompanyRecord(daObj.getRecord(Constants.COMPANY_TABLE, companyId));
            String finMnemonic = companyRec.getFinancialMne().getValue();
            LogUtil.info(CLASS_NAME, "getFinancialMnemonic", "FOUND", "company=" + companyId + " mnemonic="
                    + finMnemonic);
            return finMnemonic;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "getFinancialMnemonic", "FAILED", "company=" + companyId, e);
            return "";
        }
    }

    /**
     * Account ids of the customer: CUSTOMER.ACCOUNT concat file first, then a SELECT on
     * ACCOUNT if the concat file gives nothing. Empty list if none.
     */
    public List<String> getCustomerAccountIds(String finMnemonic, String customerId) {
        LogUtil.detail(CLASS_NAME, "getCustomerAccountIds", "READ", Constants.CUSTOMER_ACCOUNT_FILE + ">" + customerId);
        try {
            List<String> accountIds = daObj.getConcatValues(Constants.CUSTOMER_ACCOUNT_FILE, customerId);
            if (accountIds != null && !accountIds.isEmpty()) {
                LogUtil.info(CLASS_NAME, "getCustomerAccountIds", "FOUND", "in " + Constants.CUSTOMER_ACCOUNT_FILE + " "
                        + accountIds.size() + " " + accountIds);
                return accountIds;
            }
            LogUtil.info(CLASS_NAME, "getCustomerAccountIds", "NOT FOUND", Constants.CUSTOMER_ACCOUNT_FILE
                    + " empty for " + customerId);
        } catch (Exception e) {
            LogUtil.info(CLASS_NAME, "getCustomerAccountIds", "NOT FOUND", "no " + Constants.CUSTOMER_ACCOUNT_FILE
                    + " entry for " + customerId);
        }

        String selection = "WITH CUSTOMER EQ " + customerId;
        LogUtil.info(CLASS_NAME, "getCustomerAccountIds", "SELECT", "fallback SELECT " + Constants.ACCOUNT_TABLE
                + " mnemonic=" + finMnemonic
                + " [" + selection + "]");
        try {
            List<String> accountIds = daObj.selectRecords(finMnemonic, Constants.ACCOUNT_TABLE, "", selection);
            List<String> result = accountIds == null ? List.of() : accountIds;
            LogUtil.info(CLASS_NAME, "getCustomerAccountIds", "FOUND", "by SELECT " + result.size() + " " + result);
            return result;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "getCustomerAccountIds", "FAILED", "SELECT for " + customerId, e);
            return List.of();
        }
    }

    /** Reads ACCOUNT <id> in the given company; null if it cannot be read. */
    public AccountRecord readAccount(String finMnemonic, String accountId) {
        LogUtil.detail(CLASS_NAME, "readAccount", "READ", Constants.ACCOUNT_TABLE + ">" + accountId + " mnemonic="
                + finMnemonic);
        try {
            AccountRecord accountRec = new AccountRecord(daObj.getRecord(finMnemonic, Constants.ACCOUNT_TABLE, "",
                    accountId));
            LogUtil.info(CLASS_NAME, "readAccount", "FOUND", Constants.ACCOUNT_TABLE + ">" + accountId
                    + " workingBalance="
                    + accountRec.getWorkingBalance().getValue());
            return accountRec;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "readAccount", "NOT FOUND", Constants.ACCOUNT_TABLE + ">" + accountId, e);
            return null;
        }
    }
}
