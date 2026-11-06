package com.kmgtechnologies.ecommerce.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartTest {

    private Cart cart;
    private Product laptop;
    private Product mouse;

    @BeforeEach
    void setUp() {
        Category electronics = new Category("Electronics");
        laptop = product(1L, "Laptop", "999.99", electronics);
        mouse = product(2L, "Mouse", "19.90", electronics);
        cart = new Cart(new Customer("alice@example.com", "Alice"));
    }

    private static Product product(Long id, String name, String price, Category category) {
        Product product = new Product(name, null, new BigDecimal(price), 100, category);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    @Test
    void addItem_createsNewLine() {
        cart.addItem(laptop, 2);

        assertThat(cart.getItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getProduct()).isSameAs(laptop);
                    assertThat(item.getQuantity()).isEqualTo(2);
                });
    }

    @Test
    void addItem_sameProductIncreasesQuantityInsteadOfAddingLine() {
        cart.addItem(laptop, 1);
        cart.addItem(laptop, 2);

        assertThat(cart.getItems()).singleElement()
                .extracting(CartItem::getQuantity)
                .isEqualTo(3);
    }

    @Test
    void addItem_rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> cart.addItem(laptop, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getTotal_sumsSubtotals() {
        cart.addItem(laptop, 2);
        cart.addItem(mouse, 3);

        assertThat(cart.getTotal()).isEqualByComparingTo("2059.68");
    }

    @Test
    void getTotal_isZeroForEmptyCart() {
        assertThat(cart.getTotal()).isEqualByComparingTo("0");
    }

    @Test
    void changeQuantity_replacesTheQuantityAndReportsWhetherTheLineExists() {
        cart.addItem(laptop, 3);

        assertThat(cart.changeQuantity(1L, 1)).isTrue();
        assertThat(cart.changeQuantity(2L, 1)).isFalse();
        assertThat(cart.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getQuantity()).isEqualTo(1));
    }

    @Test
    void changeQuantity_rejectsNonPositiveQuantity() {
        cart.addItem(laptop, 3);

        assertThatThrownBy(() -> cart.changeQuantity(1L, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeItem_removesLineAndReportsWhetherItExisted() {
        cart.addItem(laptop, 1);
        cart.addItem(mouse, 1);

        assertThat(cart.removeItem(1L)).isTrue();
        assertThat(cart.removeItem(1L)).isFalse();
        assertThat(cart.getItems()).extracting(CartItem::getProduct).containsExactly(mouse);
    }

    @Test
    void getItems_cannotBeModifiedFromOutside() {
        assertThatThrownBy(() -> cart.getItems().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}