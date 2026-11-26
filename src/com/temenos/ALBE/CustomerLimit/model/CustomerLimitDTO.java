package com.temenos.ALBE.CustomerLimit.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;

/*-----------------------------------------------------------------------------
 * Description : Input and result data passed between the customer limit routines.
 *               Input  : customer, transaction key, T24 date, transaction currency, credit or debit
 *                        amount (converted to the limit currency by the services).
 *               Result : valid flag, last message, limitMissing flag and one LimitBreach
 *                        per breached limit (override id + amount difference).
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 * 22-Nov-2026   Development      limitCcyMissing (blank CIF.LIMIT CCY = error); copyTransaction
 * 26-Nov-2026   Development      txnExchangeRate, rateBlocked (D44)
 * 25-Nov-2026   Development      activity, notes and describe() for the RESULT log line
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitDTO {
    /** CUSTOMER id (first customer of the arrangement). */
    private String customerId;
    /** Transaction key: root reference of the FT / TT / AAA / accounting entry (CustomerLimitEngine.transactionKey). */
    private String transactionId;
    /** AA arrangement the activity belongs to: part of the TXN.DETAILS id, so both sides of an own transfer count (FQ6). */
    private String arrangementId;
    /** Server date the transaction is counted on (from the activity REMARKS tag ALBE.LIMIT.DATE; else today). */
    private LocalDate transactionDate;
    /** Credit amount (in the limit currency once converted); zero for a debit. */
    private BigDecimal creditAmount = BigDecimal.ZERO;
    /** Debit amount (in the limit currency once converted); zero for a credit. */
    private BigDecimal debitAmount = BigDecimal.ZERO;
    /** Message of the last breach / error, for logging. */
    private String validationMessage;
    /** False once any limit is breached or the limit record is missing. */
    private boolean valid = true;
    /** True when the customer has no EB.ALBE.CIF.LIMITS record. */
    private boolean limitMissing = false;
    /** True when the EB.ALBE.CIF.LIMITS record has a blank CCY. */
    private boolean limitCcyMissing = false;
    /** One entry per breached limit, raised as an override by the routine. */
    private final List<LimitBreach> breaches = new ArrayList<>();
    /** Currency of the transaction (AA.ARRANGEMENT CURRENCY). */
    private String transactionCurrency;
    /** Currency of the customer's limits (EB.ALBE.CIF.LIMITS CCY); amounts are converted to it. */
    private String limitCurrency;
    /** Rate used to convert the amount from transactionCurrency to limitCurrency (1 if same). */
    private BigDecimal exchangeRate = BigDecimal.ONE;
    /** Credit / debit amount in the transaction currency, before conversion. */
    private BigDecimal originalAmount = BigDecimal.ZERO;
    /**
     * Same amount in local currency as given by T24 on the activity (TXN.AMOUNT.LCY / ORIG.TXN.AMT.LCY);
     * used instead of a rate lookup when the limit currency is the local currency. 0 if not given.
     */
    private BigDecimal amountLcy = BigDecimal.ZERO;
    /** Activity TXN.EXCH.RATE (1 transaction currency = rate LCY); 0 if not given (D44). */
    private BigDecimal txnExchangeRate = BigDecimal.ZERO;
    /**
     * True when the transaction amount cannot be converted to the limit currency (D44: no LCY amount
     * and no TXN.EXCH.RATE, or a limit not in the local currency) - error, the transaction is blocked.
     */
    private boolean rateBlocked = false;
    /** True when no exchange rate was found between the two currencies. */
    private boolean rateMissing = false;
    /** Currency that has no rate to the limit currency (transaction or account currency). */
    private String rateMissingCurrency;
    /** AAA ACTIVITY name, for the RESULT log line only. */
    private String activity;
    /** Plain-language notes of the checks / updates done for this request (RESULT log line). */
    private final List<String> notes = new ArrayList<>();
    /** EB.ALBE.CIF.LIMITS fields that are blank; raised once as the "limit not defined" override. */
    private final List<String> undefinedLimits = new ArrayList<>();

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getArrangementId() { return arrangementId; }
    public void setArrangementId(String arrangementId) { this.arrangementId = arrangementId; }

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public BigDecimal getCreditAmount() { return creditAmount; }
    public void setCreditAmount(BigDecimal creditAmount) {
        this.creditAmount = creditAmount == null ? BigDecimal.ZERO : creditAmount;
    }

    public BigDecimal getDebitAmount() { return debitAmount; }
    public void setDebitAmount(BigDecimal debitAmount) {
        this.debitAmount = debitAmount == null ? BigDecimal.ZERO : debitAmount;
    }

    /** True for a credit transaction (credit amount above zero), false for a debit. */
    public boolean isCredit() { return creditAmount.signum() > 0; }

    /** The transaction amount: the credit amount for a credit, the debit amount for a debit. */
    public BigDecimal getAmount() { return isCredit() ? creditAmount : debitAmount; }

    /** "customer=<id> aaa=<id>" - the key that starts most log lines about this request. */
    public String logKey() { return "customer=" + customerId + " txn=" + transactionId + " arrangement=" + arrangementId; }

    public String getActivity() { return activity; }
    public void setActivity(String activity) { this.activity = activity; }

    /** Adds one plain-language note for the RESULT log line. */
    public void addNote(String note) { notes.add(note); }
    public List<String> getNotes() { return Collections.unmodifiableList(notes); }

    /** Plain-language transaction description: FT26093BGWJR (activity) customer 100199 CREDIT 11,000.00 EGP on 2026-04-03. */
    public String describe() {
        String ccy = limitCurrency != null && !limitCurrency.isEmpty() ? limitCurrency : transactionCurrency;
        return transactionId + (activity == null ? "" : " (" + activity + ")") + " customer " + customerId
                + (isCredit() ? " CREDIT " : " DEBIT ")
                + String.format(java.util.Locale.ENGLISH, "%,.2f", getAmount()) + " " + (ccy == null ? "" : ccy)
                + " on " + transactionDate + " arrangement " + arrangementId;
    }

    public String getTransactionCurrency() { return transactionCurrency; }
    public void setTransactionCurrency(String transactionCurrency) { this.transactionCurrency = transactionCurrency; }

    public String getLimitCurrency() { return limitCurrency; }
    public void setLimitCurrency(String limitCurrency) { this.limitCurrency = limitCurrency; }

    public BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }

    public BigDecimal getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }

    /**
     * New request with the same transaction info (customer, transaction, date, currency, amounts)
     * and no check results - one request per check (daily / monthly, maximum CIF).
     */
    public CustomerLimitDTO copyTransaction() {
        CustomerLimitDTO copy = new CustomerLimitDTO();
        copy.setCustomerId(customerId);
        copy.setTransactionId(transactionId);
        copy.setArrangementId(arrangementId);
        copy.setTransactionDate(transactionDate);
        copy.setTransactionCurrency(transactionCurrency);
        copy.setCreditAmount(creditAmount);
        copy.setDebitAmount(debitAmount);
        copy.setAmountLcy(amountLcy);
        copy.setTxnExchangeRate(txnExchangeRate);
        copy.setActivity(activity);
        return copy;
    }

    public BigDecimal getAmountLcy() { return amountLcy; }
    public void setAmountLcy(BigDecimal amountLcy) { this.amountLcy = amountLcy == null ? BigDecimal.ZERO : amountLcy; }

    public BigDecimal getTxnExchangeRate() { return txnExchangeRate; }
    public void setTxnExchangeRate(BigDecimal txnExchangeRate) {
        this.txnExchangeRate = txnExchangeRate == null ? BigDecimal.ZERO : txnExchangeRate;
    }

    /** True when the transaction amount cannot be converted (D44) - error, transaction blocked. */
    public boolean isRateBlocked() { return rateBlocked; }

    /** Records that the transaction currency cannot be converted to the limit currency (D44). */
    public void setRateBlocked(String fromCurrency) {
        this.rateBlocked = true;
        this.rateMissingCurrency = fromCurrency;
        valid = false;
    }

    /** True when no exchange rate was found from a currency to the limit currency. */
    public boolean isRateMissing() { return rateMissing; }
    public String getRateMissingCurrency() { return rateMissingCurrency; }

    /** Records that fromCurrency has no exchange rate to the limit currency. */
    public void setRateMissing(String fromCurrency) {
        this.rateMissing = true;
        this.rateMissingCurrency = fromCurrency;
        valid = false;
        validationMessage = String.format(Constants.MSG_EXCH_RATE_MISSING, fromCurrency, limitCurrency);
    }

    public String getValidationMessage() { return validationMessage; }
    public void setValidationMessage(String validationMessage) { this.validationMessage = validationMessage; }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    /** True when the customer has no EB.ALBE.CIF.LIMITS record ("limit not defined" override). */
    public boolean isLimitMissing() { return limitMissing; }
    public void setLimitMissing(boolean limitMissing) {
        this.limitMissing = limitMissing;
        if (limitMissing) {
            valid = false;
            validationMessage = String.format(Constants.MSG_LIMIT_NOT_DEFINED, customerId);
        }
    }

    /** True when the customer's EB.ALBE.CIF.LIMITS has a blank CCY (error - blocks the transaction). */
    public boolean isLimitCcyMissing() { return limitCcyMissing; }
    public void setLimitCcyMissing(boolean limitCcyMissing) {
        this.limitCcyMissing = limitCcyMissing;
        if (limitCcyMissing) {
            valid = false;
        }
    }

    /** True when at least one required limit field is blank. */
    public boolean isLimitNotDefined() { return !undefinedLimits.isEmpty(); }
    public List<String> getUndefinedLimits() { return Collections.unmodifiableList(undefinedLimits); }

    /** Records a blank limit field (e.g. DAILY.DEBIT.LIMIT). */
    public void addUndefinedLimit(String fieldName) {
        if (!undefinedLimits.contains(fieldName)) {
            undefinedLimits.add(fieldName);
        }
        validationMessage = String.format(Constants.MSG_LIMIT_NOT_DEFINED, customerId) + " (" + fieldName + ")";
    }

    public List<LimitBreach> getBreaches() { return Collections.unmodifiableList(breaches); }

    /** Records a breach and marks the request invalid. */
    public void addBreach(String overrideId, String message, BigDecimal difference) {
        breaches.add(new LimitBreach(overrideId, message, difference));
        valid = false;
        validationMessage = message;
    }

    /** Compact form for log lines. */
    @Override
    public String toString() {
        return "CustomerLimitDTO[customer=" + customerId + ", txn=" + transactionId + ", arrangement=" + arrangementId + ", date=" + transactionDate
                + ", credit=" + creditAmount + ", debit=" + debitAmount + ", txnCcy=" + transactionCurrency
                + ", limitCcy=" + limitCurrency + ", rate=" + exchangeRate + ", originalAmount=" + originalAmount + ", amountLcy=" + amountLcy
                + ", txnExchRate=" + txnExchangeRate + ", rateBlocked=" + rateBlocked
                + ", rateMissing=" + rateMissing + ", valid=" + valid
                + ", limitMissing=" + limitMissing + ", limitCcyMissing=" + limitCcyMissing + ", undefinedLimits=" + undefinedLimits
                + ", breaches=" + breaches.size() + "]";
    }
}
