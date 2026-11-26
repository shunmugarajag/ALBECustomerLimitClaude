package com.temenos.ALBE.CustomerLimit.model;

import java.util.ArrayList;
import java.util.List;

import com.temenos.t24.api.records.ebalbeutilisedlimit.EbAlbeUtilisedLimitRecord;

/*-----------------------------------------------------------------------------
 * Description : The records one input already read during the limit check, handed to the
 *               count in the same call so they are not read again (single routine, D44).
 *               Lives only for that one call - no cache: nothing is kept between calls.
 *               A record that does not exist yet stays null (a new one is created at count).
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 26-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class UtilisationRecords {
    /** EB.ALBE.UTILISED.LIMIT <customer>-YYYYMMDD */
    private String dailyId;
    private EbAlbeUtilisedLimitRecord dailyRecord;
    /** EB.ALBE.UTILISED.LIMIT <customer>-YYYYMM */
    private String monthlyId;
    private EbAlbeUtilisedLimitRecord monthlyRecord;
    /** Earlier input of the same transaction (EB.ALBE.TXN.DETAILS.LIMIT), empty on a first input. */
    private List<CustomerLimitDTO> earlierInputs = new ArrayList<>();
    /** True once both utilisation records were read (they may be null = not there yet). */
    private boolean dailyRead;
    private boolean monthlyRead;

    public String getDailyId() { return dailyId; }
    public EbAlbeUtilisedLimitRecord getDailyRecord() { return dailyRecord; }

    /** Daily record as read (null = no record yet). */
    public void setDaily(String id, EbAlbeUtilisedLimitRecord rec) {
        this.dailyId = id;
        this.dailyRecord = rec;
        this.dailyRead = true;
    }

    public String getMonthlyId() { return monthlyId; }
    public EbAlbeUtilisedLimitRecord getMonthlyRecord() { return monthlyRecord; }

    /** Monthly record as read (null = no record yet). */
    public void setMonthly(String id, EbAlbeUtilisedLimitRecord rec) {
        this.monthlyId = id;
        this.monthlyRecord = rec;
        this.monthlyRead = true;
    }

    public List<CustomerLimitDTO> getEarlierInputs() { return earlierInputs; }
    public void setEarlierInputs(List<CustomerLimitDTO> earlierInputs) {
        this.earlierInputs = earlierInputs == null ? new ArrayList<>() : earlierInputs;
    }

    /**
     * True when the count can use these records directly: both records were read and this is a
     * first input (a re-input takes the earlier amount back first, which changes the records).
     */
    public boolean isUsableForCount() {
        return dailyRead && monthlyRead && earlierInputs.isEmpty();
    }
}
