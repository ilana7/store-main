package com.example.store.service;

import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.entity.Product;
import com.example.store.exception.BadRequestException;
import com.example.store.exception.NotFoundException;
import com.example.store.repository.CustomerRepository;
import com.example.store.repository.OrderRepository;
import com.example.store.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Order findById(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Order", id));
    }

    /**
     * The request carries only references (ids) to an existing customer and to existing products. Resolving them here
     * validates the associations up front and means the response echoes real rows rather than the partially-populated
     * stubs the client sent.
     */
    @Transactional
    public Order create(Order order) {
        order.setCustomer(resolveCustomer(order));
        order.setProducts(resolveProducts(order));
        return orderRepository.save(order);
    }

    private Customer resolveCustomer(Order order) {
        Long customerId =
                order.getCustomer() == null ? null : order.getCustomer().getId();
        if (customerId == null) {
            throw new BadRequestException("An order must reference a customer id");
        }
        return customerRepository.findById(customerId).orElseThrow(() -> new NotFoundException("Customer", customerId));
    }

    private Set<Product> resolveProducts(Order order) {
        Set<Long> productIds = order.getProducts() == null
                ? Set.of()
                : order.getProducts().stream()
                        .map(Product::getId)
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (productIds.isEmpty()) {
            throw new BadRequestException("An order must contain at least one product id");
        }

        List<Product> found = productRepository.findAllById(productIds);
        if (found.size() != productIds.size()) {
            Set<Long> foundIds = found.stream().map(Product::getId).collect(java.util.stream.Collectors.toSet());
            Long missing = productIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .findFirst()
                    .orElseThrow();
            throw new NotFoundException("Product", missing);
        }
        return new LinkedHashSet<>(found);
    }
}
