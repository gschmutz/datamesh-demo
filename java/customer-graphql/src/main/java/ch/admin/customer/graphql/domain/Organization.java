package ch.admin.customer.graphql.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Maps the {@code organization} table from cr_customer.sql. */
@Entity
@Table(name = "organization")
public class Organization {

    @Id
    private Long id;

    private String name;
    private boolean shared;
    private String domain;

    @Column(name = "domain_assignment")
    private boolean domainAssignment;

    private boolean active;
    private boolean vip;
    private String note;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "member_ids", columnDefinition = "bigint[]")
    private List<Long> memberIds;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "secondary_member_ids", columnDefinition = "bigint[]")
    private List<Long> secondaryMemberIds;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isShared() { return shared; }
    public String getDomain() { return domain; }
    public boolean isDomainAssignment() { return domainAssignment; }
    public boolean isActive() { return active; }
    public boolean isVip() { return vip; }
    public String getNote() { return note; }
    public List<Long> getMemberIds() { return memberIds; }
    public List<Long> getSecondaryMemberIds() { return secondaryMemberIds; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
