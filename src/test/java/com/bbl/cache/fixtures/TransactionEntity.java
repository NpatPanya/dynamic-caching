package com.bbl.cache.fixtures;

public class TransactionEntity {

    private TransactionId txnId;
    private String txnDetails;

    public String getTxnDetails() {
        return txnDetails;
    }

    public void setTxnDetails(String txnDetails) {
        this.txnDetails = txnDetails;
    }

    public TransactionId getTxnId() {
        return txnId;
    }

    public void setTxnId(TransactionId txnId) {
        this.txnId = txnId;
    }

    public TransactionEntity(TransactionId txnId, String txnDetails) {
        this.txnId = txnId;
        this.txnDetails = txnDetails;
    }
}
