package com.temenos.ALBE.CustomerLimit.service.activity;

import java.time.LocalDate;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.ALBE.CustomerLimit.validation.CustomerLimitRequestValidator;

/*-----------------------------------------------------------------------------
 * Description : The one entry point of customer limit management. It works only with
 *               TRANSACTION INFO (CustomerLimitDTO: customer, transaction, date, currency,
 *               credit or debit amount) and CUSTOMER LIMIT INFO (EB.ALBE.CIF.LIMITS), so it does
 *               not depend on where the transaction comes from (FT, TELLER and other transactions on
 *               an AA account all arrive as an activity). The ACTIVITY.API routines build the
 *               transaction info and call this class:
 *
 *                 checkDailyMonthly  daily / monthly credit or debit limit
 *                 checkMaxCif        maximum CIF balance (credits only)
 *                 count              utilisation at input (an earlier input of the same
 *                                    activity is taken back first); key = customer + day +
 *                                    FT / AAA + arrangement, so both sides of an own-account
 *                                    transfer are counted (FQ6)
 *                 reverse            utilisation taken back, from the day it was counted on
 *
 *               The request is validated here once (CustomerLimitRequestValidator); the business
 *               rules stay in DailyMonthlyLimitService / MaximumCifLimitService.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 22-Nov-2026   Development      Initial Version
 * 22-Nov-2026   Development      Key = root reference (no .CR/.DR); own-account transfers not counted
 * 22-Nov-2026   Development      Activity routines only (FT / TELLER / ACCOUNT.PARAMETER routines removed)
 * 23-Nov-2026   Development      Arrangement id in the key; own-account transfers counted on both sides (FQ6)
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitEngine {
    private static final String CLASS_NAME = "CustomerLimitEngine";

    private final CustomerLimitRepository repository;
    private final DailyMonthlyLimitService dailyMonthlyService;

    public CustomerLimitEngine(CustomerLimitRepository repository) {
        this.repository = repository;
        this.dailyMonthlyService = new DailyMonthlyLimitService(repository);
    }

    /**
     * Daily / monthly check. Returns the checked request (with its breaches and "limit not defined" /
     * "no rate" flags) for the view to raise overrides; null if the transaction info is not valid.
     */
    public CustomerLimitDTO checkDailyMonthly(CustomerLimitDTO transaction) {
        if (!CustomerLimitRequestValidator.isValidForDailyMonthly(transaction)) {
            return null;
        }
        CustomerLimitDTO result = transaction.copyTransaction();
        dailyMonthlyService.validate(result);
        LogUtil.info(CLASS_NAME, "checkDailyMonthly", "END", result.logKey() + " breaches="
                + result.getBreaches().size());
        return result;
    }

    /** Maximum CIF balance check (credits only); null for a debit or invalid transaction info. */
    public CustomerLimitDTO checkMaxCif(CustomerLimitDTO transaction) {
        if (!transaction.isCredit() || !CustomerLimitRequestValidator.isValidForMaxCif(transaction)) {
            return null;
        }
        CustomerLimitDTO result = transaction.copyTransaction();
        new MaximumCifLimitService(repository).validate(result);
        LogUtil.info(CLASS_NAME, "checkMaxCif", "END", result.logKey() + " breaches=" + result.getBreaches().size());
        return result;
    }

    /**
     * Counts the transaction at input; an earlier input of the same activity (same customer,
     * transaction and arrangement) is replaced. Both sides of an own-account transfer are counted:
     * they are on different arrangements, so they are two records (FQ6).
     */
    public void count(CustomerLimitDTO transaction, String accountNo, String effectiveDate) {
        if (!CustomerLimitRequestValidator.isValidForDailyMonthly(transaction)) {
            return;
        }
        LogUtil.info(CLASS_NAME, "count", "ACTION", "COUNT " + transaction.logKey());
        dailyMonthlyService.countAtInput(transaction, accountNo, effectiveDate);
    }

    /**
     * Takes the transaction back from the day it was counted on. The day comes from the transaction
     * info; if nothing was counted on that day, the counted record is looked up by the transaction
     * (one SELECT, only then) - e.g. a reversal on a later day.
     * @param fallbackToToday when nothing was counted at all, take back from the transaction day anyway
     * @return number of records changed
     */
    public int reverse(CustomerLimitDTO transaction, boolean fallbackToToday) {
        if (!CustomerLimitRequestValidator.isValidForDailyMonthly(transaction)) {
            return 0;
        }
        List<CustomerLimitDTO> recorded = new TxnDetailsLimitRecorder(repository).findOriginal(transaction);
        if (recorded.isEmpty()) {
            LocalDate counted = findCountedDate(transaction);
            if (counted != null) {
                transaction.setTransactionDate(counted);
                recorded = new TxnDetailsLimitRecorder(repository).findOriginal(transaction);
            }
        }
        LogUtil.info(CLASS_NAME, "reverse", "ACTION", "REVERSE " + transaction.logKey());
        return dailyMonthlyService.reverse(transaction, fallbackToToday);
    }

    /**
     * Counted transaction key: the root reference (e.g. FT26091ABCDE behind an activity, else the
     * AAA id AAACT26091XYZ). Together with the customer and the day it is the
     * EB.ALBE.TXN.DETAILS.LIMIT id.
     */
    public static String transactionKey(String reference) {
        return ValidationUtil.rootReference(reference);
    }

    /** Date a transaction was counted on, from its EB.ALBE.TXN.DETAILS.LIMIT id; null if none. */
    private LocalDate findCountedDate(CustomerLimitDTO transaction) {
        for (String recordId : repository.selectTxnDetailsIdsForTransaction(transaction.getCustomerId(),
                transaction.getTransactionId(), ValidationUtil.nvl(transaction.getArrangementId()))) {
            String[] parts = recordId.split(Constants.TXN_ID_SEPARATOR, 4);
            LocalDate date = parts.length >= 3 ? ValidationUtil.toDate(parts[1]) : null;
            if (date != null) {
                LogUtil.info(CLASS_NAME, "findCountedDate", "FOUND", "id=" + recordId);
                return date;
            }
        }
        LogUtil.info(CLASS_NAME, "findCountedDate", "NOT FOUND", transaction.logKey());
        return null;
    }
}
