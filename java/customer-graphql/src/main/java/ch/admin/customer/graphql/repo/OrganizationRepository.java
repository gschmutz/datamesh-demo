package ch.admin.customer.graphql.repo;

import ch.admin.customer.graphql.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
}
