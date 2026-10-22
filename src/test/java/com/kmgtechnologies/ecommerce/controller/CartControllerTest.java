package com.kmgtechnologies.ecommerce.controller;

import com.kmgtechnologies.ecommerce.dto.AddCartItemRequest;
import com.kmgtechnologies.ecommerce.dto.CartItemResponse;
import com.kmgtechnologies.ecommerce.dto.CartResponse;
import com.kmgtechnologies.ecommerce.exception.InsufficientStockException;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.service.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
class CartControllerTest {

    private static final CartResponse CART_WITH_LAPTOP = new CartResponse(
            1L,
            List.of(new CartItemResponse(10L, "Laptop", new BigDecimal("999.99"), 2, new BigDecimal("1999.98"))),
            2,
            new BigDecimal("1999.98"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @Test
    void getCart_returnsCart() throws Exception {
        when(cartService.getCart(1L)).thenReturn(CART_WITH_LAPTOP);

        mockMvc.perform(get("/api/customers/1/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("Laptop"))
                .andExpect(jsonPath("$.items[0].subtotal").value(1999.98))
                .andExpect(jsonPath("$.totalQuantity").value(2))
                .andExpect(jsonPath("$.total").value(1999.98));
    }

    @Test
    void getCart_returns404WhenCustomerDoesNotExist() throws Exception {
        when(cartService.getCart(99L)).thenThrow(new ResourceNotFoundException("Customer", 99L));

        mockMvc.perform(get("/api/customers/99/cart"))
                .andExpect(status().isNotFound());
    }

    @Test
    void addItem_returnsUpdatedCart() throws Exception {
        when(cartService.addItem(eq(1L), any(AddCartItemRequest.class))).thenReturn(CART_WITH_LAPTOP);

        mockMvc.perform(post("/api/customers/1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId": 10, "quantity": 2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1999.98));
    }

    @Test
    void addItem_returns400WhenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/customers/1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.productId").value("Product is required"))
                .andExpect(jsonPath("$.errors.quantity").value("Quantity must be positive"));

        verifyNoInteractions(cartService);
    }

    @Test
    void addItem_returns409WhenStockIsInsufficient() throws Exception {
        when(cartService.addItem(eq(1L), any(AddCartItemRequest.class)))
                .thenThrow(new InsufficientStockException("Laptop", 11, 10));

        mockMvc.perform(post("/api/customers/1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId": 10, "quantity": 11}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Insufficient stock for 'Laptop': requested 11, available 10"));
    }

    @Test
    void removeItem_returnsUpdatedCart() throws Exception {
        when(cartService.removeItem(1L, 10L)).thenReturn(CartResponse.empty(1L));

        mockMvc.perform(delete("/api/customers/1/cart/items/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void removeItem_returns404WhenProductIsNotInCart() throws Exception {
        when(cartService.removeItem(1L, 10L))
                .thenThrow(new ResourceNotFoundException("Product 10 is not in the cart"));

        mockMvc.perform(delete("/api/customers/1/cart/items/10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product 10 is not in the cart"));
    }

    @Test
    void clear_returns204() throws Exception {
        mockMvc.perform(delete("/api/customers/1/cart"))
                .andExpect(status().isNoContent());

        verify(cartService).clear(1L);
    }
}