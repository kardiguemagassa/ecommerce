package com.kmgtechnologies.ecommerce.service;

import com.kmgtechnologies.ecommerce.dto.ProductImageOrderRequest;
import com.kmgtechnologies.ecommerce.dto.ProductRequest;
import com.kmgtechnologies.ecommerce.dto.ProductResponse;
import com.kmgtechnologies.ecommerce.entity.Category;
import com.kmgtechnologies.ecommerce.entity.Product;
import com.kmgtechnologies.ecommerce.entity.ProductImage;
import com.kmgtechnologies.ecommerce.exception.ImageLimitException;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.media.ImageKind;
import com.kmgtechnologies.ecommerce.media.MediaFiles;
import com.kmgtechnologies.ecommerce.media.MediaUrls;
import com.kmgtechnologies.ecommerce.repository.CategoryRepository;
import com.kmgtechnologies.ecommerce.repository.ProductImageRepository;
import com.kmgtechnologies.ecommerce.repository.ProductRepository;
import com.kmgtechnologies.ecommerce.repository.ProductRow;
import com.kmgtechnologies.ecommerce.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Pageable PAGE = PageRequest.of(0, 20);

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MediaFiles mediaFiles;

    @Mock
    private MediaUrls mediaUrls;

    @InjectMocks
    private ProductService productService;

    private ProductRequest laptopRequest(Long categoryId) {
        return new ProductRequest("  Laptop  ", "15 inch", new BigDecimal("999.99"), 10, categoryId);
    }

    @Test
    void search_replacesNullNameWithEmptyString() {
        when(productRepository.search(1L, "", PAGE)).thenReturn(Page.empty());

        productService.search(1L, null, PAGE);

        verify(productRepository).search(1L, "", PAGE);
    }

    @Test
    void search_stripsName() {
        when(productRepository.search(null, "lap", PAGE)).thenReturn(Page.empty());

        productService.search(null, "  lap ", PAGE);

        verify(productRepository).search(null, "lap", PAGE);
    }

    @Test
    void findById_throwsWhenProductDoesNotExist() {
        when(productRepository.findRowById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product with id 99 not found");
    }

    @Test
    void create_savesProductWithCategory() {
        Category electronics = new Category("Electronics");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.create(laptopRequest(1L));

        assertThat(response.name()).isEqualTo("Laptop");
        assertThat(response.price()).isEqualByComparingTo("999.99");
        assertThat(response.categoryName()).isEqualTo("Electronics");
    }

    @Test
    void create_throwsWhenCategoryDoesNotExist() {
        when(categoryRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(laptopRequest(42L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Category with id 42 not found");
        verify(productRepository, never()).save(any());
    }

    @Test
    void update_changesAllFieldsIncludingCategory() {
        Category books = new Category("Books");
        Category electronics = new Category("Electronics");
        Product product = new Product("Old", "old", new BigDecimal("1.00"), 1, books);
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(electronics));

        ProductResponse response = productService.update(5L, laptopRequest(2L));

        assertThat(product.getName()).isEqualTo("Laptop");
        assertThat(product.getStock()).isEqualTo(10);
        assertThat(product.getCategory()).isSameAs(electronics);
        assertThat(response.categoryName()).isEqualTo("Electronics");
    }

    @Test
    void update_throwsWhenProductDoesNotExist() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(99L, laptopRequest(1L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesExistingProduct_andItsImageOnceCommitted() {
        Product product = productWithImage("products/laptop.jpg");
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));

        productService.delete(5L);

        verify(productRepository).delete(product);
        verify(mediaFiles).deleteAfterCommit("products/laptop.jpg");
    }

    @Test
    void delete_throwsWhenProductDoesNotExist() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(productRepository, never()).delete(any());
    }

    @Test
    void replaceImage_storesTheNewImage_andDeletesThePreviousOneOnceCommitted() {
        Product product = productWithImage("products/old.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "laptop.png", "image/png", new byte[]{1});
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        when(mediaFiles.store(ImageKind.PRODUCT, file)).thenReturn("products/new.jpg");
        when(mediaUrls.of("products/new.jpg")).thenReturn("http://media.test/products/new.jpg");

        ProductResponse response = productService.replaceImage(5L, file);

        assertThat(product.getImageKey()).isEqualTo("products/new.jpg");
        assertThat(response.imageUrl()).isEqualTo("http://media.test/products/new.jpg");
        verify(mediaFiles).deleteAfterCommit("products/old.jpg");
    }

    @Test
    void replaceImage_throwsWhenProductDoesNotExist_withoutStoringAnything() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.replaceImage(99L, new MockMultipartFile("file", new byte[]{1})))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(mediaFiles, never()).store(any(), any());
    }

    @Test
    void deleteImage_removesTheReference_andTheObjectOnceCommitted() {
        Product product = productWithImage("products/old.jpg");
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));

        productService.deleteImage(5L);

        assertThat(product.getImageKey()).isNull();
        verify(mediaFiles).deleteAfterCommit("products/old.jpg");
    }

    @Test
    void replaceImage_ofAProductWithoutImage_addsItsFirstView() {
        Product product = new Product("Laptop", null, new BigDecimal("999.99"), 10, new Category("Electronics"));
        MockMultipartFile file = new MockMultipartFile("file", "laptop.png", "image/png", new byte[]{1});
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        when(mediaFiles.store(ImageKind.PRODUCT, file)).thenReturn("products/first.jpg");

        productService.replaceImage(5L, file);

        assertThat(product.getImages()).extracting(ProductImage::getImageKey).containsExactly("products/first.jpg");
        verify(productImageRepository).save(any(ProductImage.class));
        verify(mediaFiles, never()).deleteAfterCommit(any());
    }

    @Test
    void findById_returnsTheViews_mainImageFirst() {
        Product product = productWithImage("products/front.jpg");
        ReflectionTestUtils.setField(product.addImage("products/back.jpg"), "id", 2L);
        when(productRepository.findRowById(5L)).thenReturn(Optional.of(new ProductRow(
                5L, "Laptop", null, new BigDecimal("999.99"), 10, 1L, "Electronics", "products/front.jpg", 4.5, 2L)));
        when(productImageRepository.findByProductIdOrderByDisplayOrderAsc(5L)).thenReturn(product.getImages());
        when(mediaUrls.of(any())).thenAnswer(invocation -> "http://media.test/" + invocation.getArgument(0));

        ProductResponse response = productService.findById(5L);

        assertThat(response.imageUrl()).isEqualTo("http://media.test/products/front.jpg");
        assertThat(response.rating().average()).isEqualTo(4.5);
        assertThat(response.images()).extracting("id", "url").containsExactly(
                tuple(1L, "http://media.test/products/front.jpg"),
                tuple(2L, "http://media.test/products/back.jpg"));
    }

    @Test
    void addImage_storesTheUpload_andSavesTheNewView() {
        Product product = productWithImage("products/front.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "side.png", "image/png", new byte[]{1});
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        when(mediaFiles.store(ImageKind.PRODUCT, file)).thenReturn("products/side.jpg");

        productService.addImage(5L, file);

        assertThat(product.getImages()).extracting(ProductImage::getImageKey)
                .containsExactly("products/front.jpg", "products/side.jpg");
        assertThat(product.getImageKey()).isEqualTo("products/front.jpg");
        verify(productImageRepository).save(any(ProductImage.class));
    }

    @Test
    void addImage_beyondFourViews_isRefusedBeforeAnythingIsStored() {
        Product product = productWithImage("products/1.jpg");
        product.addImage("products/2.jpg");
        product.addImage("products/3.jpg");
        product.addImage("products/4.jpg");
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.addImage(5L, new MockMultipartFile("file", new byte[]{1})))
                .isInstanceOf(ImageLimitException.class);
        verify(mediaFiles, never()).store(any(), any());
    }

    @Test
    void removeImage_deletesTheObjectOnceCommitted() {
        Product product = productWithImage("products/front.jpg");
        ReflectionTestUtils.setField(product.addImage("products/back.jpg"), "id", 2L);
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));

        productService.removeImage(5L, 1L);

        assertThat(product.getImageKey()).isEqualTo("products/back.jpg");
        verify(mediaFiles).deleteAfterCommit("products/front.jpg");
    }

    @Test
    void reorderImages_makesTheFirstGivenViewTheMainImage() {
        Product product = productWithImage("products/front.jpg");
        ReflectionTestUtils.setField(product.addImage("products/back.jpg"), "id", 2L);
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));

        productService.reorderImages(5L, new ProductImageOrderRequest(List.of(2L, 1L)));

        assertThat(product.getImageKey()).isEqualTo("products/back.jpg");
    }

    @Test
    void delete_removesAllTheViewsOnceCommitted() {
        Product product = productWithImage("products/front.jpg");
        product.addImage("products/back.jpg");
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));

        productService.delete(5L);

        verify(mediaFiles).deleteAfterCommit("products/front.jpg");
        verify(mediaFiles).deleteAfterCommit("products/back.jpg");
    }

    /** A product whose main image (id 1, as saved) has this key. */
    private static Product productWithImage(String imageKey) {
        Product product = new Product("Laptop", null, new BigDecimal("999.99"), 10, new Category("Electronics"));
        ReflectionTestUtils.setField(product.addImage(imageKey), "id", 1L);
        return product;
    }
}