package com.kmgtechnologies.ecommerce.service;

import com.kmgtechnologies.ecommerce.dto.AddCartItemRequest;
import com.kmgtechnologies.ecommerce.dto.CartResponse;
import com.kmgtechnologies.ecommerce.entity.Cart;
import com.kmgtechnologies.ecommerce.entity.Category;
import com.kmgtechnologies.ecommerce.entity.Customer;
import com.kmgtechnologies.ecommerce.entity.Product;
import com.kmgtechnologies.ecommerce.exception.InsufficientStockException;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.repository.CartRepository;
import com.kmgtechnologies.ecommerce.repository.CustomerRepository;
import com.kmgtechnologies.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final Long LAPTOP_ID = 10L;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartService cartService;

    private Customer alice;
    private Product laptop;

    @BeforeEach
    void setUp() {
        alice = new Customer("alice@example.com", "Alice");
        laptop = new Product("Laptop", null, new BigDecimal("999.99"), 10, new Category("Electronics"));
        ReflectionTestUtils.setField(laptop, "id", LAPTOP_ID);
    }

    @Test
    void getCart_returnsEmptyCartWithoutCreatingOne() {
        when(customerRepository.existsById(CUSTOMER_ID)).thenReturn(true);
        when(cartRepository.findWithItemsByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

        CartResponse response = cartService.getCart(CUSTOMER_ID);

        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isEqualByComparingTo("0");
        verify(cartRepository, never()).save(any());
    }

    @Test
    void getCart_throwsWhenCustomerDoesNotExist() {
        when(customerRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> cartService.getCart(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Customer with id 99 not found");
    }

    @Test
    void addItem_createsCartOnFirstAdd() {
        when(cartRepository.findWithItemsByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(alice));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.findById(LAPTOP_ID)).thenReturn(Optional.of(laptop));

        CartResponse response = cartService.addItem(CUSTOMER_ID, new AddCartItemRequest(LAPTOP_ID, 2));

        verify(cartRepository).save(any(Cart.class));
        assertThat(response.totalQuantity()).isEqualTo(2);
        assertThat(response.total()).isEqualByComparingTo("1999.98");
    }

    @Test
    void addItem_increasesQuantityOfExistingLine() {
        Cart cart = new Cart(alice);
        cart.addItem(laptop, 3);
        when(cartRepository.findWithItemsByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));
        when(productRepository.findById(LAPTOP_ID)).thenReturn(Optional.of(laptop));

        CartResponse response = cartService.addItem(CUSTOMER_ID, new AddCartItemRequest(LAPTOP_ID, 2));

        assertThat(response.items()).singleElement()
                .satisfies(item -> assertThat(item.quantity()).isEqualTo(5));
        verify(cartRepository, never()).save(any());
    }

    @Test
    void addItem_rejectsTotalQuantityAboveStock() {
        Cart cart = new Cart(alice);
        cart.addItem(laptop, 8);
        when(cartRepository.findWithItemsByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));
        when(productRepository.findById(LAPTOP_ID)).thenReturn(Optional.of(laptop));

        assertThatThrownBy(() -> cartService.addItem(CUSTOMER_ID, new AddCartItemRequest(LAPTOP_ID, 3)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Insufficient stock for 'Laptop': requested 11, available 10");
        assertThat(cart.findItem(LAPTOP_ID)).get()
                .satisfies(item -> assertThat(item.getQuantity()).isEqualTo(8));
    }

    @Test
    void addItem_throwsWhenProductDoesNotExist() {
        when(cartRepository.findWithItemsByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(new Cart(alice)));
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(CUSTOMER_ID, new AddCartItemRequest(99L, 1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product with id 99 not found");
    }

    @Test
    void addItem_throwsWhenCustomerDoesNotExist() {
        when(cartRepository.findWithItemsByCustomerId(99L)).thenReturn(Optional.empty());
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(99L, new AddCartItemRequest(LAPTOP_ID, 1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Customer with id 99 not found");
    }

    @Test
    void removeItem_removesLine() {
        Cart cart = new Cart(alice);
        cart.addItem(laptop, 1);
        when(cartRepository.findWithItemsByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));

        CartResponse response = cartService.removeItem(CUSTOMER_ID, LAPTOP_ID);

        assertThat(response.items()).isEmpty();
    }

    @Test
    void removeItem_throwsWhenProductIsNotInCart() {
        when(cartRepository.findWithItemsByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(new Cart(alice)));

        assertThatThrownBy(() -> cartService.removeItem(CUSTOMER_ID, LAPTOP_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product 10 is not in the cart");
    }

    @Test
    void clear_emptiesExistingCart() {
        Cart cart = new Cart(alice);
        cart.addItem(laptop, 2);
        when(customerRepository.existsById(CUSTOMER_ID)).thenReturn(true);
        when(cartRepository.findWithItemsByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));

        cartService.clear(CUSTOMER_ID);

        assertThat(cart.getItems()).isEmpty();
    }
}