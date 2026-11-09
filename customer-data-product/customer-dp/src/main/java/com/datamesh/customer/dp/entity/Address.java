package com.datamesh.customer.dp.entity;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Read-only view on customer_dp_v1.address. */
@Entity
@Immutable
@Table(name = "address")
public class Address {

    @Id
    private Long id;
    private String street;
    private String zipCode;
    private String city;
    private String state;
    private String country;
    private String type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    protected Address() {
    }

    public Long getId() { return id; }
    public String getStreet() { return street; }
    public String getZipCode() { return zipCode; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getCountry() { return country; }
    public String getType() { return type; }
    public Customer getCustomer() { return customer; }
}
