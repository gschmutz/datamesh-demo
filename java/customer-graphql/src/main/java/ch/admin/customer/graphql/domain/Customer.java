package ch.admin.customer.graphql.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;

/** Maps the {@code customer} table from cr_customer.sql. */
@Entity
@Table(name = "customer")
public class Customer {

    @Id
    private Long id;

    private String login;
    private String email;
    private String firstname;
    private String lastname;
    private String image;

    @Column(name = "image_source")
    private String imageSource;

    private String department;

    /** Primary organization (nullable FK). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    /** Secondary organizations (M:N join table). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "customer_secondary_organization",
        joinColumns = @JoinColumn(name = "customer_id"),
        inverseJoinColumns = @JoinColumn(name = "organization_id"))
    private List<Organization> secondaryOrganizations;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    private boolean active;
    private boolean verified;
    private boolean vip;
    private String phone;
    private String mobile;
    private String fax;
    private String web;
    private String street;
    private String city;
    private String zip;
    private String country;
    private String note;
    private String address;

    public Long getId() { return id; }
    public String getLogin() { return login; }
    public String getEmail() { return email; }
    public String getFirstname() { return firstname; }
    public String getLastname() { return lastname; }
    public String getImage() { return image; }
    public String getImageSource() { return imageSource; }
    public String getDepartment() { return department; }
    public Organization getOrganization() { return organization; }
    public List<Organization> getSecondaryOrganizations() { return secondaryOrganizations; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public boolean isActive() { return active; }
    public boolean isVerified() { return verified; }
    public boolean isVip() { return vip; }
    public String getPhone() { return phone; }
    public String getMobile() { return mobile; }
    public String getFax() { return fax; }
    public String getWeb() { return web; }
    public String getStreet() { return street; }
    public String getCity() { return city; }
    public String getZip() { return zip; }
    public String getCountry() { return country; }
    public String getNote() { return note; }
    public String getAddress() { return address; }
}
