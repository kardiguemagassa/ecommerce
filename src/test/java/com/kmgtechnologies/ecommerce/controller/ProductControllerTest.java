package com.kmgtechnologies.ecommerce.controller;

import com.kmgtechnologies.ecommerce.dto.ProductRequest;
import com.kmgtechnologies.ecommerce.dto.ProductResponse;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.service.ProductService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    private static final ProductResponse LAPTOP = new ProductResponse(
            1L, "Laptop", "15 inch", new BigDecimal("999.99"), 10, 2L, "Electronics");

    private static final String LAPTOP_JSON = """
            {"name": "Laptop", "description": "15 inch", "price": 999.99, "stock": 10, "categoryId": 2}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void search_passesFiltersAndReturnsPagedResponse() throws Exception {
        when(productService.search(eq(2L), eq("lap"), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(LAPTOP), invocation.getArgument(2), 1));

        mockMvc.perform(get("/api/products")
                        .param("categoryId", "2")
                        .param("name", "lap")
                        .param("sort", "price,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Laptop"))
                .andExpect(jsonPath("$.content[0].categoryName").value("Electronics"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.number").value(0));
    }

    @Test
    void search_usesDefaultPageSizeAndSortByName() throws Exception {
        when(productService.search(isNull(), isNull(), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(2), 0));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).search(isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by("name"));
    }

    @Test
    void search_rejectsUnknownSortProperty() throws Exception {
        mockMvc.perform(get("/api/products").param("sort", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Cannot sort by 'password'")));

        verifyNoInteractions(productService);
    }

    @Test
    void findById_returnsProduct() throws Exception {
        when(productService.findById(1L)).thenReturn(LAPTOP);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(999.99));
    }

    @Test
    void findById_returns404WhenProductDoesNotExist() throws Exception {
        when(productService.findById(99L)).thenThrow(new ResourceNotFoundException("Product", 99L));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_returns201WithLocation() throws Exception {
        when(productService.create(any(ProductRequest.class))).thenReturn(LAPTOP);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LAPTOP_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/products/1"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void create_returns400WithAllInvalidFields() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "price": 0, "stock": -1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.price").value("Price must be greater than 0"))
                .andExpect(jsonPath("$.errors.stock").value("Stock must be zero or positive"))
                .andExpect(jsonPath("$.errors.categoryId").value("Category is required"));

        verifyNoInteractions(productService);
    }

    @Test
    void create_returns400WhenPriceHasTooManyDecimals() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Laptop", "price": 19.999, "stock": 1, "categoryId": 2}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.price").exists());
    }

    @Test
    void update_returnsUpdatedProduct() throws Exception {
        when(productService.update(eq(1L), any(ProductRequest.class))).thenReturn(LAPTOP);

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LAPTOP_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Laptop"));
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());

        verify(productService).delete(1L);
    }
}