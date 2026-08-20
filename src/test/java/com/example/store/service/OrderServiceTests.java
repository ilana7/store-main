package com.example.store.service;

import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.entity.Product;
import com.example.store.exception.BadRequestException;
import com.example.store.exception.NotFoundException;
import com.example.store.repository.CustomerRepository;
import com.example.store.repository.OrderRepository;
import com.example.store.repository.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTests {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private OrderService orderService;

    private Customer customer;
    private Product product;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setId(1L);
        customer.setName("John Doe");

        product = new Product();
        product.setId(10L);
        product.setDescription("Widget");
    }

    private Order request(Long customerId, Long... productIds) {
        Order order = new Order();
        order.setDescription("Test Order");
        if (customerId != null) {
            Customer stub = new Customer();
            stub.setId(customerId);
            order.setCustomer(stub);
        }
        Set<Product> products = new LinkedHashSet<>();
        for (Long id : productIds) {
            Product stub = new Product();
            stub.setId(id);
            products.add(stub);
        }
        order.setProducts(products);
        return order;
    }

    @Test
    void createResolvesCustomerAndProductsFromTheDatabase() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.findAllById(anyIterable())).thenReturn(List.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order saved = orderService.create(request(1L, 10L));

        // The stubs the client sent are replaced by the real rows, not merely echoed back.
        assertThat(saved.getCustomer().getName()).isEqualTo("John Doe");
        assertThat(saved.getProducts()).containsExactly(product);
        assertThat(saved.getProducts().iterator().next().getDescription()).isEqualTo("Widget");
    }

    @Test
    void createRejectsAnOrderWithNoProducts() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> orderService.create(request(1L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least one product");
    }

    @Test
    void createRejectsAnOrderWithNoCustomer() {
        assertThatThrownBy(() -> orderService.create(request(null, 10L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("customer id");
    }

    @Test
    void createRejectsAnUnknownCustomer() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.create(request(99L, 10L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Customer 99");
    }

    @Test
    void createRejectsAnUnknownProduct() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.findAllById(anyIterable())).thenReturn(List.of(product));

        assertThatThrownBy(() -> orderService.create(request(1L, 10L, 404L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product 404");
    }

    @Test
    void findByIdRaisesNotFoundForAnUnknownId() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findById(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Order 99");
    }
}
