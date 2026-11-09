package com.datamesh.customer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.datamesh.customer.entity.Country;

public interface CountryRepository extends JpaRepository<Country, String> {
}
