package com.example.store.controller;

import com.example.store.entity.Order;
import com.example.store.entity.Product;
import com.example.store.exception.NotFoundException;
import com.example.store.mapper.ProductMapperImpl;
import com.example.store.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import(ProductMapperImpl.class)
class ProductControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(10L);
        product.setDescription("Widget");

        // Deliberately out of order, to prove the response sorts the ids.
        Set<Order> orders = new LinkedHashSet<>();
        orders.add(order(3L));
        orders.add(order(1L));
        product.setOrders(orders);
    }

    private static Order order(Long id) {
        Order order = new Order();
        order.setId(id);
        return order;
    }

    @Test
    void testGetAllProductsIncludesContainingOrderIds() throws Exception {
        when(productService.findAll()).thenReturn(List.of(product));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].description").value("Widget"))
                .andExpect(jsonPath("$[0].orders").value(org.hamcrest.Matchers.contains(1, 3)));
    }

    @Test
    void testGetProductById() throws Exception {
        when(productService.findById(10L)).thenReturn(product);

        mockMvc.perform(get("/products/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.orders").value(org.hamcrest.Matchers.contains(1, 3)));
    }

    @Test
    void testGetProductByIdNotFound() throws Exception {
        when(productService.findById(99L)).thenThrow(new NotFoundException("Product", 99L));

        mockMvc.perform(get("/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product 99 not found"));
    }

    @Test
    void testCreateProduct() throws Exception {
        Product created = new Product();
        created.setId(11L);
        created.setDescription("Gadget");
        when(productService.create(any(Product.class))).thenReturn(created);

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(created)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.description").value("Gadget"))
                .andExpect(jsonPath("$.orders").isEmpty());
    }
}
