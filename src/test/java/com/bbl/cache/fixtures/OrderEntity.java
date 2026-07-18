package com.bbl.cache.fixtures;

/**
 * Mimics the shape of a JPA entity (no-arg constructor, mutable fields, identity-based
 * equals/hashCode) WITHOUT an actual jakarta.persistence dependency — proves the cache makes
 * no assumptions about {@code T} beyond it being some Java object.
 */
public class OrderEntity {

    private String orderId;
    private String customerId;

    public OrderEntity() {
    }

    public OrderEntity(String orderId, String customerId) {
        this.orderId = orderId;
        this.customerId = customerId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderEntity other)) return false;
        return java.util.Objects.equals(orderId, other.orderId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hashCode(orderId);
    }
}
