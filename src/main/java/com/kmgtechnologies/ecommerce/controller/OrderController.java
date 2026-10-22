package com.kmgtechnologies.ecommerce.controller;

import com.kmgtechnologies.ecommerce.dto.OrderResponse;
import com.kmgtechnologies.ecommerce.dto.OrderSummaryResponse;
import com.kmgtechnologies.ecommerce.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
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

import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.BAD_REQUEST;
import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.CONFLICT;
import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.NOT_FOUND;

@Tag(name = "Orders", description = "Orders placed from the cart, confirmed asynchronously")
@RestController
@RequestMapping("/api/customers/{customerId}/orders")
public class OrderController {

    private static final Set<String> SORTABLE_PROPERTIES = Set.of("id", "createdAt", "totalAmount");

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "Place an order from the cart",
            description = """
                    Atomically snapshots prices, decrements stock and empties the cart. \
                    The order is returned with status PENDING; a confirmation email is sent asynchronously \
                    after commit, then the status becomes CONFIRMED.""")
    @ApiResponse(responseCode = "201", description = "Order created (PENDING); URI in the Location header")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = CONFLICT)
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@PathVariable Long customerId) {
        OrderResponse order = orderService.placeOrder(customerId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{orderId}")
                .buildAndExpand(order.id())
                .toUri();
        return ResponseEntity.created(location).body(order);
    }

    @Operation(summary = "List the customer's orders",
            description = "Paginated summaries, newest first by default. Sortable properties: id, createdAt, totalAmount.")
    @ApiResponse(responseCode = "200", description = "Page of order summaries returned")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @GetMapping
    public PagedModel<OrderSummaryResponse> listOrders(
            @PathVariable Long customerId,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        SortValidation.requireAllowed(pageable.getSort(), SORTABLE_PROPERTIES);
        return new PagedModel<>(orderService.listOrders(customerId, pageable));
    }

    @Operation(summary = "Get an order with its lines",
            description = "Returns 404 if the order does not exist or belongs to another customer.")
    @ApiResponse(responseCode = "200", description = "Order found")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable Long customerId, @PathVariable Long orderId) {
        return orderService.getOrder(customerId, orderId);
    }
}
