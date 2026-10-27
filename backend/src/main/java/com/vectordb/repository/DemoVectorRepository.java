package com.vectordb.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vectordb.model.DemoVector;

public interface DemoVectorRepository extends JpaRepository<DemoVector, Integer> {
    long count();
}
