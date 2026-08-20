package com.example.store.entity;

import jakarta.persistence.*;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "product")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String description;

    // Excluded from toString: lazy, see Customer.orders.
    @ManyToMany(mappedBy = "products", fetch = FetchType.LAZY)
    @ToString.Exclude
    private Set<Order> orders = new LinkedHashSet<>();
}
