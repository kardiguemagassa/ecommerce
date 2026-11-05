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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.BAD_REQUEST;
import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.CONFLICT;
import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.IMAGE_REJECTED;
import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.NOT_FOUND;
import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.STORAGE_UNAVAILABLE;

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

    @Operation(summary = "Replace my profile photo",
            description = """
                    Optional photo, shown next to the name in the navigation bar. Multipart field `file`: JPEG, PNG \
                    or WebP, 1 MB at most (format read from the content). Stored as a square JPEG of 256 x 256 at most \
                    (centred crop), without the metadata of the upload (EXIF, GPS). The previous photo is deleted.""")
    @ApiResponse(responseCode = "200", description = "Photo stored; profile with its new `avatarUrl`")
    @ApiResponse(responseCode = "409", ref = CONFLICT)
    @ApiResponse(responseCode = "413", ref = IMAGE_REJECTED)
    @ApiResponse(responseCode = "415", ref = IMAGE_REJECTED)
    @ApiResponse(responseCode = "422", ref = IMAGE_REJECTED)
    @ApiResponse(responseCode = "503", ref = STORAGE_UNAVAILABLE)
    @PutMapping(path = "/api/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CustomerResponse replaceAvatar(@RequestPart("file") MultipartFile file) {
        return customerService.replaceAvatar(currentCustomer.id(), file);
    }

    @Operation(summary = "Delete my profile photo", description = "The photo is erased from the storage.")
    @ApiResponse(responseCode = "204", description = "Photo deleted (or there was none)")
    @DeleteMapping("/api/me/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAvatar() {
        customerService.deleteAvatar(currentCustomer.id());
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
