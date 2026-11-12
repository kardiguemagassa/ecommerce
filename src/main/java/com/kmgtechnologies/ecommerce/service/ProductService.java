package com.kmgtechnologies.ecommerce.service;

import com.kmgtechnologies.ecommerce.config.CacheNames;
import com.kmgtechnologies.ecommerce.dto.ProductImageOrderRequest;
import com.kmgtechnologies.ecommerce.dto.ProductRatingResponse;
import com.kmgtechnologies.ecommerce.dto.ProductRequest;
import com.kmgtechnologies.ecommerce.dto.ProductResponse;
import com.kmgtechnologies.ecommerce.entity.Category;
import com.kmgtechnologies.ecommerce.entity.Product;
import com.kmgtechnologies.ecommerce.entity.ProductImage;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.media.ImageKind;
import com.kmgtechnologies.ecommerce.media.MediaFiles;
import com.kmgtechnologies.ecommerce.media.MediaUrls;
import com.kmgtechnologies.ecommerce.repository.CategoryRepository;
import com.kmgtechnologies.ecommerce.repository.ProductImageRepository;
import com.kmgtechnologies.ecommerce.repository.ProductRepository;
import com.kmgtechnologies.ecommerce.repository.ReviewRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ReviewRepository reviewRepository;
    private final CategoryRepository categoryRepository;
    private final MediaFiles mediaFiles;
    private final MediaUrls mediaUrls;

    public ProductService(ProductRepository productRepository, ProductImageRepository productImageRepository,
                          ReviewRepository reviewRepository, CategoryRepository categoryRepository,
                          MediaFiles mediaFiles, MediaUrls mediaUrls) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.reviewRepository = reviewRepository;
        this.categoryRepository = categoryRepository;
        this.mediaFiles = mediaFiles;
        this.mediaUrls = mediaUrls;
    }

    public Page<ProductResponse> search(Long categoryId, String name, Pageable pageable) {
        String normalizedName = name == null ? "" : name.strip();
        return productRepository.search(categoryId, normalizedName, pageable)
                .map(row -> ProductResponse.from(row, mediaUrls));
    }

    @Cacheable(cacheNames = CacheNames.PRODUCTS, key = "#id")
    public ProductResponse findById(Long id) {
        return productRepository.findRowById(id)
                .map(row -> ProductResponse.from(row, productImageRepository.findByProductIdOrderByDisplayOrderAsc(id),
                        mediaUrls))
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Category category = getCategory(request.categoryId());
        Product product = new Product(
                request.name().strip(),
                request.description(),
                request.price(),
                request.stock(),
                category);
        return respond(productRepository.save(product));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id")
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getProduct(id);

        product.setName(request.name().strip());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setCategory(getCategory(request.categoryId()));

        return respond(product);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id")
    public void delete(Long id) {
        Product product = getProduct(id);
        List<String> keys = product.getImages().stream().map(ProductImage::getImageKey).toList();
        productRepository.delete(product);
        // Only if the deletion commits: a product still referenced by an order keeps its images.
        keys.forEach(mediaFiles::deleteAfterCommit);
    }

    /** A new view, after the others (the first view is the main image); 409 when the product has 4 already. */
    @Transactional
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id")
    public ProductResponse addImage(Long id, MultipartFile file) {
        Product product = getProduct(id);
        product.requireRoomForImage(); // before the upload is processed and stored
        // Saved at once: the answer carries the id of the new view (to remove it, to move it).
        productImageRepository.save(product.addImage(mediaFiles.store(ImageKind.PRODUCT, file)));
        return respond(product);
    }

    /** Removes a view, deleted from the storage once committed; the next ones move up. */
    @Transactional
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id")
    public ProductResponse removeImage(Long id, Long imageId) {
        Product product = getProduct(id);
        mediaFiles.deleteAfterCommit(product.removeImage(imageId));
        return respond(product);
    }

    /** The views in a new order; the first one becomes the main image. */
    @Transactional
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id")
    public ProductResponse reorderImages(Long id, ProductImageOrderRequest request) {
        Product product = getProduct(id);
        product.reorderImages(request.imageIds());
        return respond(product);
    }

    /** Replaces the main image (added when there is none); the previous one is deleted once committed. */
    @Transactional
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id")
    public ProductResponse replaceImage(Long id, MultipartFile file) {
        Product product = getProduct(id);
        if (product.getImages().isEmpty()) {
            productImageRepository.save(product.addImage(mediaFiles.store(ImageKind.PRODUCT, file)));
        } else {
            mediaFiles.deleteAfterCommit(product.replaceMainImage(mediaFiles.store(ImageKind.PRODUCT, file)));
        }
        return respond(product);
    }

    /** Removes the main image; the next view becomes the main one. */
    @Transactional
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id")
    public void deleteImage(Long id) {
        Product product = getProduct(id);
        mediaFiles.deleteAfterCommit(product.removeMainImage());
    }

    /** The product as answered after a change: its rating read again (a new product has none). */
    private ProductResponse respond(Product product) {
        ReviewRepository.RatingRow row = product.getId() == null ? null : reviewRepository.ratingOf(product.getId());
        ProductRatingResponse rating = row == null
                ? ProductRatingResponse.NONE
                : ProductRatingResponse.of(row.getAverage(), row.getTotal());
        return ProductResponse.from(product, rating, mediaUrls);
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private Category getCategory(Long categoryId) {
        return categoryRepository.findById(categoryId).orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }
}
