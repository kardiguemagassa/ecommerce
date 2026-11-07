package com.kmgtechnologies.ecommerce.controller;

import com.kmgtechnologies.ecommerce.dto.TopSellingProductResponse;
import com.kmgtechnologies.ecommerce.service.TopSellingProductService;
import org.springframework.test.context.ActiveProfiles;
import com.kmgtechnologies.ecommerce.dto.ProductImageOrderRequest;
import com.kmgtechnologies.ecommerce.dto.ProductImageResponse;
import com.kmgtechnologies.ecommerce.dto.ProductRequest;
import com.kmgtechnologies.ecommerce.dto.ProductResponse;
import com.kmgtechnologies.ecommerce.exception.ImageLimitException;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.exception.UnsupportedImageException;
import com.kmgtechnologies.ecommerce.service.ProductService;
import com.kmgtechnologies.ecommerce.security.BffTestConfiguration;
import com.kmgtechnologies.ecommerce.security.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, BffTestConfiguration.class})
@ActiveProfiles("test")
class ProductControllerTest {

    private static final ProductResponse LAPTOP = new ProductResponse(
            1L, "Laptop", "15 inch", new BigDecimal("999.99"), 10, 2L, "Electronics",
            "http://media.test/ecommerce-media/products/laptop.jpg",
            List.of(new ProductImageResponse(10L, "http://media.test/ecommerce-media/products/laptop.jpg")));

    private static final MockMultipartFile IMAGE = new MockMultipartFile("file", "laptop.png", "image/png", new byte[]{1});

    private static final String LAPTOP_JSON = """
            {"name": "Laptop", "description": "15 inch", "price": 999.99, "stock": 10, "categoryId": 2}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private TopSellingProductService topSellingProductService;

    /** Requests are authenticated by @WithMockUser: no token to decode, no Keycloak. */
    @MockitoBean
    private JwtDecoder jwtDecoder;

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
                .andExpect(jsonPath("$.price").value(999.99))
                .andExpect(jsonPath("$.imageUrl").value("http://media.test/ecommerce-media/products/laptop.jpg"));
    }

    @Test
    void findById_returns404WhenProductDoesNotExist() throws Exception {
        when(productService.findById(99L)).thenThrow(new ResourceNotFoundException("Product", 99L));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
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
    @WithMockUser(roles = "ADMIN")
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
    @WithMockUser(roles = "ADMIN")
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
    @WithMockUser(roles = "ADMIN")
    void update_returnsUpdatedProduct() throws Exception {
        when(productService.update(eq(1L), any(ProductRequest.class))).thenReturn(LAPTOP);

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LAPTOP_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Laptop"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());

        verify(productService).delete(1L);
    }

    @Test
    void topSelling_returnsRanking() throws Exception {
        when(topSellingProductService.findTopSelling(3))
                .thenReturn(List.of(new TopSellingProductResponse(1L, "Laptop", 12L)));

        mockMvc.perform(get("/api/products/top-selling").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productName").value("Laptop"))
                .andExpect(jsonPath("$[0].totalQuantity").value(12));
    }

    @Test
    void topSelling_rejectsLimitAbove50() throws Exception {
        mockMvc.perform(get("/api/products/top-selling").param("limit", "51"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(topSellingProductService);
    }


    @Test
    @WithMockUser(roles = "CUSTOMER")
    void create_isForbiddenToCustomers() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LAPTOP_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(productService);
    }

    @Test
    void update_requiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LAPTOP_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(productService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void replaceImage_returnsTheProductWithItsNewImage() throws Exception {
        when(productService.replaceImage(eq(1L), any())).thenReturn(LAPTOP);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/products/1/image").file(IMAGE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("http://media.test/ecommerce-media/products/laptop.jpg"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void replaceImage_returns415ForAFileThatIsNotAnImage() throws Exception {
        when(productService.replaceImage(eq(1L), any())).thenThrow(new UnsupportedImageException());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/products/1/image").file(IMAGE))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void replaceImage_returns400WithoutFile() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/products/1/image"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void replaceImage_isForbiddenToCustomers() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/products/1/image").file(IMAGE))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productService);
    }

    @Test
    void replaceImage_requiresAuthentication() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/products/1/image").file(IMAGE))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(productService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteImage_returns204() throws Exception {
        mockMvc.perform(delete("/api/products/1/image"))
                .andExpect(status().isNoContent());

        verify(productService).deleteImage(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void addImage_returnsTheProductWithItsViews() throws Exception {
        when(productService.addImage(eq(1L), any())).thenReturn(LAPTOP);

        mockMvc.perform(multipart(HttpMethod.POST, "/api/products/1/images").file(IMAGE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[0].id").value(10))
                .andExpect(jsonPath("$.images[0].url").value("http://media.test/ecommerce-media/products/laptop.jpg"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void addImage_returns409WithItsCode_beyondFourViews() throws Exception {
        when(productService.addImage(eq(1L), any())).thenThrow(new ImageLimitException(4));

        mockMvc.perform(multipart(HttpMethod.POST, "/api/products/1/images").file(IMAGE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMAGE_LIMIT"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void addImage_isForbiddenToCustomers() throws Exception {
        mockMvc.perform(multipart(HttpMethod.POST, "/api/products/1/images").file(IMAGE))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void removeImage_returnsTheProduct() throws Exception {
        when(productService.removeImage(1L, 10L)).thenReturn(LAPTOP);

        mockMvc.perform(delete("/api/products/1/images/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reorderImages_passesTheNewOrder() throws Exception {
        when(productService.reorderImages(eq(1L), any())).thenReturn(LAPTOP);

        mockMvc.perform(put("/api/products/1/images/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\": [11, 10]}"))
                .andExpect(status().isOk());

        verify(productService).reorderImages(1L, new ProductImageOrderRequest(List.of(11L, 10L)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reorderImages_returns400WithoutIds() throws Exception {
        mockMvc.perform(put("/api/products/1/images/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.imageIds").exists());

        verifyNoInteractions(productService);
    }
}
