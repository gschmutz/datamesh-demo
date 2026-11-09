package com.datamesh.customer.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Lookup table of countries, keyed by its code. */
@Entity
public class Country extends Auditable {

    @Id
    private String code;
    private String name;

    protected Country() {
    }

    public Country(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
