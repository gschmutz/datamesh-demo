package com.datamesh.customer.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.datamesh.customer.entity.PhoneType;

public interface PhoneTypeRepository extends JpaRepository<PhoneType, Integer> {

    Optional<PhoneType> findByName(String name);
}
