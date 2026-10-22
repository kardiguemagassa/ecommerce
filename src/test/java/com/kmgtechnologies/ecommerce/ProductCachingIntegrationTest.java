package com.kmgtechnologies.ecommerce;

import com.kmgtechnologies.ecommerce.dto.AddCartItemRequest;
import com.kmgtechnologies.ecommerce.dto.CategoryRequest;
import com.kmgtechnologies.ecommerce.dto.CustomerRequest;
import com.kmgtechnologies.ecommerce.dto.ProductRequest;
import com.kmgtechnologies.ecommerce.dto.TopSellingProductResponse;
import com.kmgtechnologies.ecommerce.entity.Category;
import com.kmgtechnologies.ecommerce.entity.Product;
import com.kmgtechnologies.ecommerce.notification.EmailSender;
import com.kmgtechnologies.ecommerce.repository.CategoryRepository;
import com.kmgtechnologies.ecommerce.repository.ProductRepository;
import com.kmgtechnologies.ecommerce.service.CartService;
import com.kmgtechnologies.ecommerce.service.CategoryService;
import com.kmgtechnologies.ecommerce.service.CustomerService;
import com.kmgtechnologies.ecommerce.service.OrderService;
import com.kmgtechnologies.ecommerce.service.ProductService;
import com.kmgtechnologies.ecommerce.service.TopSellingProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProductCachingIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private TopSellingProductService topSellingProductService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private EmailSender emailSender;

    private Category category;
    private Product product;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
        category = categoryRepository.save(new Category("Cache " + UUID.randomUUID()));
        product = productRepository.save(new Product("Keyboard", null, new BigDecimal("49.90"), 5, category));
    }

    private void placeOrder(Product orderedProduct, int quantity) {
        Long customerId = customerService.create(
                new CustomerRequest("cache-" + UUID.randomUUID() + "@example.com", "Cache Test")).id();
        cartService.addItem(customerId, new AddCartItemRequest(orderedProduct.getId(), quantity));
        orderService.placeOrder(customerId);
    }

    @Test
    void findById_isServedFromCache() {
        productService.findById(product.getId());

        product.setName("Renamed behind the cache");
        productRepository.save(product);

        assertThat(productService.findById(product.getId()).name()).isEqualTo("Keyboard");
    }

    @Test
    void update_evictsProduct() {
        productService.findById(product.getId());

        productService.update(product.getId(),
                new ProductRequest("Keyboard Pro", null, new BigDecimal("59.90"), 5, category.getId()));

        assertThat(productService.findById(product.getId()).name()).isEqualTo("Keyboard Pro");
    }

    @Test
    void placingAnOrder_evictsOrderedProducts() {
        assertThat(productService.findById(product.getId()).stock()).isEqualTo(5);

        placeOrder(product, 2);

        assertThat(productService.findById(product.getId()).stock()).isEqualTo(3);
    }

    @Test
    void renamingCategory_evictsAllProducts() {
        productService.findById(product.getId());

        categoryService.update(category.getId(), new CategoryRequest("Renamed " + UUID.randomUUID()));

        assertThat(productService.findById(product.getId()).categoryName()).startsWith("Renamed");
    }

    @Test
    void topSelling_isCachedUntilExpiry() {
        placeOrder(product, 1);
        List<TopSellingProductResponse> first = topSellingProductService.findTopSelling(50);

        placeOrder(product, 2);

        assertThat(topSellingProductService.findTopSelling(50)).isEqualTo(first);
    }
}