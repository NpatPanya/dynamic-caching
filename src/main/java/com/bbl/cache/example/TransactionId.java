package com.bbl.cache.example;

import java.util.Objects;

@Deprecated
public class TransactionId {

    private String referenceId;
    private String orderStatus;


    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TransactionId transactionId = (TransactionId) o;
        return Objects.equals(referenceId, transactionId.referenceId) && Objects.equals(orderStatus, transactionId.orderStatus);
    }

    @Override
    public int hashCode() {
        return Objects.hash(referenceId, orderStatus);
    }

    public TransactionId(String orderStatus, String referenceId) {
        this.orderStatus = orderStatus;
        this.referenceId = referenceId;
    }
}
