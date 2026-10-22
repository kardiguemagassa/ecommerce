package com.kmgtechnologies.ecommerce.service;

import com.kmgtechnologies.ecommerce.dto.OrderResponse;
import com.kmgtechnologies.ecommerce.dto.OrderSummaryResponse;
import com.kmgtechnologies.ecommerce.entity.Cart;
import com.kmgtechnologies.ecommerce.entity.CartItem;
import com.kmgtechnologies.ecommerce.entity.Order;
import com.kmgtechnologies.ecommerce.entity.Product;
import com.kmgtechnologies.ecommerce.exception.EmptyCartException;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.repository.CartRepository;
import com.kmgtechnologies.ecommerce.repository.CustomerRepository;
import com.kmgtechnologies.ecommerce.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CustomerRepository customerRepository;
    private final Clock clock;

    public OrderService(OrderRepository orderRepository,
                        CartRepository cartRepository,
                        CustomerRepository customerRepository,
                        Clock clock) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.customerRepository = customerRepository;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse placeOrder(Long customerId) {
        Cart cart = cartRepository.findWithItemsByCustomerId(customerId)
                .orElseThrow(() -> customerRepository.existsById(customerId)
                        ? new EmptyCartException(customerId)
                        : new ResourceNotFoundException("Customer", customerId));
        if (cart.getItems().isEmpty()) {
            throw new EmptyCartException(customerId);
        }

        Order order = new Order(cart.getCustomer(), Instant.now(clock));
        for (CartItem item : cart.getItems()) {
            Product product = item.getProduct();
            product.decreaseStock(item.getQuantity());
            order.addItem(product, item.getQuantity());
        }

        Order saved = orderRepository.save(order);
        cart.clear();
        return OrderResponse.from(saved);
    }

    public OrderResponse getOrder(Long customerId, Long orderId) {
        return orderRepository.findWithItemsById(orderId)
                .filter(order -> Objects.equals(order.getCustomer().getId(), customerId))
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    public Page<OrderSummaryResponse> listOrders(Long customerId, Pageable pageable) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer", customerId);
        }
        return orderRepository.findSummariesByCustomerId(customerId, pageable);
    }
}