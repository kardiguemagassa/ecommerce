package com.kmgtechnologies.ecommerce.controller;

import com.kmgtechnologies.ecommerce.dto.OrderResponse;
import com.kmgtechnologies.ecommerce.dto.OrderSummaryResponse;
import com.kmgtechnologies.ecommerce.service.OrderService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Set;

@RestController
@RequestMapping("/api/customers/{customerId}/orders")
public class OrderController {

    private static final Set<String> SORTABLE_PROPERTIES = Set.of("id", "createdAt", "totalAmount");

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@PathVariable Long customerId) {
        OrderResponse order = orderService.placeOrder(customerId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{orderId}")
                .buildAndExpand(order.id())
                .toUri();
        return ResponseEntity.created(location).body(order);
    }

    @GetMapping
    public PagedModel<OrderSummaryResponse> listOrders(
            @PathVariable Long customerId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        SortValidation.requireAllowed(pageable.getSort(), SORTABLE_PROPERTIES);
        return new PagedModel<>(orderService.listOrders(customerId, pageable));
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable Long customerId, @PathVariable Long orderId) {
        return orderService.getOrder(customerId, orderId);
    }
}