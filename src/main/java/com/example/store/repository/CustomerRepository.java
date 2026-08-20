package com.example.store.repository;

import com.example.store.entity.Customer;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** Customers are serialised with their orders, so they are fetch-joined rather than lazy. */
    @Override
    @EntityGraph(attributePaths = "orders")
    List<Customer> findAll();

    /**
     * Case-insensitive substring match against the customer name. LOWER(name) mirrors the expression the trigram index
     * is built on, so the index remains usable.
     */
    @Query("SELECT c FROM Customer c WHERE LOWER(c.name) LIKE :pattern ESCAPE '!'")
    @EntityGraph(attributePaths = "orders")
    List<Customer> searchByName(@Param("pattern") String pattern);
}
