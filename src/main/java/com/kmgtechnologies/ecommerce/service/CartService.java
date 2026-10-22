package com.kmgtechnologies.ecommerce.service;

import com.kmgtechnologies.ecommerce.dto.AddCartItemRequest;
import com.kmgtechnologies.ecommerce.dto.CartResponse;
import com.kmgtechnologies.ecommerce.entity.Cart;
import com.kmgtechnologies.ecommerce.entity.CartItem;
import com.kmgtechnologies.ecommerce.entity.Customer;
import com.kmgtechnologies.ecommerce.entity.Product;
import com.kmgtechnologies.ecommerce.exception.InsufficientStockException;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.repository.CartRepository;
import com.kmgtechnologies.ecommerce.repository.CustomerRepository;
import com.kmgtechnologies.ecommerce.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CartService {

    private final CartRepository cartRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public CartService(CartRepository cartRepository,
                       CustomerRepository customerRepository,
                       ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    public CartResponse getCart(Long customerId) {
        ensureCustomerExists(customerId);
        return cartRepository.findWithItemsByCustomerId(customerId)
                .map(cart -> CartResponse.from(customerId, cart))
                .orElseGet(() -> CartResponse.empty(customerId));
    }

    @Transactional
    public CartResponse addItem(Long customerId, AddCartItemRequest request) {
        Cart cart = cartRepository.findWithItemsByCustomerId(customerId)
                .orElseGet(() -> createCart(customerId));
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.productId()));

        int alreadyInCart = cart.findItem(product.getId()).map(CartItem::getQuantity).orElse(0);
        int requested = alreadyInCart + request.quantity();
        if (requested > product.getStock()) {
            throw new InsufficientStockException(product.getName(), requested, product.getStock());
        }

        cart.addItem(product, request.quantity());
        return CartResponse.from(customerId, cart);
    }

    @Transactional
    public CartResponse removeItem(Long customerId, Long productId) {
        Cart cart = cartRepository.findWithItemsByCustomerId(customerId)
                .orElseThrow(() -> customerRepository.existsById(customerId)
                        ? notInCart(productId)
                        : new ResourceNotFoundException("Customer", customerId));

        if (!cart.removeItem(productId)) {
            throw notInCart(productId);
        }
        return CartResponse.from(customerId, cart);
    }

    @Transactional
    public void clear(Long customerId) {
        ensureCustomerExists(customerId);
        cartRepository.findWithItemsByCustomerId(customerId).ifPresent(Cart::clear);
    }

    private Cart createCart(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        return cartRepository.save(new Cart(customer));
    }

    private void ensureCustomerExists(Long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer", customerId);
        }
    }

    private static ResourceNotFoundException notInCart(Long productId) {
        return new ResourceNotFoundException("Product " + productId + " is not in the cart");
    }
}