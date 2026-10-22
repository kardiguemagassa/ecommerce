package com.kmgtechnologies.ecommerce.repository;

import com.kmgtechnologies.ecommerce.entity.Cart;
import com.kmgtechnologies.ecommerce.entity.Category;
import com.kmgtechnologies.ecommerce.entity.Customer;
import com.kmgtechnologies.ecommerce.entity.Product;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class CartRepositoryTest {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Customer alice;
    private Product laptop;

    @BeforeEach
    void setUp() {
        Category electronics = entityManager.persist(new Category("Electronics"));
        laptop = entityManager.persist(new Product("Laptop", null, new BigDecimal("999.99"), 10, electronics));
        alice = entityManager.persist(new Customer("alice@example.com", "Alice"));
        entityManager.flush();
    }

    @Test
    void save_cascadesItems() {
        Cart cart = new Cart(alice);
        cart.addItem(laptop, 2);
        cartRepository.saveAndFlush(cart);
        entityManager.clear();

        Cart reloaded = cartRepository.findWithItemsByCustomerId(alice.getId()).orElseThrow();

        assertThat(reloaded.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getQuantity()).isEqualTo(2));
    }

    @Test
    void findWithItemsByCustomerId_fetchesItemsAndProductsInOneQuery() {
        Cart cart = new Cart(alice);
        cart.addItem(laptop, 1);
        cartRepository.saveAndFlush(cart);
        entityManager.clear();

        Cart reloaded = cartRepository.findWithItemsByCustomerId(alice.getId()).orElseThrow();

        assertThat(Hibernate.isInitialized(reloaded.getItems())).isTrue();
        assertThat(Hibernate.isInitialized(reloaded.getItems().getFirst().getProduct())).isTrue();
    }

    @Test
    void findWithItemsByCustomerId_returnsEmptyCart() {
        cartRepository.saveAndFlush(new Cart(alice));
        entityManager.clear();

        assertThat(cartRepository.findWithItemsByCustomerId(alice.getId()))
                .get()
                .satisfies(cart -> assertThat(cart.getItems()).isEmpty());
    }

    @Test
    void removeItem_deletesOrphanLine() {
        Cart cart = new Cart(alice);
        cart.addItem(laptop, 1);
        cartRepository.saveAndFlush(cart);
        entityManager.clear();

        Cart reloaded = cartRepository.findWithItemsByCustomerId(alice.getId()).orElseThrow();
        reloaded.removeItem(laptop.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(cartRepository.findWithItemsByCustomerId(alice.getId()).orElseThrow().getItems()).isEmpty();
    }

    @Test
    void save_rejectsSecondCartForSameCustomer() {
        cartRepository.saveAndFlush(new Cart(alice));

        assertThatThrownBy(() -> cartRepository.saveAndFlush(new Cart(alice)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}