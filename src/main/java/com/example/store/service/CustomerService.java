package com.example.store.service;

import com.example.store.entity.Customer;
import com.example.store.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CustomerService {

    /**
     * Escape character for LIKE patterns. Deliberately not a backslash: that would have to be escaped again in the JPQL
     * string literal, which is easy to get subtly wrong.
     */
    private static final String LIKE_ESCAPE = "!";

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    /** Returns every customer when no query is supplied, otherwise those matching it. */
    @Transactional(readOnly = true)
    public List<Customer> search(String query) {
        if (!StringUtils.hasText(query)) {
            return findAll();
        }
        return customerRepository.searchByName(toLikePattern(query));
    }

    /**
     * Builds a case-insensitive "contains" pattern. The wildcards a user might type are escaped so that a query of "%"
     * matches a literal percent sign rather than every customer.
     */
    private static String toLikePattern(String query) {
        String escaped = query.trim()
                .replace(LIKE_ESCAPE, LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
        return "%" + escaped.toLowerCase(Locale.ROOT) + "%";
    }

    @Transactional
    public Customer create(Customer customer) {
        return customerRepository.save(customer);
    }
}
