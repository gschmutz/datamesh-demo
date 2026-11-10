package com.datamesh.customer.dp.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/** Read-only view on customer_dp_v1.customer. */
@Entity
@Immutable
@Table(name = "customer")
public class Customer {

    @Id
    private String id;
    private String login;
    private boolean active;
    private boolean verified;
    private boolean vip;
    private String firstName;
    private String lastName;
    private String emailAddress;
    private String preferredContact;
    private String segment;
    private LocalDate dateOfBirth;
    private String preferredLanguage;
    private boolean marketingConsent;
    private String lifecycleStatus;
    private String image;
    private String imageSource;
    private String department;
    private String web;
    private String note;
    private String address;

    @Column(name = "onboarded_date")
    private Instant onboardedDate;
    private Instant createdAt;
    private Instant modifiedAt;

    @OneToMany(mappedBy = "customer")
    @BatchSize(size = 50)
    private List<Address> addresses = new ArrayList<>();

    @OneToMany(mappedBy = "customer")
    @BatchSize(size = 50)
    private List<Phone> phones = new ArrayList<>();

    protected Customer() {
    }

    public String getId() { return id; }
    public String getLogin() { return login; }
    public boolean isActive() { return active; }
    public boolean isVerified() { return verified; }
    public boolean isVip() { return vip; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmailAddress() { return emailAddress; }
    public String getPreferredContact() { return preferredContact; }
    public String getSegment() { return segment; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public boolean isMarketingConsent() { return marketingConsent; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public String getImage() { return image; }
    public String getImageSource() { return imageSource; }
    public String getDepartment() { return department; }
    public String getWeb() { return web; }
    public String getNote() { return note; }
    public String getAddress() { return address; }
    public Instant getOnboardedDate() { return onboardedDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getModifiedAt() { return modifiedAt; }
    public List<Address> getAddresses() { return addresses; }
    public List<Phone> getPhones() { return phones; }
}
