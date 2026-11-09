package com.datamesh.customer.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.datamesh.customer.entity.Segment;

public interface SegmentRepository extends JpaRepository<Segment, Integer> {

    Optional<Segment> findByName(String name);
}
