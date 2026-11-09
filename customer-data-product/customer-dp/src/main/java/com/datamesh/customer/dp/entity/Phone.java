package com.datamesh.customer.dp.entity;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Read-only view on customer_dp_v1.phone. */
@Entity
@Immutable
@Table(name = "phone")
public class Phone {

    @Id
    private Long id;
    private String number;
    private String type;
    private String availability;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    protected Phone() {
    }

    public Long getId() { return id; }
    public String getNumber() { return number; }
    public String getType() { return type; }
    public String getAvailability() { return availability; }
    public Customer getCustomer() { return customer; }
}
