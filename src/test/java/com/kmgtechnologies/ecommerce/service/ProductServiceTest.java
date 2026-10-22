package com.kmgtechnologies.ecommerce.service;

import com.kmgtechnologies.ecommerce.dto.ProductRequest;
import com.kmgtechnologies.ecommerce.dto.ProductResponse;
import com.kmgtechnologies.ecommerce.entity.Category;
import com.kmgtechnologies.ecommerce.entity.Product;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.repository.CategoryRepository;
import com.kmgtechnologies.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Pageable PAGE = PageRequest.of(0, 20);

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    private ProductRequest laptopRequest(Long categoryId) {
        return new ProductRequest("  Laptop  ", "15 inch", new BigDecimal("999.99"), 10, categoryId);
    }

    @Test
    void search_replacesNullNameWithEmptyString() {
        when(productRepository.search(1L, "", PAGE)).thenReturn(Page.empty());

        productService.search(1L, null, PAGE);

        verify(productRepository).search(1L, "", PAGE);
    }

    @Test
    void search_stripsName() {
        when(productRepository.search(null, "lap", PAGE)).thenReturn(Page.empty());

        productService.search(null, "  lap ", PAGE);

        verify(productRepository).search(null, "lap", PAGE);
    }

    @Test
    void findById_throwsWhenProductDoesNotExist() {
        when(productRepository.findResponseById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product with id 99 not found");
    }

    @Test
    void create_savesProductWithCategory() {
        Category electronics = new Category("Electronics");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.create(laptopRequest(1L));

        assertThat(response.name()).isEqualTo("Laptop");
        assertThat(response.price()).isEqualByComparingTo("999.99");
        assertThat(response.categoryName()).isEqualTo("Electronics");
    }

    @Test
    void create_throwsWhenCategoryDoesNotExist() {
        when(categoryRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(laptopRequest(42L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Category with id 42 not found");
        verify(productRepository, never()).save(any());
    }

    @Test
    void update_changesAllFieldsIncludingCategory() {
        Category books = new Category("Books");
        Category electronics = new Category("Electronics");
        Product product = new Product("Old", "old", new BigDecimal("1.00"), 1, books);
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(electronics));

        ProductResponse response = productService.update(5L, laptopRequest(2L));

        assertThat(product.getName()).isEqualTo("Laptop");
        assertThat(product.getStock()).isEqualTo(10);
        assertThat(product.getCategory()).isSameAs(electronics);
        assertThat(response.categoryName()).isEqualTo("Electronics");
    }

    @Test
    void update_throwsWhenProductDoesNotExist() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(99L, laptopRequest(1L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesExistingProduct() {
        when(productRepository.existsById(5L)).thenReturn(true);

        productService.delete(5L);

        verify(productRepository).deleteById(5L);
    }

    @Test
    void delete_throwsWhenProductDoesNotExist() {
        when(productRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> productService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(productRepository, never()).deleteById(any());
    }
}