package com.kmgtechnologies.ecommerce.service;

import com.kmgtechnologies.ecommerce.dto.OrderResponse;
import com.kmgtechnologies.ecommerce.entity.Cart;
import com.kmgtechnologies.ecommerce.entity.Category;
import com.kmgtechnologies.ecommerce.entity.Customer;
import com.kmgtechnologies.ecommerce.entity.Order;
import com.kmgtechnologies.ecommerce.entity.OrderStatus;
import com.kmgtechnologies.ecommerce.entity.Product;
import com.kmgtechnologies.ecommerce.exception.EmptyCartException;
import com.kmgtechnologies.ecommerce.exception.InsufficientStockException;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.repository.CartRepository;
import com.kmgtechnologies.ecommerce.repository.CustomerRepository;
import com.kmgtechnologies.ecommerce.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kmgtechnologies.ecommerce.event.OrderPlacedEvent;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrderService orderService;
    private Customer alice;
    private Product laptop;
    private Product mouse;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, cartRepository, customerRepository,
                eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
        alice = new Customer("alice@example.com", "Alice");
        ReflectionTestUtils.setField(alice, "id", CUSTOMER_ID);
        Category electronics = new Category("Electronics");
        laptop = product(10L, "Laptop", "999.99", 5, electronics);
        mouse = product(11L, "Mouse", "19.90", 1, electronics);
    }

    private static Product product(Long id, String name, String price, int stock, Category category) {
        Product product = new Product(name, null, new BigDecimal(price), stock, category);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    @Test
    void placeOrder_createsOrderDecrementsStockAndClearsCart() {
        Cart cart = new Cart(alice);
        cart.addItem(laptop, 2);
        cart.addItem(mouse, 1);
        when(cartRepository.findWithItemsByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.placeOrder(CUSTOMER_ID);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.totalAmount()).isEqualByComparingTo("2019.88");
        assertThat(response.items()).hasSize(2);
        assertThat(laptop.getStock()).isEqualTo(3);
        assertThat(mouse.getStock()).isZero();
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void placeOrder_rejectsEmptyCart() {
        when(cartRepository.findWithItemsByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(new Cart(alice)));

        assertThatThrownBy(() -> orderService.placeOrder(CUSTOMER_ID))
                .isInstanceOf(EmptyCartException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_rejectsCustomerWithoutCart() {
        when(cartRepository.findWithItemsByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());
        when(customerRepository.existsById(CUSTOMER_ID)).thenReturn(true);

        assertThatThrownBy(() -> orderService.placeOrder(CUSTOMER_ID))
                .isInstanceOf(EmptyCartException.class);
    }

    @Test
    void placeOrder_throwsWhenCustomerDoesNotExist() {
        when(cartRepository.findWithItemsByCustomerId(99L)).thenReturn(Optional.empty());
        when(customerRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> orderService.placeOrder(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Customer with id 99 not found");
    }

    @Test
    void placeOrder_rejectsInsufficientStockWithoutSavingOrClearingCart() {
        Cart cart = new Cart(alice);
        cart.addItem(laptop, 2);
        mouse.setStock(10);
        cart.addItem(mouse, 5);
        mouse.setStock(3);
        when(cartRepository.findWithItemsByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> orderService.placeOrder(CUSTOMER_ID))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Insufficient stock for 'Mouse': requested 5, available 3");
        verify(orderRepository, never()).save(any());
        assertThat(cart.getItems()).hasSize(2);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void placeOrder_publishesOrderPlacedEvent() {
        Cart cart = new Cart(alice);
        cart.addItem(laptop, 1);
        when(cartRepository.findWithItemsByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.placeOrder(CUSTOMER_ID);

        verify(eventPublisher).publishEvent(new OrderPlacedEvent(null, "alice@example.com", new BigDecimal("999.99")));
    }

    @Test
    void markAsConfirmed_confirmsPendingOrder() {
        Order order = new Order(alice, NOW);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        orderService.markAsConfirmed(5L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void markAsConfirmed_ignoresUnknownOrder() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        orderService.markAsConfirmed(99L);

        verify(orderRepository).findById(99L);
    }

    @Test
    void getOrder_returnsOrderOfCustomer() {
        Order order = new Order(alice, NOW);
        order.addItem(laptop, 1);
        when(orderRepository.findWithItemsById(5L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrder(CUSTOMER_ID, 5L);

        assertThat(response.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(response.items()).singleElement()
                .satisfies(item -> assertThat(item.productName()).isEqualTo("Laptop"));
    }

    @Test
    void getOrder_hidesOrderOfAnotherCustomer() {
        when(orderRepository.findWithItemsById(5L)).thenReturn(Optional.of(new Order(alice, NOW)));

        assertThatThrownBy(() -> orderService.getOrder(2L, 5L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order with id 5 not found");
    }

    @Test
    void listOrders_throwsWhenCustomerDoesNotExist() {
        when(customerRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> orderService.listOrders(99L, PageRequest.of(0, 10)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}