package com.datamesh.customer.dp.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import com.datamesh.customer.dp.entity.Customer;

/** Read-only: only query methods are exposed, there is no save or delete. */
public interface CustomerRepository extends Repository<Customer, String> {

    Page<Customer> findAll(Pageable pageable);

    Optional<Customer> findById(String id);
}
