package com.kmgtechnologies.ecommerce.entity;

import com.kmgtechnologies.ecommerce.exception.ImageLimitException;
import com.kmgtechnologies.ecommerce.exception.InsufficientStockException;
import com.kmgtechnologies.ecommerce.exception.InvalidImageOrderException;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private final Product laptop = new Product("Laptop", null, new BigDecimal("999.99"), 5, new Category("Electronics"));

    @Test
    void decreaseStock_subtractsQuantity() {
        laptop.decreaseStock(5);

        assertThat(laptop.getStock()).isZero();
    }

    @Test
    void decreaseStock_rejectsQuantityAboveStock() {
        assertThatThrownBy(() -> laptop.decreaseStock(6))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Insufficient stock for 'Laptop': requested 6, available 5");
        assertThat(laptop.getStock()).isEqualTo(5);
    }

    @Test
    void decreaseStock_rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> laptop.decreaseStock(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addImage_appendsViews_theFirstBeingTheMainImage() {
        image("front.jpg", 1L);
        image("back.jpg", 2L);

        assertThat(keys()).containsExactly("front.jpg", "back.jpg");
        assertThat(laptop.getImages()).extracting(ProductImage::getDisplayOrder).containsExactly(0, 1);
        assertThat(laptop.getImageKey()).isEqualTo("front.jpg");
    }

    @Test
    void addImage_refusesAFifthView() {
        for (long id = 1; id <= Product.MAX_IMAGES; id++) {
            image("view-" + id + ".jpg", id);
        }

        assertThatThrownBy(() -> laptop.addImage("fifth.jpg"))
                .isInstanceOf(ImageLimitException.class)
                .hasMessage("A product has at most 4 images");
        assertThat(laptop.getImages()).hasSize(4);
    }

    @Test
    void removeImage_movesTheNextViewsUp_andTheSecondBecomesTheMainImage() {
        image("front.jpg", 1L);
        image("back.jpg", 2L);
        image("side.jpg", 3L);

        String removed = laptop.removeImage(1L);

        assertThat(removed).isEqualTo("front.jpg");
        assertThat(keys()).containsExactly("back.jpg", "side.jpg");
        assertThat(laptop.getImages()).extracting(ProductImage::getDisplayOrder).containsExactly(0, 1);
        assertThat(laptop.getImageKey()).isEqualTo("back.jpg");
    }

    @Test
    void removeImage_ofTheLastView_leavesTheProductWithoutImage() {
        image("front.jpg", 1L);

        laptop.removeImage(1L);

        assertThat(laptop.getImages()).isEmpty();
        assertThat(laptop.getImageKey()).isNull();
    }

    @Test
    void removeImage_rejectsAViewOfAnotherProduct() {
        image("front.jpg", 1L);

        assertThatThrownBy(() -> laptop.removeImage(99L)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(laptop.getImages()).hasSize(1);
    }

    @Test
    void reorderImages_followsTheGivenOrder_andChangesTheMainImage() {
        image("front.jpg", 1L);
        image("back.jpg", 2L);
        image("side.jpg", 3L);

        laptop.reorderImages(List.of(3L, 1L, 2L));

        assertThat(keys()).containsExactly("side.jpg", "front.jpg", "back.jpg");
        assertThat(laptop.getImageKey()).isEqualTo("side.jpg");
    }

    @Test
    void reorderImages_needsEachViewExactlyOnce() {
        image("front.jpg", 1L);
        image("back.jpg", 2L);

        assertThatThrownBy(() -> laptop.reorderImages(List.of(1L))).isInstanceOf(InvalidImageOrderException.class);
        assertThatThrownBy(() -> laptop.reorderImages(List.of(1L, 1L))).isInstanceOf(InvalidImageOrderException.class);
        assertThatThrownBy(() -> laptop.reorderImages(List.of(1L, 99L))).isInstanceOf(InvalidImageOrderException.class);
        assertThat(keys()).containsExactly("front.jpg", "back.jpg");
    }

    @Test
    void replaceMainImage_keepsTheOtherViews_andReturnsTheReplacedKey() {
        image("front.jpg", 1L);
        image("back.jpg", 2L);

        String previous = laptop.replaceMainImage("new-front.jpg");

        assertThat(previous).isEqualTo("front.jpg");
        assertThat(keys()).containsExactly("new-front.jpg", "back.jpg");
        assertThat(laptop.getImageKey()).isEqualTo("new-front.jpg");
    }

    /** A view as saved: the database gives it its id. */
    private void image(String key, Long id) {
        ReflectionTestUtils.setField(laptop.addImage(key), "id", id);
    }

    private List<String> keys() {
        return laptop.getImages().stream().map(ProductImage::getImageKey).toList();
    }
}
