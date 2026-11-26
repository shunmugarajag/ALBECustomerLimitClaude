package com.temenos.ALBE.CustomerLimit.model;

import java.math.BigDecimal;

/*-----------------------------------------------------------------------------
 * Description : One breached limit: EB.OVERRIDE id, HLSD message and the amount by
 *               which the limit was exceeded. Collected on CustomerLimitDTO and raised
 *               as an override by CustomerLimitOverride.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class LimitBreach {
    /** EB.OVERRIDE id, e.g. EB-ABLE.DAILY.CREDIT.LIMIT.EXCEED */
    private final String overrideId;
    /** HLSD message text with the values filled in, for logging. */
    private final String message;
    /** Amount by which the limit was exceeded (second & of the override). */
    private final BigDecimal difference;

    public LimitBreach(String overrideId, String message, BigDecimal difference) {
        this.overrideId = overrideId;
        this.message = message;
        this.difference = difference;
    }

    public String getOverrideId() { return overrideId; }
    public String getMessage() { return message; }
    public BigDecimal getDifference() { return difference; }

    @Override
    public String toString() {
        return "LimitBreach[overrideId=" + overrideId + ", difference=" + difference + "]";
    }
}
