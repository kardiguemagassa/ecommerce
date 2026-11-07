package com.kmgtechnologies.ecommerce.entity;

import com.kmgtechnologies.ecommerce.exception.ImageLimitException;
import com.kmgtechnologies.ecommerce.exception.InsufficientStockException;
import com.kmgtechnologies.ecommerce.exception.InvalidImageOrderException;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

@Entity
@Table(name = "products", indexes = @Index(name = "idx_products_category_id", columnList = "category_id"))
public class Product {

    /** Views a product shows at most (product page gallery). */
    public static final int MAX_IMAGES = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /**
     * Object key of the main image (the first view), null without image: a copy kept in step with {@link #images}, so
     * that lists read it with the product, without joining the views.
     */
    @Column(length = 200)
    private String imageKey;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

    @Version
    private Long version;

    protected Product() {
    }

    public Product(String name, String description, BigDecimal price, int stock, Category category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.category = category;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getImageKey() {
        return imageKey;
    }

    /** The views, main image first. */
    public List<ProductImage> getImages() {
        return images.stream().sorted(Comparator.comparingInt(ProductImage::getDisplayOrder)).toList();
    }

    /** Refused when the product already shows {@link #MAX_IMAGES} views: checked before an upload is stored. */
    public void requireRoomForImage() {
        if (images.size() >= MAX_IMAGES) {
            throw new ImageLimitException(MAX_IMAGES);
        }
    }

    /** A new view, last; the first view of a product is its main image. */
    public ProductImage addImage(String key) {
        requireRoomForImage();
        ProductImage image = new ProductImage(this, key, images.size());
        images.add(image);
        syncMainImage();
        return image;
    }

    /** The main image replaced by a new one (added when there is none); returns the key replaced, or null. */
    public String replaceMainImage(String key) {
        List<ProductImage> ordered = getImages();
        if (ordered.isEmpty()) {
            addImage(key);
            return null;
        }
        ProductImage main = ordered.getFirst();
        String previous = main.getImageKey();
        main.setImageKey(key);
        syncMainImage();
        return previous;
    }

    /** Removes a view, the next ones move up; returns its key (the object to delete). */
    public String removeImage(Long imageId) {
        ProductImage image = images.stream()
                .filter(candidate -> candidate.getId() != null && candidate.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Product image", imageId));
        images.remove(image);
        renumber(getImages());
        return image.getImageKey();
    }

    /** Removes the main image, the next view becomes the main one; returns its key, or null without image. */
    public String removeMainImage() {
        List<ProductImage> ordered = getImages();
        return ordered.isEmpty() ? null : removeImage(ordered.getFirst().getId());
    }

    /** The views in a new order, given by their ids, each exactly once; the first becomes the main image. */
    public void reorderImages(List<Long> imageIds) {
        if (imageIds == null || imageIds.size() != images.size() || new HashSet<>(imageIds).size() != images.size()) {
            throw new InvalidImageOrderException();
        }
        List<ProductImage> ordered = imageIds.stream()
                .map(id -> images.stream()
                        .filter(image -> id.equals(image.getId()))
                        .findFirst()
                        .orElseThrow(InvalidImageOrderException::new))
                .toList();
        renumber(ordered);
    }

    private void renumber(List<ProductImage> ordered) {
        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).setDisplayOrder(index);
        }
        syncMainImage();
    }

    private void syncMainImage() {
        List<ProductImage> ordered = getImages();
        imageKey = ordered.isEmpty() ? null : ordered.getFirst().getImageKey();
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (quantity > stock) {
            throw new InsufficientStockException(name, quantity, stock);
        }
        stock -= quantity;
    }
}
