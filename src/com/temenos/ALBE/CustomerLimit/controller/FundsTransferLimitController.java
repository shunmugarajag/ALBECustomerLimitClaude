package com.temenos.ALBE.CustomerLimit.controller;

import java.util.List;

import com.temenos.ALBE.CustomerLimit.model.TransactionSide;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.api.TStructure;
import com.temenos.api.TValidationResponse;
import com.temenos.t24.api.complex.eb.servicehook.TransactionData;
import com.temenos.t24.api.complex.eb.templatehook.TransactionContext;
import com.temenos.t24.api.hook.system.RecordLifecycle;
import com.temenos.t24.api.records.fundstransfer.FundsTransferRecord;

/*-----------------------------------------------------------------------------
 * Attached as : VERSION Input routine (validateRecord) and After Unau + Auth routine
 *               (postUpdateRequest) - see T24_CONFIGURATION
 * Attached to : FUNDS.TRANSFER versions chosen by the bank
 * Description : Customer limit check and utilisation for FUNDS.TRANSFER (HLSD 7.2.1 - 2 and 3).
 *               Both sides are read (TransactionSideReader); each customer account side is
 *               checked and counted for its own customer, internal / cash accounts skipped.
 *               validateRecord     : daily / monthly (debit and credit) and maximum CIF (credit)
 *                                    check, acceptable overrides on the side's amount field
 *               postUpdateRequest  : utilisation - input counts, delete takes back, authorising
 *                                    a reversal takes back (TransactionLimitHandler.actionFor)
 *               Counted date: FT PROCESSING.DATE, else the T24 date.
 *               Common flow in TransactionLimitHandler. No business rules and no DB access here.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 21-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class FundsTransferLimitController extends RecordLifecycle {
    private static final String CLASS_NAME = "FundsTransferLimitController";

    /** Input: limit checks of every customer side; overrides on the amount fields. */
    @Override
    public TValidationResponse validateRecord(String application, String currentRecordId, TStructure currentRecord,
            TStructure unauthorisedRecord, TStructure liveRecord, TransactionContext transactionContext) {

        FundsTransferRecord record = new FundsTransferRecord(currentRecord);
        long startTime = System.currentTimeMillis();
        try {
            LogUtil.info(CLASS_NAME, "validateRecord", "START", "id=" + currentRecordId + " function="
                    + (transactionContext == null ? "" : transactionContext.getCurrentFunction()));
            TransactionLimitHandler handler = new TransactionLimitHandler(new CustomerLimitRepository(this));
            List<TransactionSide> sides = TransactionSideReader.fromFundsTransfer(record);
            int raised = handler.validate(currentRecordId, sides, handler.countedDate(record.getProcessingDate().getValue()));
            LogUtil.info(CLASS_NAME, "validateRecord", "END", "id=" + currentRecordId + " overridesRaised=" + raised
                    + " elapsedMs=" + (System.currentTimeMillis() - startTime));
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "validateRecord", "ERROR", "id=" + currentRecordId, e);
        }
        return record.getValidationResponse();
    }

    /** After Unau / Auth: utilisation - input counts, delete and authorised reversal take back. */
    @Override
    public void postUpdateRequest(String application, String currentRecordId, TStructure currentRecord,
            List<TransactionData> transactionData, List<TStructure> currentRecords,
            TransactionContext transactionContext) {

        long startTime = System.currentTimeMillis();
        try {
            FundsTransferRecord record = new FundsTransferRecord(currentRecord);
            String function = transactionContext == null ? "" : transactionContext.getCurrentFunction();
            String status = record.getRecordStatus();
            LogUtil.info(CLASS_NAME, "postUpdateRequest", "START", "id=" + currentRecordId + " function=" + function
                    + " status=" + status);
            TransactionLimitHandler handler = new TransactionLimitHandler(new CustomerLimitRepository(this));
            List<TransactionSide> sides = TransactionSideReader.fromFundsTransfer(record);
            handler.update(currentRecordId, sides, function, status, handler.countedDate(record.getProcessingDate().getValue()));
            LogUtil.info(CLASS_NAME, "postUpdateRequest", "END", "id=" + currentRecordId + " elapsedMs="
                    + (System.currentTimeMillis() - startTime));
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "postUpdateRequest", "ERROR", "id=" + currentRecordId, e);
        }
    }
}
