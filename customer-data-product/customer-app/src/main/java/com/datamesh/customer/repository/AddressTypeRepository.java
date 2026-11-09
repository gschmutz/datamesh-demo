package com.datamesh.customer.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.datamesh.customer.entity.AddressType;

public interface AddressTypeRepository extends JpaRepository<AddressType, Integer> {

    Optional<AddressType> findByName(String name);
}
