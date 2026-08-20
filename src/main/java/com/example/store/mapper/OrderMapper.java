package com.example.store.mapper;

import com.example.store.dto.NestedCustomerDTO;
import com.example.store.dto.NestedProductDTO;
import com.example.store.dto.OrderDTO;
import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.entity.Product;

import org.mapstruct.Mapper;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    OrderDTO orderToOrderDTO(Order order);

    List<OrderDTO> ordersToOrderDTOs(List<Order> orders);

    NestedCustomerDTO customerToNestedCustomerDTO(Customer customer);

    NestedProductDTO productToNestedProductDTO(Product product);

    /** Sorted by id so the response is stable between calls. */
    default List<NestedProductDTO> productsToNestedProductDTOs(Set<Product> products) {
        if (products == null) {
            return List.of();
        }
        return products.stream()
                .sorted(Comparator.comparing(Product::getId))
                .map(this::productToNestedProductDTO)
                .toList();
    }
}
