package com.kmgtechnologies.ecommerce.controller;

import com.kmgtechnologies.ecommerce.dto.CustomerResponse;
import com.kmgtechnologies.ecommerce.dto.OrderResponse;
import com.kmgtechnologies.ecommerce.dto.OrderSummaryResponse;
import com.kmgtechnologies.ecommerce.security.CurrentCustomer;
import com.kmgtechnologies.ecommerce.service.CustomerService;
import com.kmgtechnologies.ecommerce.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.BAD_REQUEST;
import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.NOT_FOUND;

/**
 * Customer accounts are Keycloak users: no sign-up here, the record is created on the first authenticated call.
 * Customers read their own profile; administrators look up any customer and their orders (support).
 */
@Tag(name = "Customers", description = "The signed-in customer, and customer support for administrators")
@RestController
public class CustomerController {

    private final CurrentCustomer currentCustomer;
    private final CustomerService customerService;
    private final OrderService orderService;

    public CustomerController(CurrentCustomer currentCustomer, CustomerService customerService,
                              OrderService orderService) {
        this.currentCustomer = currentCustomer;
        this.customerService = customerService;
        this.orderService = orderService;
    }

    @Operation(summary = "My customer profile",
            description = "Created on the first call from the Keycloak user; e-mail and name follow Keycloak.")
    @ApiResponse(responseCode = "200", description = "Profile of the signed-in customer")
    @GetMapping("/api/me")
    public CustomerResponse me() {
        return currentCustomer.profile();
    }

    @Operation(summary = "Get a customer (administrators)")
    @ApiResponse(responseCode = "200", description = "Customer found")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @GetMapping("/api/admin/customers/{id}")
    public CustomerResponse findById(@PathVariable Long id) {
        return customerService.findById(id);
    }

    @Operation(summary = "List the orders of a customer (administrators)",
            description = "Paginated summaries, newest first by default. Sortable properties: id, createdAt, totalAmount.")
    @ApiResponse(responseCode = "200", description = "Page of order summaries returned")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @GetMapping("/api/admin/customers/{id}/orders")
    public PagedModel<OrderSummaryResponse> listOrders(
            @PathVariable Long id,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        SortValidation.requireAllowed(pageable.getSort(), OrderController.SORTABLE_PROPERTIES);
        return new PagedModel<>(orderService.listOrders(id, pageable));
    }

    @Operation(summary = "Get an order of a customer (administrators)")
    @ApiResponse(responseCode = "200", description = "Order found")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @GetMapping("/api/admin/customers/{id}/orders/{orderId}")
    public OrderResponse getOrder(@PathVariable Long id, @PathVariable Long orderId) {
        return orderService.getOrder(id, orderId);
    }
}
