package com.datamesh.organization.entity;

import java.util.ArrayList;
import java.util.List;

import com.datamesh.customer.entity.Auditable;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "organization")
public class Organization extends Auditable {

    @Id
    private String id;
    private String name;
    private boolean shared;
    private String domain = "";
    private boolean domainAssignment;
    private boolean active;
    private boolean vip;
    private String note;

    @ElementCollection
    @CollectionTable(name = "organization_member", joinColumns = @JoinColumn(name = "organization_id"))
    @Column(name = "member_id")
    private List<Long> memberIds = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "organization_secondary_member", joinColumns = @JoinColumn(name = "organization_id"))
    @Column(name = "member_id")
    private List<Long> secondaryMemberIds = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public boolean isDomainAssignment() {
        return domainAssignment;
    }

    public void setDomainAssignment(boolean domainAssignment) {
        this.domainAssignment = domainAssignment;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isVip() {
        return vip;
    }

    public void setVip(boolean vip) {
        this.vip = vip;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public List<Long> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<Long> memberIds) {
        this.memberIds = memberIds;
    }

    public List<Long> getSecondaryMemberIds() {
        return secondaryMemberIds;
    }

    public void setSecondaryMemberIds(List<Long> secondaryMemberIds) {
        this.secondaryMemberIds = secondaryMemberIds;
    }
}
