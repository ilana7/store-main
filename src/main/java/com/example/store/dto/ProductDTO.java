package com.example.store.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProductDTO {
    private Long id;
    private String description;

    /** Ids of the orders containing this product. */
    private List<Long> orders;
}
