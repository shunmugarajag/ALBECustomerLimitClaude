package com.temenos.ALBE.CustomerLimit.repository;

import java.util.List;

import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;
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
 *                 EB.ALBE.CIF.LIMIT          read, write, delete   (id: customer id)
 *                 EB.ALBE.GEN.PARAM          read                  (id: SYSTEM)
 *                 EB.ALBE.UTILISED.LIMIT     read, write           (id: customer-YYYYMMDD / customer-YYYYMM)
 *                 EB.ALBE.TXN.DETAILS.LIMIT  read, write, select, delete (id: customer-YYYYMMDD-AAA id)
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

    private static final Logger logger = LoggerFactory.getLogger("L3API");
    private static final String LOG_PREFIX = "CustomerLimitRepository:";

    public static final String CIF_LIMIT_TABLE = "EB.ALBE.CIF.LIMIT";
    public static final String GEN_PARAM_TABLE = "EB.ALBE.GEN.PARAM";
    public static final String UTILISED_LIMIT_TABLE = "EB.ALBE.UTILISED.LIMIT";
    public static final String TXN_DETAILS_TABLE = "EB.ALBE.TXN.DETAILS.LIMIT";
    private static final String ACCOUNT_TABLE = "ACCOUNT";
    private static final String CUSTOMER_ACCOUNT_FILE = "CUSTOMER.ACCOUNT";
    private static final String COMPANY_TABLE = "COMPANY";

    private final DataAccess daObj;
    private final Session ssObj;
    private final EbAlbeCifLimitTable cifLimitTable;
    private final EbAlbeUtilisedLimitTable utilisedLimitTable;
    private final EbAlbeTxnDetailsLimitTable txnDetailsTable;

    // Exactly one of these is set; used only to create new (empty) generated records.
    private final ActivityLifecycle activityHook;
    private final RecordLifecycle recordHook;

    /** Constructor for the ACTIVITY.API routines (DailyMonthlyLimitValidator, MaximumCifLimitValidator). */
    public CustomerLimitRepository(ActivityLifecycle hook) {
        this.daObj = new DataAccess(hook);
        this.ssObj = new Session(hook);
        this.cifLimitTable = new EbAlbeCifLimitTable(hook);
        this.utilisedLimitTable = new EbAlbeUtilisedLimitTable(hook);
        this.txnDetailsTable = new EbAlbeTxnDetailsLimitTable(hook);
        this.activityHook = hook;
        this.recordHook = null;
        logger.info(LOG_PREFIX + "created for ACTIVITY.API hook " + hook.getClass().getSimpleName());
    }

    /** Constructor for the version routine (CustomerLimitCreation). */
    public CustomerLimitRepository(RecordLifecycle hook) {
        this.daObj = new DataAccess(hook);
        this.ssObj = new Session(hook);
        this.cifLimitTable = new EbAlbeCifLimitTable(hook);
        this.utilisedLimitTable = new EbAlbeUtilisedLimitTable(hook);
        this.txnDetailsTable = new EbAlbeTxnDetailsLimitTable(hook);
        this.activityHook = null;
        this.recordHook = hook;
        logger.info(LOG_PREFIX + "created for version hook " + hook.getClass().getSimpleName());
    }

    // =========================================================================
    // EB.ALBE.CIF.LIMIT
    // =========================================================================

    /** Reads EB.ALBE.CIF.LIMIT <customer id>; null if there is no record. */
    public EbAlbeCifLimitRecord readCifLimit(String customerId) {
        logger.info(LOG_PREFIX + "readCifLimit READ " + CIF_LIMIT_TABLE + ">" + customerId);
        try {
            EbAlbeCifLimitRecord cifLimitRec =
                    new EbAlbeCifLimitRecord(daObj.getRecord(CIF_LIMIT_TABLE, customerId));
            if (cifLimitRec.toString().isEmpty()) {
                logger.info(LOG_PREFIX + "readCifLimit EMPTY " + CIF_LIMIT_TABLE + ">" + customerId);
                return null;
            }
            logger.info(LOG_PREFIX + "readCifLimit FOUND " + CIF_LIMIT_TABLE + ">" + customerId);
            return cifLimitRec;
        } catch (Exception e) {
            logger.info(LOG_PREFIX + "readCifLimit NOT FOUND " + CIF_LIMIT_TABLE + ">" + customerId + " "
                    + e.getMessage());
            return null;
        }
    }

    /** New empty EB.ALBE.CIF.LIMIT record, to be filled and written. */
    public EbAlbeCifLimitRecord newCifLimitRecord() {
        logger.info(LOG_PREFIX + "newCifLimitRecord NEW " + CIF_LIMIT_TABLE + " record");
        return activityHook != null ? new EbAlbeCifLimitRecord(activityHook) : new EbAlbeCifLimitRecord(recordHook);
    }

    /** Writes EB.ALBE.CIF.LIMIT <customer id>. */
    public boolean writeCifLimit(String customerId, EbAlbeCifLimitRecord cifLimitRec) {
        logger.info(LOG_PREFIX + "writeCifLimit WRITE " + CIF_LIMIT_TABLE + ">" + customerId);
        try {
            cifLimitTable.write(customerId, cifLimitRec);
            logger.info(LOG_PREFIX + "writeCifLimit WRITTEN " + CIF_LIMIT_TABLE + ">" + customerId);
            return true;
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "writeCifLimit FAILED " + CIF_LIMIT_TABLE + ">" + customerId + " "
                    + e.getMessage());
            return false;
        }
    }

    /** Deletes EB.ALBE.CIF.LIMIT <customer id>. */
    public boolean deleteCifLimit(String customerId) {
        logger.info(LOG_PREFIX + "deleteCifLimit DELETE " + CIF_LIMIT_TABLE + ">" + customerId);
        try {
            cifLimitTable.delete(customerId);
            logger.info(LOG_PREFIX + "deleteCifLimit DELETED " + CIF_LIMIT_TABLE + ">" + customerId);
            return true;
        } catch (Exception e) {
            logger.info(LOG_PREFIX + "deleteCifLimit NOT DELETED (no record?) " + CIF_LIMIT_TABLE + ">" + customerId
                    + " " + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // EB.ALBE.GEN.PARAM
    // =========================================================================

    /** Reads EB.ALBE.GEN.PARAM <id>; null if there is no record. */
    public EbAlbeGenParamRecord readGenParam(String paramId) {
        logger.info(LOG_PREFIX + "readGenParam READ " + GEN_PARAM_TABLE + ">" + paramId);
        try {
            EbAlbeGenParamRecord genParamRec = new EbAlbeGenParamRecord(daObj.getRecord(GEN_PARAM_TABLE, paramId));
            logger.info(LOG_PREFIX + "readGenParam FOUND " + GEN_PARAM_TABLE + ">" + paramId);
            return genParamRec;
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "readGenParam NOT FOUND " + GEN_PARAM_TABLE + ">" + paramId + " "
                    + e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // EB.ALBE.UTILISED.LIMIT
    // =========================================================================

    /** Reads EB.ALBE.UTILISED.LIMIT <id>; null if there is no utilisation yet. */
    public EbAlbeUtilisedLimitRecord readUtilisedLimit(String recordId) {
        logger.info(LOG_PREFIX + "readUtilisedLimit READ " + UTILISED_LIMIT_TABLE + ">" + recordId);
        try {
            EbAlbeUtilisedLimitRecord utilRec = utilisedLimitTable.read(recordId);
            logger.info(LOG_PREFIX + "readUtilisedLimit FOUND " + UTILISED_LIMIT_TABLE + ">" + recordId
                    + " credit=" + utilRec.getUtilisedCreditLimit().getValue()
                    + " debit=" + utilRec.getUtilisedDebitLimit().getValue()
                    + " migratedCredit=" + utilRec.getUtilisedMigratedCreditLimit().getValue()
                    + " migratedDebit=" + utilRec.getUtilisedMigratedDebitLimit().getValue());
            return utilRec;
        } catch (Exception e) {
            logger.info(LOG_PREFIX + "readUtilisedLimit NOT FOUND " + UTILISED_LIMIT_TABLE + ">" + recordId);
            return null;
        }
    }

    /** New empty EB.ALBE.UTILISED.LIMIT record (first transaction of the day / month). */
    public EbAlbeUtilisedLimitRecord newUtilisedLimitRecord() {
        logger.info(LOG_PREFIX + "newUtilisedLimitRecord NEW " + UTILISED_LIMIT_TABLE + " record");
        return activityHook != null ? new EbAlbeUtilisedLimitRecord(activityHook)
                : new EbAlbeUtilisedLimitRecord(recordHook);
    }

    /** Writes EB.ALBE.UTILISED.LIMIT <id>. */
    public boolean writeUtilisedLimit(String recordId, EbAlbeUtilisedLimitRecord utilRec) {
        logger.info(LOG_PREFIX + "writeUtilisedLimit WRITE " + UTILISED_LIMIT_TABLE + ">" + recordId
                + " credit=" + utilRec.getUtilisedCreditLimit().getValue()
                + " debit=" + utilRec.getUtilisedDebitLimit().getValue());
        try {
            utilisedLimitTable.write(recordId, utilRec);
            logger.info(LOG_PREFIX + "writeUtilisedLimit WRITTEN " + UTILISED_LIMIT_TABLE + ">" + recordId);
            return true;
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "writeUtilisedLimit FAILED " + UTILISED_LIMIT_TABLE + ">" + recordId + " "
                    + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // EB.ALBE.TXN.DETAILS.LIMIT
    // =========================================================================

    /** Reads EB.ALBE.TXN.DETAILS.LIMIT <id>; null if there is no record. */
    public EbAlbeTxnDetailsLimitRecord readTxnDetails(String recordId) {
        logger.info(LOG_PREFIX + "readTxnDetails READ " + TXN_DETAILS_TABLE + ">" + recordId);
        try {
            EbAlbeTxnDetailsLimitRecord txnRec =
                    new EbAlbeTxnDetailsLimitRecord(daObj.getRecord(TXN_DETAILS_TABLE, recordId));
            logger.info(LOG_PREFIX + "readTxnDetails FOUND " + TXN_DETAILS_TABLE + ">" + recordId
                    + " flag=" + txnRec.getCreditDebitFlag().getValue() + " amount=" + txnRec.getTxnAmount().getValue());
            return txnRec;
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "readTxnDetails NOT FOUND " + TXN_DETAILS_TABLE + ">" + recordId + " "
                    + e.getMessage());
            return null;
        }
    }

    /**
     * Ids of EB.ALBE.TXN.DETAILS.LIMIT records for a customer + AAA id, whatever the date
     * in the middle of the id. Empty list if none.
     */
    public List<String> selectTxnDetailsIds(String customerId, String aaaId) {
        String selection = "WITH @ID LIKE " + customerId + "-...-" + aaaId;
        logger.info(LOG_PREFIX + "selectTxnDetailsIds SELECT " + TXN_DETAILS_TABLE + " [" + selection + "]");
        try {
            List<String> recordIds = daObj.selectRecords("", TXN_DETAILS_TABLE, "", selection);
            List<String> result = recordIds == null ? List.of() : recordIds;
            logger.info(LOG_PREFIX + "selectTxnDetailsIds FOUND " + result.size() + " " + result);
            return result;
        } catch (Exception e) {
            logger.info(LOG_PREFIX + "selectTxnDetailsIds NONE " + e.getMessage());
            return List.of();
        }
    }

    /** New empty EB.ALBE.TXN.DETAILS.LIMIT record, to be filled and written. */
    public EbAlbeTxnDetailsLimitRecord newTxnDetailsRecord() {
        logger.info(LOG_PREFIX + "newTxnDetailsRecord NEW " + TXN_DETAILS_TABLE + " record");
        return activityHook != null ? new EbAlbeTxnDetailsLimitRecord(activityHook)
                : new EbAlbeTxnDetailsLimitRecord(recordHook);
    }

    /** Writes EB.ALBE.TXN.DETAILS.LIMIT <id>. */
    public boolean writeTxnDetails(String recordId, EbAlbeTxnDetailsLimitRecord txnRec) {
        logger.info(LOG_PREFIX + "writeTxnDetails WRITE " + TXN_DETAILS_TABLE + ">" + recordId);
        try {
            txnDetailsTable.write(recordId, txnRec);
            logger.info(LOG_PREFIX + "writeTxnDetails WRITTEN " + TXN_DETAILS_TABLE + ">" + recordId);
            return true;
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "writeTxnDetails FAILED " + TXN_DETAILS_TABLE + ">" + recordId + " "
                    + e.getMessage());
            return false;
        }
    }

    /** Deletes EB.ALBE.TXN.DETAILS.LIMIT <id>. */
    public boolean deleteTxnDetails(String recordId) {
        logger.info(LOG_PREFIX + "deleteTxnDetails DELETE " + TXN_DETAILS_TABLE + ">" + recordId);
        try {
            txnDetailsTable.delete(recordId);
            logger.info(LOG_PREFIX + "deleteTxnDetails DELETED " + TXN_DETAILS_TABLE + ">" + recordId);
            return true;
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "deleteTxnDetails FAILED " + TXN_DETAILS_TABLE + ">" + recordId + " "
                    + e.getMessage());
            return false;
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
            logger.info(LOG_PREFIX + "getFinancialMnemonic READ " + COMPANY_TABLE + ">" + companyId);
            CompanyRecord companyRec = new CompanyRecord(daObj.getRecord(COMPANY_TABLE, companyId));
            String finMnemonic = companyRec.getFinancialMne().getValue();
            logger.info(LOG_PREFIX + "getFinancialMnemonic FOUND company=" + companyId + " mnemonic=" + finMnemonic);
            return finMnemonic;
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "getFinancialMnemonic FAILED company=" + companyId + " " + e.getMessage());
            return "";
        }
    }

    /**
     * Account ids of the customer: CUSTOMER.ACCOUNT concat file first, then a SELECT on
     * ACCOUNT if the concat file gives nothing. Empty list if none.
     */
    public List<String> getCustomerAccountIds(String finMnemonic, String customerId) {
        logger.info(LOG_PREFIX + "getCustomerAccountIds READ " + CUSTOMER_ACCOUNT_FILE + ">" + customerId);
        try {
            List<String> accountIds = daObj.getConcatValues(CUSTOMER_ACCOUNT_FILE, customerId);
            if (accountIds != null && !accountIds.isEmpty()) {
                logger.info(LOG_PREFIX + "getCustomerAccountIds FOUND in " + CUSTOMER_ACCOUNT_FILE + " "
                        + accountIds.size() + " " + accountIds);
                return accountIds;
            }
            logger.info(LOG_PREFIX + "getCustomerAccountIds " + CUSTOMER_ACCOUNT_FILE + " empty for " + customerId);
        } catch (Exception e) {
            logger.info(LOG_PREFIX + "getCustomerAccountIds no " + CUSTOMER_ACCOUNT_FILE + " entry for " + customerId);
        }

        String selection = "WITH CUSTOMER EQ " + customerId;
        logger.info(LOG_PREFIX + "getCustomerAccountIds SELECT " + ACCOUNT_TABLE + " mnemonic=" + finMnemonic
                + " [" + selection + "]");
        try {
            List<String> accountIds = daObj.selectRecords(finMnemonic, ACCOUNT_TABLE, "", selection);
            List<String> result = accountIds == null ? List.of() : accountIds;
            logger.info(LOG_PREFIX + "getCustomerAccountIds FOUND by SELECT " + result.size() + " " + result);
            return result;
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "getCustomerAccountIds SELECT FAILED for " + customerId + " " + e.getMessage());
            return List.of();
        }
    }

    /** Reads ACCOUNT <id> in the given company; null if it cannot be read. */
    public AccountRecord readAccount(String finMnemonic, String accountId) {
        logger.info(LOG_PREFIX + "readAccount READ " + ACCOUNT_TABLE + ">" + accountId + " mnemonic=" + finMnemonic);
        try {
            AccountRecord accountRec = new AccountRecord(daObj.getRecord(finMnemonic, ACCOUNT_TABLE, "", accountId));
            logger.info(LOG_PREFIX + "readAccount FOUND " + ACCOUNT_TABLE + ">" + accountId + " workingBalance="
                    + accountRec.getWorkingBalance().getValue());
            return accountRec;
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "readAccount NOT FOUND " + ACCOUNT_TABLE + ">" + accountId + " "
                    + e.getMessage());
            return null;
        }
    }
}
