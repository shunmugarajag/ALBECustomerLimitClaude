package com.temenos.ALBE.CustomerLimit.model;

import java.math.BigDecimal;

/*-----------------------------------------------------------------------------
 * Description : One breached limit: EB.OVERRIDE id and the amount by which the limit was
 *               exceeded. Collected on CustomerLimitDTO and raised as an override by
 *               CustomerLimitOverride (the text itself is in EB.OVERRIDE).
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 * 26-Nov-2026   Development      Clean rewrite (D47): message text removed (EB.OVERRIDE holds it)
 *-----------------------------------------------------------------------------
 */
public class LimitBreach {
    /** EB.OVERRIDE id, e.g. EB-ABLE.DAILY.CREDIT.LIMIT.EXCEED */
    private final String overrideId;
    /** Amount by which the limit was exceeded. */
    private final BigDecimal difference;

    public LimitBreach(String overrideId, BigDecimal difference) {
        this.overrideId = overrideId;
        this.difference = difference;
    }

    public String getOverrideId() { return overrideId; }
    public BigDecimal getDifference() { return difference; }

    @Override
    public String toString() {
        return "LimitBreach[overrideId=" + overrideId + ", difference=" + difference + "]";
    }
}
