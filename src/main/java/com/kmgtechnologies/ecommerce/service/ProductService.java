package com.kmgtechnologies.ecommerce.service;

import com.kmgtechnologies.ecommerce.config.CacheNames;
import com.kmgtechnologies.ecommerce.dto.ProductRequest;
import com.kmgtechnologies.ecommerce.dto.ProductResponse;
import com.kmgtechnologies.ecommerce.entity.Category;
import com.kmgtechnologies.ecommerce.entity.Product;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.media.ImageKind;
import com.kmgtechnologies.ecommerce.media.MediaFiles;
import com.kmgtechnologies.ecommerce.media.MediaUrls;
import com.kmgtechnologies.ecommerce.repository.CategoryRepository;
import com.kmgtechnologies.ecommerce.repository.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final MediaFiles mediaFiles;
    private final MediaUrls mediaUrls;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          MediaFiles mediaFiles, MediaUrls mediaUrls) {
        this.productRepository = productRepository;
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
                .map(row -> ProductResponse.from(row, mediaUrls))
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
        return ProductResponse.from(productRepository.save(product), mediaUrls);
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

        return ProductResponse.from(product, mediaUrls);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id")
    public void delete(Long id) {
        Product product = getProduct(id);
        productRepository.delete(product);
        // Only if the deletion commits: a product still referenced by an order keeps its image.
        mediaFiles.deleteAfterCommit(product.getImageKey());
    }

    /** Replaces the image of the product; the previous one is deleted once the change is committed. */
    @Transactional
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id")
    public ProductResponse replaceImage(Long id, MultipartFile file) {
        Product product = getProduct(id);
        String previous = product.getImageKey();
        product.setImageKey(mediaFiles.store(ImageKind.PRODUCT, file));
        mediaFiles.deleteAfterCommit(previous);
        return ProductResponse.from(product, mediaUrls);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id")
    public void deleteImage(Long id) {
        Product product = getProduct(id);
        mediaFiles.deleteAfterCommit(product.getImageKey());
        product.setImageKey(null);
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private Category getCategory(Long categoryId) {
        return categoryRepository.findById(categoryId).orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }
}
