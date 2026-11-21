package com.temenos.ALBE.CustomerLimit.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;

/*-----------------------------------------------------------------------------
 * Description : Input and result data passed between the customer limit routines.
 *               Input  : customer, AAA id, server date, transaction currency, credit or debit
 *                        amount (converted to the limit currency by the services).
 *               Result : valid flag, last message, limitMissing flag and one LimitBreach
 *                        per breached limit (override id + amount difference).
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitDTO {
    /** CUSTOMER id (first customer of the arrangement). */
    private String customerId;
    /** AAA id (daily/monthly routine) or arrangement id (maximum CIF routine). */
    private String transactionId;
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
    /** True when the customer has no EB.ALBE.CIF.LIMIT record. */
    private boolean limitMissing = false;
    /** One entry per breached limit, raised as an override by the routine. */
    private final List<LimitBreach> breaches = new ArrayList<>();
    /** Currency of the transaction (AA.ARRANGEMENT CURRENCY). */
    private String transactionCurrency;
    /** Currency of the customer's limits (EB.ALBE.CIF.LIMIT CCY); amounts are converted to it. */
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
    /** True when no exchange rate was found between the two currencies. */
    private boolean rateMissing = false;
    /** Currency that has no rate to the limit currency (transaction or account currency). */
    private String rateMissingCurrency;
    /** EB.ALBE.CIF.LIMIT fields that are blank; raised once as the "limit not defined" override. */
    private final List<String> undefinedLimits = new ArrayList<>();

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

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
    public String logKey() { return "customer=" + customerId + " aaa=" + transactionId; }

    public String getTransactionCurrency() { return transactionCurrency; }
    public void setTransactionCurrency(String transactionCurrency) { this.transactionCurrency = transactionCurrency; }

    public String getLimitCurrency() { return limitCurrency; }
    public void setLimitCurrency(String limitCurrency) { this.limitCurrency = limitCurrency; }

    public BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }

    public BigDecimal getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }

    public BigDecimal getAmountLcy() { return amountLcy; }
    public void setAmountLcy(BigDecimal amountLcy) { this.amountLcy = amountLcy == null ? BigDecimal.ZERO : amountLcy; }

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

    /** True when the customer has no EB.ALBE.CIF.LIMIT record ("limit not defined" override). */
    public boolean isLimitMissing() { return limitMissing; }
    public void setLimitMissing(boolean limitMissing) {
        this.limitMissing = limitMissing;
        if (limitMissing) {
            valid = false;
            validationMessage = String.format(Constants.MSG_LIMIT_NOT_DEFINED, customerId);
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
        return "CustomerLimitDTO[customer=" + customerId + ", txn=" + transactionId + ", date=" + transactionDate
                + ", credit=" + creditAmount + ", debit=" + debitAmount + ", txnCcy=" + transactionCurrency
                + ", limitCcy=" + limitCurrency + ", rate=" + exchangeRate + ", originalAmount=" + originalAmount + ", amountLcy=" + amountLcy
                + ", rateMissing=" + rateMissing + ", valid=" + valid
                + ", limitMissing=" + limitMissing + ", undefinedLimits=" + undefinedLimits
                + ", breaches=" + breaches.size() + "]";
    }
}
