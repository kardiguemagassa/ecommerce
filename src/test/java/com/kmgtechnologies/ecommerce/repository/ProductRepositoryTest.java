package com.kmgtechnologies.ecommerce.repository;

import com.kmgtechnologies.ecommerce.dto.ProductResponse;
import com.kmgtechnologies.ecommerce.entity.Category;
import com.kmgtechnologies.ecommerce.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ProductRepositoryTest {

    private static final Pageable FIRST_PAGE_BY_NAME = PageRequest.of(0, 10, Sort.by("name"));

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Category electronics;
    private Category books;

    @BeforeEach
    void setUp() {
        electronics = entityManager.persist(new Category("Electronics"));
        books = entityManager.persist(new Category("Books"));

        entityManager.persist(new Product("Laptop", "15 inch", new BigDecimal("999.99"), 10, electronics));
        entityManager.persist(new Product("Phone", "6 inch", new BigDecimal("599.00"), 5, electronics));
        entityManager.persist(new Product("Clean Code", "Robert C. Martin", new BigDecimal("39.90"), 20, books));

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void search_withoutFilters_returnsAllProducts() {
        Page<ProductResponse> page = productRepository.search(null, "", FIRST_PAGE_BY_NAME);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(ProductResponse::name).containsExactly("Clean Code", "Laptop", "Phone");
    }

    @Test
    void search_byCategory_returnsOnlyProductsOfThatCategory() {
        Page<ProductResponse> page = productRepository.search(electronics.getId(), "", FIRST_PAGE_BY_NAME);

        assertThat(page.getContent()).extracting(ProductResponse::name).containsExactly("Laptop", "Phone");
    }

    @Test
    void search_byName_isCaseInsensitive() {
        Page<ProductResponse> page = productRepository.search(null, "LAP", FIRST_PAGE_BY_NAME);

        assertThat(page.getContent()).extracting(ProductResponse::name).containsExactly("Laptop");
    }

    @Test
    void search_byCategoryAndName_combinesFilters() {
        Page<ProductResponse> page = productRepository.search(books.getId(), "lap", FIRST_PAGE_BY_NAME);

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void search_returnsCategoryInProjection() {
        Page<ProductResponse> page = productRepository.search(books.getId(), "", FIRST_PAGE_BY_NAME);

        assertThat(page.getContent()).singleElement()
                .satisfies(product -> {
                    assertThat(product.categoryId()).isEqualTo(books.getId());
                    assertThat(product.categoryName()).isEqualTo("Books");
                    assertThat(product.price()).isEqualByComparingTo("39.90");
                });
    }

    @Test
    void search_paginatesResults() {
        Page<ProductResponse> page = productRepository.search(null, "", PageRequest.of(0, 2, Sort.by("name")));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void save_rejectsProductWithoutCategory() {
        Product orphan = new Product("Orphan", null, new BigDecimal("1.00"), 1, null);

        assertThatThrownBy(() -> productRepository.saveAndFlush(orphan)).isInstanceOf(DataIntegrityViolationException.class);
    }
}