package com.kmgtechnologies.ecommerce;

import org.springframework.test.context.ActiveProfiles;
import com.kmgtechnologies.ecommerce.dto.AddCartItemRequest;
import com.kmgtechnologies.ecommerce.dto.CustomerRequest;
import com.kmgtechnologies.ecommerce.dto.OrderResponse;
import com.kmgtechnologies.ecommerce.entity.Category;
import com.kmgtechnologies.ecommerce.entity.OrderStatus;
import com.kmgtechnologies.ecommerce.entity.Product;
import com.kmgtechnologies.ecommerce.exception.InsufficientStockException;
import com.kmgtechnologies.ecommerce.notification.EmailSender;
import com.kmgtechnologies.ecommerce.repository.CategoryRepository;
import com.kmgtechnologies.ecommerce.repository.OrderRepository;
import com.kmgtechnologies.ecommerce.repository.ProductRepository;
import com.kmgtechnologies.ecommerce.service.CartService;
import com.kmgtechnologies.ecommerce.service.CustomerService;
import com.kmgtechnologies.ecommerce.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class OrderConfirmationIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @MockitoBean
    private EmailSender emailSender;

    private record Scenario(Long customerId, String email, Product product) {
    }

    private Scenario customerWithCart(int stock, int quantity) {
        String suffix = UUID.randomUUID().toString();
        Category category = categoryRepository.save(new Category("Async " + suffix));
        Product product = productRepository.save(
                new Product("Keyboard", null, new BigDecimal("49.90"), stock, category));
        String email = "async-" + suffix + "@example.com";
        Long customerId = customerService.create(new CustomerRequest(email, "Async Test")).id();
        cartService.addItem(customerId, new AddCartItemRequest(product.getId(), quantity));
        return new Scenario(customerId, email, product);
    }

    @Test
    void placeOrder_isConfirmedAsynchronouslyAfterCommit() {
        Scenario scenario = customerWithCart(5, 2);

        OrderResponse order = orderService.placeOrder(scenario.customerId());

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(orderRepository.findById(order.id()).orElseThrow().getStatus())
                        .isEqualTo(OrderStatus.CONFIRMED));
        verify(emailSender).sendOrderConfirmation(eq(scenario.email()), eq(order.id()), any(BigDecimal.class));
    }

    @Test
    void failedOrder_sendsNoConfirmation() {
        Scenario scenario = customerWithCart(5, 2);
        Product product = scenario.product();
        product.setStock(1);
        productRepository.save(product);

        assertThatThrownBy(() -> orderService.placeOrder(scenario.customerId()))
                .isInstanceOf(InsufficientStockException.class);

        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                verify(emailSender, never()).sendOrderConfirmation(anyString(), anyLong(), any()));
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(1);
    }
}