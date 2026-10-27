package com.lhbank.customer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.lhbank.customer.entity.Person;

public interface CustomerRepository extends JpaRepository<Person, String> {
}
