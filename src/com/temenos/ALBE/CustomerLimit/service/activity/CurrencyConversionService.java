package com.temenos.ALBE.CustomerLimit.service.activity;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;

/*-----------------------------------------------------------------------------
 * Description : Converts amounts to the customer's limit currency (EB.ALBE.CIF.LIMITS CCY).
 *               Same currency (or a blank currency on either side) => rate 1, no lookup.
 *               Transaction amount with the limit in local currency => the activity's LCY
 *               amount (TXN.AMOUNT.LCY), no rate lookup.
 *               Otherwise different currency => rate from CustomerLimitRepository.readExchangeRate.
 *               No rate => the caller is told (null / rateMissing).
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 * 21-Nov-2026   Development      LCY amount from the activity when the limit CCY is the LCY (E1)
 *-----------------------------------------------------------------------------
 */
public class CurrencyConversionService {
    private static final String CLASS_NAME = "CurrencyConversionService";
    private static final String METHOD_GET_RATE = "getRate";
    private static final String METHOD_CONVERT_REQUEST = "convertRequest";
    private static final String LABEL_FROM = "from=";

    private final CustomerLimitRepository repository;

    public CurrencyConversionService(CustomerLimitRepository repository) {
        this.repository = repository;
    }

    /**
     * Rate from fromCcy to toCcy: 1 when the currencies are the same or one is blank,
     * otherwise the configured exchange rate; null when no rate is available.
     */
    public BigDecimal getRate(String fromCcy, String toCcy) {
        if (ValidationUtil.isBlank(fromCcy) || ValidationUtil.isBlank(toCcy) || ValidationUtil.sameCurrency(fromCcy,
                toCcy)) {
            LogUtil.info(CLASS_NAME, METHOD_GET_RATE, "CALC", LABEL_FROM + fromCcy + " to=" + toCcy + " same currency, rate=1");
            return Constants.SAME_CURRENCY_RATE;
        }
        BigDecimal rate = repository.readExchangeRate(fromCcy.trim(), toCcy.trim());
        if (rate == null || rate.signum() <= 0) {
            LogUtil.error(CLASS_NAME, METHOD_GET_RATE, "NO RATE", LABEL_FROM + fromCcy + " to=" + toCcy + " NO RATE");
            return null;
        }
        LogUtil.info(CLASS_NAME, METHOD_GET_RATE, "CALC", LABEL_FROM + fromCcy + " to=" + toCcy + " rate=" + rate);
        return rate;
    }

    /**
     * True when the activity's LCY amount (TXN.AMOUNT.LCY) can be used: different currencies,
     * the limit currency is the local currency and the LCY amount is given.
     */
    private boolean useLcyAmount(CustomerLimitDTO request, BigDecimal amount, String limitCcy) {
        if (ValidationUtil.isBlank(request.getTransactionCurrency()) || ValidationUtil.isBlank(limitCcy)
                || ValidationUtil.sameCurrency(request.getTransactionCurrency(), limitCcy)
                || !ValidationUtil.isPositive(request.getAmountLcy()) || !ValidationUtil.isPositive(amount)) {
            return false;
        }
        return ValidationUtil.sameCurrency(limitCcy, repository.getLocalCurrency());
    }

    /** amount in fromCcy converted to toCcy; null when no rate is available. */
    public BigDecimal convert(BigDecimal amount, String fromCcy, String toCcy) {
        BigDecimal rate = getRate(fromCcy, toCcy);
        if (rate == null) {
            return null;
        }
        BigDecimal converted = amount.multiply(rate).setScale(Constants.AMOUNT_SCALE, RoundingMode.HALF_UP);
        LogUtil.info(CLASS_NAME, "convert", "CALC", amount + " " + fromCcy + " x " + rate + " = " + converted + " "
                + toCcy);
        return converted;
    }

    /**
     * Converts the request's credit / debit amount from its transaction currency to limitCcy.
     * Keeps the original amount and the rate on the request.
     * @return true if converted (or no conversion needed); false if no rate (rateMissing set)
     */
    public boolean convertRequest(CustomerLimitDTO request, String limitCcy) {
        boolean credit = request.getCreditAmount().signum() > 0;
        BigDecimal amount = credit ? request.getCreditAmount() : request.getDebitAmount();
        request.setLimitCurrency(limitCcy);
        request.setOriginalAmount(amount);

        // Limit in local currency and T24 gave the LCY amount on the activity: use it, no rate lookup.
        if (useLcyAmount(request, amount, limitCcy)) {
            BigDecimal converted = request.getAmountLcy().setScale(Constants.AMOUNT_SCALE, RoundingMode.HALF_UP);
            request.setExchangeRate(converted.divide(amount, Constants.RATE_SCALE, RoundingMode.HALF_UP));
            if (credit) {
                request.setCreditAmount(converted);
            } else {
                request.setDebitAmount(converted);
            }
            LogUtil.info(CLASS_NAME, METHOD_CONVERT_REQUEST, "CALC", "customer=" + request.getCustomerId() + " " + amount
                    + " " + request.getTransactionCurrency() + " = " + converted + " " + limitCcy
                    + " (LCY amount from the activity)");
            return true;
        }

        BigDecimal rate = getRate(request.getTransactionCurrency(), limitCcy);
        if (rate == null) {
            LogUtil.error(CLASS_NAME, METHOD_CONVERT_REQUEST, "NO RATE", request.logKey() + " no rate from "
                    + request.getTransactionCurrency() + " to " + limitCcy);
            request.setRateMissing(request.getTransactionCurrency());
            return false;
        }

        BigDecimal converted = amount.multiply(rate).setScale(Constants.AMOUNT_SCALE, RoundingMode.HALF_UP);
        request.setExchangeRate(rate);
        if (credit) {
            request.setCreditAmount(converted);
        } else {
            request.setDebitAmount(converted);
        }
        LogUtil.info(CLASS_NAME, METHOD_CONVERT_REQUEST, "CALC", "customer=" + request.getCustomerId() + " " + amount + " "
                + request.getTransactionCurrency() + " x " + rate + " = " + converted + " " + limitCcy);
        return true;
    }

}
