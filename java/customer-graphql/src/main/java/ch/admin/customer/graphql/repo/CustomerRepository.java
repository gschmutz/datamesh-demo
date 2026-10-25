package ch.admin.customer.graphql.repo;

import ch.admin.customer.graphql.domain.Customer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByLogin(String login);
}
