package com.temenos.ALBE.CustomerLimit.repository;

import java.time.LocalDate;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.tafj.api.client.impl.T24Context;
import com.temenos.t24.api.records.account.AccountRecord;
import com.temenos.t24.api.records.company.CompanyRecord;
import com.temenos.t24.api.records.ebalbeciflimits.EbAlbeCifLimitsRecord;
import com.temenos.t24.api.records.ebalbegenparam.EbAlbeGenParamRecord;
import com.temenos.t24.api.records.ebalbetxndetailslimit.EbAlbeTxnDetailsLimitRecord;
import com.temenos.t24.api.records.ebalbeutilisedlimit.EbAlbeUtilisedLimitRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;
import com.temenos.t24.api.tables.ebalbeciflimits.EbAlbeCifLimitsTable;
import com.temenos.t24.api.tables.ebalbetxndetailslimit.EbAlbeTxnDetailsLimitTable;
import com.temenos.t24.api.tables.ebalbeutilisedlimit.EbAlbeUtilisedLimitTable;

/*-----------------------------------------------------------------------------
 * Description : Single place for every database read and write of the customer
 *               limit routines. No business logic here: each method reads, writes,
 *               selects or deletes one thing and logs what it did.
 *
 *               Tables / files used:
 *                 EB.ALBE.CIF.LIMITS         read, write           (id: customer id)
 *                 EB.ALBE.GEN.PARAM          read                  (id: CUSTOMER.LIMIT)
 *                 EB.ALBE.UTILISED.LIMIT     read, write           (id: customer-YYYYMMDD / customer-YYYYMM)
 *                 EB.ALBE.TXN.DETAILS.LIMIT  read, write, delete, select (id: customer-YYYYMMDD-AAA id)
 *                 ACCOUNT, CUSTOMER.ACCOUNT, COMPANY  read
 *                 Session                    T24 date (!TODAY), local currency
 *
 *               Read methods return null (or an empty list) when nothing is found;
 *               write / delete methods return true on success. Callers decide what
 *               a missing record means.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 * 21-Nov-2026   Development      T24 date and local currency; readActivity for the report
 * 22-Nov-2026   Development      One constructor for every hook (T24Context)
 * 25-Nov-2026   Development      readTxnDetails: record not found logged as INFO (normal at first input), other errors ERROR
 * 26-Nov-2026   Development      Report (enquiry) reads and selects removed (reports dropped)
 * 26-Nov-2026   Development      CUSTOMER.ACCOUNT only - ACCOUNT SELECT fallback removed (G8)
 * 26-Nov-2026   Development      readTxnDetails through the Table class: sees a write of the same commit (D46)
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitRepository {
    private static final String CLASS_NAME = "CustomerLimitRepository";

    private final DataAccess daObj;
    private final Session ssObj;
    private final EbAlbeCifLimitsTable cifLimitTable;
    private final EbAlbeUtilisedLimitTable utilisedLimitTable;
    private final EbAlbeTxnDetailsLimitTable txnDetailsTable;

    /** The hook the routine runs in; used to create new (empty) generated records. */
    private final T24Context context;
    /** Set when a utilisation / TXN.DETAILS save fails; the controller then blocks (D37). */
    private boolean saveFailed = false;

    /**
     * One constructor for every hook (ACTIVITY.API, version routine): all T24 Java
     * hooks extend T24Context.
     */
    public CustomerLimitRepository(T24Context hook) {
        this.daObj = new DataAccess(hook);
        this.ssObj = new Session(hook);
        this.cifLimitTable = new EbAlbeCifLimitsTable(hook);
        this.utilisedLimitTable = new EbAlbeUtilisedLimitTable(hook);
        this.txnDetailsTable = new EbAlbeTxnDetailsLimitTable(hook);
        this.context = hook;
        LogUtil.info(CLASS_NAME, "init", "CREATED", "for hook " + hook.getClass().getSimpleName());
    }

    /** True when a utilisation / TXN.DETAILS save failed in this request (decision D37: block). */
    public boolean isSaveFailed() {
        return saveFailed;
    }

    /** Records a save failure noticed outside the write itself (e.g. the record could not be built). */
    public void markSaveFailed(String className, String reason) {
        saveFailed = true;
        LogUtil.error(CLASS_NAME, "markSaveFailed", "FAILED", className + ": " + reason);
    }

    // =========================================================================
    // EB.ALBE.CIF.LIMITS
    // =========================================================================

    /** Reads EB.ALBE.CIF.LIMITS <customer id>; null if there is no record. */
    public EbAlbeCifLimitsRecord readCifLimit(String customerId) {
        LogUtil.detail(CLASS_NAME, "readCifLimit", "READ", Constants.CIF_LIMIT_TABLE + ">" + customerId);
        try {
            EbAlbeCifLimitsRecord cifLimitRec =
                    new EbAlbeCifLimitsRecord(daObj.getRecord(Constants.CIF_LIMIT_TABLE, customerId));
            if (cifLimitRec.toString().isEmpty()) {
                LogUtil.info(CLASS_NAME, "readCifLimit", "NOT FOUND", "empty record " + Constants.CIF_LIMIT_TABLE
                        + ">" + customerId);
                return null;
            }
            LogUtil.detail(CLASS_NAME, "readCifLimit", "FOUND", Constants.CIF_LIMIT_TABLE + ">" + customerId);
            return cifLimitRec;
        } catch (Exception e) {
            LogUtil.info(CLASS_NAME, "readCifLimit", "NOT FOUND", Constants.CIF_LIMIT_TABLE + ">" + customerId, e);
            return null;
        }
    }

    /**
     * Whether EB.ALBE.CIF.LIMITS <customer id> exists, used before creating one so an existing
     * (possibly amended) record is never overwritten.
     * @return TRUE  record exists
     *         FALSE T24 reports no such record (or the record is empty)
     *         null  the read failed for another reason: the caller must not create the record
     */
    public Boolean cifLimitExists(String customerId) {
        LogUtil.detail(CLASS_NAME, "cifLimitExists", "READ", Constants.CIF_LIMIT_TABLE + ">" + customerId);
        try {
            EbAlbeCifLimitsRecord cifLimitRec =
                    new EbAlbeCifLimitsRecord(daObj.getRecord(Constants.CIF_LIMIT_TABLE, customerId));
            boolean exists = !cifLimitRec.toString().isEmpty();
            LogUtil.info(CLASS_NAME, "cifLimitExists", exists ? "FOUND" : "NOT FOUND",
                    Constants.CIF_LIMIT_TABLE + ">" + customerId);
            return exists;
        } catch (Exception e) {
            if (isRecordNotFound(e)) {
                LogUtil.info(CLASS_NAME, "cifLimitExists", "NOT FOUND", Constants.CIF_LIMIT_TABLE + ">" + customerId);
                return Boolean.FALSE;
            }
            LogUtil.error(CLASS_NAME, "cifLimitExists", "FAILED", Constants.CIF_LIMIT_TABLE + ">" + customerId
                    + " existence unknown", e);
            return null;
        }
    }

    /** True when the exception from a read means "record not found" rather than a real failure. */
    private boolean isRecordNotFound(Exception e) {
        String text = (e.getClass().getSimpleName() + " " + e.getMessage()).toUpperCase();
        for (String marker : Constants.RECORD_NOT_FOUND_MARKERS) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /** New empty EB.ALBE.CIF.LIMITS record, to be filled and written. */
    public EbAlbeCifLimitsRecord newCifLimitRecord() {
        LogUtil.info(CLASS_NAME, "newCifLimitRecord", "NEW", Constants.CIF_LIMIT_TABLE + " record");
        return new EbAlbeCifLimitsRecord(context);
    }

    /** Writes EB.ALBE.CIF.LIMITS <customer id>. */
    public boolean writeCifLimit(String customerId, EbAlbeCifLimitsRecord cifLimitRec) {
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
            LogUtil.detail(CLASS_NAME, "readUtilisedLimit", "FOUND", Constants.UTILISED_LIMIT_TABLE + ">" + recordId
                    + " credit=" + utilRec.getUtilisedCreditLimit().getValue()
                    + " debit=" + utilRec.getUtilisedDebitLimit().getValue()
                    + " migratedCredit=" + utilRec.getUtilisedMigratedCreditLimit().getValue()
                    + " migratedDebit=" + utilRec.getUtilisedMigratedDebitLimit().getValue());
            return utilRec;
        } catch (Exception e) {
            LogUtil.detail(CLASS_NAME, "readUtilisedLimit", "NOT FOUND",
                    Constants.UTILISED_LIMIT_TABLE + ">" + recordId);
            return null;
        }
    }

    /** New empty EB.ALBE.UTILISED.LIMIT record (first transaction of the day / month). */
    public EbAlbeUtilisedLimitRecord newUtilisedLimitRecord() {
        LogUtil.info(CLASS_NAME, "newUtilisedLimitRecord", "NEW", Constants.UTILISED_LIMIT_TABLE + " record");
        return new EbAlbeUtilisedLimitRecord(context);
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
            saveFailed = true;
            return false;
        }
    }

    // =========================================================================
    // EB.ALBE.TXN.DETAILS.LIMIT
    // =========================================================================

    /**
     * Reads EB.ALBE.TXN.DETAILS.LIMIT <id>; null if there is no record.
     * Read through the generated Table class (like EB.ALBE.UTILISED.LIMIT) so a record written earlier
     * in the SAME commit is found - DataAccess.getRecord did not see it (DEV 26-Nov, FT2609332FH0:
     * counted twice).
     */
    public EbAlbeTxnDetailsLimitRecord readTxnDetails(String recordId) {
        LogUtil.detail(CLASS_NAME, "readTxnDetails", "READ", Constants.TXN_DETAILS_TABLE + ">" + recordId);
        try {
            EbAlbeTxnDetailsLimitRecord txnRec = txnDetailsTable.read(recordId);
            if (txnRec == null) {
                LogUtil.detail(CLASS_NAME, "readTxnDetails", "NOT FOUND", Constants.TXN_DETAILS_TABLE + ">" + recordId);
                return null;
            }
            LogUtil.detail(CLASS_NAME, "readTxnDetails", "FOUND", Constants.TXN_DETAILS_TABLE + ">" + recordId
                    + " flag=" + txnRec.getCreditDebitFlag().getValue() + " amount=" + txnRec.getTxnAmount().getValue());
            return txnRec;
        } catch (Exception e) {
            if (isRecordNotFound(e)) {
                // normal: first input of the activity (no earlier input to take back)
                LogUtil.detail(CLASS_NAME, "readTxnDetails", "NOT FOUND", Constants.TXN_DETAILS_TABLE + ">" + recordId);
            } else {
                // Table.read may word "not found" differently from DataAccess: logged (INFO), not an alert.
                LogUtil.info(CLASS_NAME, "readTxnDetails", "NOT READ", Constants.TXN_DETAILS_TABLE + ">" + recordId
                        + " (treated as not found)", e);
            }
            return null;
        }
    }

    /**
     * Ids of the EB.ALBE.TXN.DETAILS.LIMIT records of one customer and one counted transaction
     * and arrangement (<customer>-<YYYYMMDD>-<transactionId>-<arrangementId>), whatever the date.
     * Used only to find the counted date of a transaction reversed on a later day.
     */
    public List<String> selectTxnDetailsIdsForTransaction(String customerId, String transactionId,
            String arrangementId) {
        String selection = "WITH @ID LIKE " + customerId + Constants.TXN_ID_SEPARATOR + "..." + Constants.TXN_ID_SEPARATOR
                + transactionId + Constants.TXN_ID_SEPARATOR + arrangementId;
        LogUtil.info(CLASS_NAME, "selectTxnDetailsIdsForTransaction", "SELECT",
                Constants.TXN_DETAILS_TABLE + " [" + selection + "]");
        try {
            List<String> ids = daObj.selectRecords("", Constants.TXN_DETAILS_TABLE, "", selection);
            List<String> result = ids == null ? List.of() : ids;
            LogUtil.info(CLASS_NAME, "selectTxnDetailsIdsForTransaction", "FOUND", result.size() + " records");
            return result;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "selectTxnDetailsIdsForTransaction", "FAILED", Constants.TXN_DETAILS_TABLE, e);
            return List.of();
        }
    }

    /** New empty EB.ALBE.TXN.DETAILS.LIMIT record, to be filled and written. */
    public EbAlbeTxnDetailsLimitRecord newTxnDetailsRecord() {
        LogUtil.info(CLASS_NAME, "newTxnDetailsRecord", "NEW", Constants.TXN_DETAILS_TABLE + " record");
        return new EbAlbeTxnDetailsLimitRecord(context);
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
            saveFailed = true;
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
            saveFailed = true;
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
    // T24 session: date and local currency
    // =========================================================================

    /**
     * Current T24 date (common variable !TODAY), not the server clock; null if it cannot be read.
     * DEV runs on a T24 date far from the server clock, so the clock must not be used for counting.
     */
    public LocalDate getT24Today() {
        try {
            String today = ssObj.getCurrentVariable(Constants.T24_TODAY_VARIABLE);
            LocalDate date = ValidationUtil.toDate(today);
            LogUtil.info(CLASS_NAME, "getT24Today", date == null ? "INVALID" : "FOUND", Constants.T24_TODAY_VARIABLE
                    + "=" + today);
            return date;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "getT24Today", "FAILED", Constants.T24_TODAY_VARIABLE, e);
            return null;
        }
    }

    /** Local currency of the current company (Session); "" if it cannot be read. */
    public String getLocalCurrency() {
        try {
            String lcy = ssObj.getLocalCurrency();
            LogUtil.info(CLASS_NAME, "getLocalCurrency", "FOUND", "lcy=" + lcy);
            return lcy == null ? "" : lcy.trim();
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "getLocalCurrency", "FAILED", "local currency", e);
            return "";
        }
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
     * Account ids of the customer from CUSTOMER.ACCOUNT <customerId>; empty list when the record is
     * missing or empty (the max CIF check then blocks the transaction - no SELECT fallback).
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
        return List.of();
    }

    /** Reads ACCOUNT <id> in the given company; null if it cannot be read. */
    public AccountRecord readAccount(String finMnemonic, String accountId) {
        LogUtil.detail(CLASS_NAME, "readAccount", "READ", Constants.ACCOUNT_TABLE + ">" + accountId + " mnemonic="
                + finMnemonic);
        try {
            AccountRecord accountRec = new AccountRecord(daObj.getRecord(finMnemonic, Constants.ACCOUNT_TABLE, "",
                    accountId));
            LogUtil.detail(CLASS_NAME, "readAccount", "FOUND", Constants.ACCOUNT_TABLE + ">" + accountId
                    + " workingBalance="
                    + accountRec.getWorkingBalance().getValue());
            return accountRec;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "readAccount", "NOT FOUND", Constants.ACCOUNT_TABLE + ">" + accountId, e);
            return null;
        }
    }
}
