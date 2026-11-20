package com.temenos.ALBE.CustomerLimit.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*-----------------------------------------------------------------------------
 * Description : Input and result data passed between the customer limit routines.
 *               Input  : customer, AAA id, server date, credit or debit amount (LCY).
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
    /** Server date the transaction is counted on. */
    private LocalDate transactionDate;
    /** Credit amount in LCY; zero for a debit. */
    private BigDecimal creditAmount = BigDecimal.ZERO;
    /** Debit amount in LCY; zero for a credit. */
    private BigDecimal debitAmount = BigDecimal.ZERO;
    /** Message of the last breach / error, for logging. */
    private String validationMessage;
    /** False once any limit is breached or the limit record is missing. */
    private boolean valid = true;
    /** True when the customer has no EB.ALBE.CIF.LIMIT record. */
    private boolean limitMissing = false;
    /** One entry per breached limit, raised as an override by the routine. */
    private final List<LimitBreach> breaches = new ArrayList<>();
    /** EB.ALBE.CIF.LIMIT fields that are blank; raised once as the "limit not defined" override. */
    private final List<String> undefinedLimits = new ArrayList<>();

    /** One breached limit: override id, HLSD message and the amount by which it was exceeded. */
    public static class LimitBreach {
        private final String overrideId;
        private final String message;
        private final BigDecimal difference;

        public LimitBreach(String overrideId, String message, BigDecimal difference) {
            this.overrideId = overrideId;
            this.message = message;
            this.difference = difference;
        }

        public String getOverrideId() { return overrideId; }
        public String getMessage() { return message; }
        public BigDecimal getDifference() { return difference; }
    }

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
            validationMessage = "Customer limit not defined for Customer " + customerId;
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
        validationMessage = "Customer limit not defined for Customer " + customerId + " (" + fieldName + ")";
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
                + ", credit=" + creditAmount + ", debit=" + debitAmount + ", valid=" + valid
                + ", limitMissing=" + limitMissing + ", undefinedLimits=" + undefinedLimits
                + ", breaches=" + breaches.size() + "]";
    }
}
