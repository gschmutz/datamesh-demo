package com.datamesh.customer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.datamesh.customer.entity.Person;

public interface CustomerRepository extends JpaRepository<Person, String> {
}
