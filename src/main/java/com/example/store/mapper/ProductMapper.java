package com.example.store.mapper;

import com.example.store.dto.ProductDTO;
import com.example.store.entity.Order;
import com.example.store.entity.Product;

import org.mapstruct.Mapper;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductDTO productToProductDTO(Product product);

    List<ProductDTO> productsToProductDTOs(List<Product> products);

    /**
     * The API exposes only the ids of the orders containing a product. Sorted so the response is stable between calls.
     */
    default List<Long> ordersToOrderIds(Set<Order> orders) {
        if (orders == null) {
            return List.of();
        }
        return orders.stream()
                .map(Order::getId)
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}
