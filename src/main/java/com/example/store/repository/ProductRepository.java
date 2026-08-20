package com.example.store.repository;

import com.example.store.entity.Product;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Products are serialised with the ids of the orders containing them. */
    @Override
    @EntityGraph(attributePaths = "orders")
    List<Product> findAll();

    @Override
    @EntityGraph(attributePaths = "orders")
    Optional<Product> findById(Long id);
}
