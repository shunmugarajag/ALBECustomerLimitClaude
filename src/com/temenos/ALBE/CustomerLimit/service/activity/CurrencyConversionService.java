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
 *               TRANSACTION amount (convertRequest, D44 - only from the activity):
 *                 same currency (or a blank currency)          => as it is
 *                 limit in LCY, LCY amount on the activity      => TXN.AMOUNT.LCY / ORIG.TXN.AMT.LCY
 *                 limit in LCY, no LCY amount, TXN.EXCH.RATE    => amount x TXN.EXCH.RATE
 *                 otherwise (no LCY amount and no rate, or limit not in LCY)
 *                                                               => rateBlocked (ERROR, blocks)
 *               OTHER amounts (convert / getRate - max CIF account balances, parked):
 *                 rate from CustomerLimitRepository.readExchangeRate; none => null (override).
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 * 21-Nov-2026   Development      LCY amount from the activity when the limit CCY is the LCY (E1)
 * 26-Nov-2026   Development      D44: transaction amount only from the activity (LCY amount, else
 *                                TXN.EXCH.RATE, else blocked); limit not in LCY -> blocked;
 *                                LCY not readable -> override, not blocked (technical failure)
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
            LogUtil.info(CLASS_NAME, METHOD_GET_RATE, "NO RATE", LABEL_FROM + fromCcy + " to=" + toCcy + " NO RATE");
            return null;
        }
        LogUtil.info(CLASS_NAME, METHOD_GET_RATE, "CALC", LABEL_FROM + fromCcy + " to=" + toCcy + " rate=" + rate);
        return rate;
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
     * Converts the request's credit / debit amount from its transaction currency to limitCcy (D44).
     * Keeps the original amount and the rate used on the request.
     * @return true if converted (or no conversion needed); false if it cannot be converted -
     *         rateBlocked is set (ERROR, the transaction is blocked)
     */
    public boolean convertRequest(CustomerLimitDTO request, String limitCcy) {
        boolean credit = request.getCreditAmount().signum() > 0;
        BigDecimal amount = credit ? request.getCreditAmount() : request.getDebitAmount();
        String txnCcy = request.getTransactionCurrency();
        request.setLimitCurrency(limitCcy);
        request.setOriginalAmount(amount);

        if (ValidationUtil.isBlank(txnCcy) || ValidationUtil.isBlank(limitCcy) || ValidationUtil.sameCurrency(txnCcy,
                limitCcy)) {
            BigDecimal same = amount.setScale(Constants.AMOUNT_SCALE, RoundingMode.HALF_UP);
            request.setExchangeRate(Constants.SAME_CURRENCY_RATE);
            if (credit) {
                request.setCreditAmount(same);
            } else {
                request.setDebitAmount(same);
            }
            LogUtil.info(CLASS_NAME, METHOD_CONVERT_REQUEST, "CALC", request.logKey() + " same currency " + txnCcy);
            return true;
        }

        String lcy = repository.getLocalCurrency();
        if (ValidationUtil.isBlank(lcy)) {
            // Technical failure (local currency not readable): logged, not blocked - check skipped.
            LogUtil.info(CLASS_NAME, METHOD_CONVERT_REQUEST, "NO LCY", request.logKey()
                    + " local currency not readable - check skipped, not blocked");
            request.addNote("local currency not readable -> " + txnCcy + " amount not converted, not checked");
            request.setRateMissing(txnCcy);
            return false;
        }
        if (!ValidationUtil.sameCurrency(limitCcy, lcy)) {
            return block(request, "limit currency " + limitCcy + " is not the local currency " + lcy);
        }

        BigDecimal converted;
        String source;
        if (ValidationUtil.isPositive(request.getAmountLcy())) {
            converted = request.getAmountLcy().setScale(Constants.AMOUNT_SCALE, RoundingMode.HALF_UP);
            request.setExchangeRate(converted.divide(amount, Constants.RATE_SCALE, RoundingMode.HALF_UP));
            source = "LCY amount from the activity";
        } else if (ValidationUtil.isPositive(request.getTxnExchangeRate())) {
            converted = amount.multiply(request.getTxnExchangeRate()).setScale(Constants.AMOUNT_SCALE,
                    RoundingMode.HALF_UP);
            request.setExchangeRate(request.getTxnExchangeRate());
            source = "TXN.EXCH.RATE " + request.getTxnExchangeRate().toPlainString();
        } else {
            return block(request, "no LCY amount and no TXN.EXCH.RATE on the activity");
        }

        if (credit) {
            request.setCreditAmount(converted);
        } else {
            request.setDebitAmount(converted);
        }
        request.addNote("converted " + ValidationUtil.formatAmount(amount) + " " + txnCcy + " -> "
                + ValidationUtil.formatAmount(converted) + " " + limitCcy + " (" + source + ")");
        LogUtil.info(CLASS_NAME, METHOD_CONVERT_REQUEST, "CALC", "customer=" + request.getCustomerId() + " " + amount
                + " " + txnCcy + " = " + converted + " " + limitCcy + " (" + source + ")");
        return true;
    }

    /** D44: the transaction amount cannot be converted - ERROR, the transaction is blocked. */
    private boolean block(CustomerLimitDTO request, String reason) {
        LogUtil.info(CLASS_NAME, METHOD_CONVERT_REQUEST, "BLOCKED", request.logKey() + " "
                + request.getTransactionCurrency() + " -> " + request.getLimitCurrency() + ": " + reason
                + ", raising " + Constants.ERR_EXCH_RATE_MISSING);
        request.addNote("BLOCKED: " + request.getTransactionCurrency() + " -> " + request.getLimitCurrency()
                + " cannot be converted (" + reason + ")");
        request.setRateBlocked(request.getTransactionCurrency());
        return false;
    }
}
