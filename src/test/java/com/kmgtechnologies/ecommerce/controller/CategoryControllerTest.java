package com.kmgtechnologies.ecommerce.controller;

import org.springframework.test.context.ActiveProfiles;
import com.kmgtechnologies.ecommerce.dto.CategoryRequest;
import com.kmgtechnologies.ecommerce.dto.CategoryResponse;
import com.kmgtechnologies.ecommerce.exception.DuplicateResourceException;
import com.kmgtechnologies.ecommerce.exception.ResourceInUseException;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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

@WebMvcTest(CategoryController.class)
@ActiveProfiles("test")
class CategoryControllerTest {

    private static final String BOOKS_JSON = """
            {"name": "Books"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void findAll_returnsCategories() throws Exception {
        when(categoryService.findAll()).thenReturn(List.of(
                new CategoryResponse(1L, "Books"),
                new CategoryResponse(2L, "Electronics")));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Books"));
    }

    @Test
    void findById_returnsCategory() throws Exception {
        when(categoryService.findById(1L)).thenReturn(new CategoryResponse(1L, "Books"));

        mockMvc.perform(get("/api/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Books"));
    }

    @Test
    void findById_returns404WhenCategoryDoesNotExist() throws Exception {
        when(categoryService.findById(99L)).thenThrow(new ResourceNotFoundException("Category", 99L));

        mockMvc.perform(get("/api/categories/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Category with id 99 not found"));
    }

    @Test
    void create_returns201WithLocation() throws Exception {
        when(categoryService.create(any(CategoryRequest.class))).thenReturn(new CategoryResponse(1L, "Books"));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOKS_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/categories/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Books"));
    }

    @Test
    void create_returns400WhenNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Name is required"));

        verifyNoInteractions(categoryService);
    }

    @Test
    void create_returns409WhenNameAlreadyExists() throws Exception {
        when(categoryService.create(any(CategoryRequest.class)))
                .thenThrow(new DuplicateResourceException("Category 'Books' already exists"));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOKS_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    void update_returnsUpdatedCategory() throws Exception {
        when(categoryService.update(any(Long.class), any(CategoryRequest.class)))
                .thenReturn(new CategoryResponse(1L, "Books"));

        mockMvc.perform(put("/api/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOKS_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Books"));
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/categories/1"))
                .andExpect(status().isNoContent());

        verify(categoryService).delete(1L);
    }

    @Test
    void delete_returns409WhenCategoryHasProducts() throws Exception {
        doThrow(new ResourceInUseException("Category 1 still contains products"))
                .when(categoryService).delete(1L);

        mockMvc.perform(delete("/api/categories/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Resource in use"));
    }
}