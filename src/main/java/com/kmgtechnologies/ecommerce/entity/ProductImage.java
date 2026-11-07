package com.kmgtechnologies.ecommerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One view of a product: its object key in the media storage and its place among the views (0: the main image). */
@Entity
@Table(name = "product_images")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 200)
    private String imageKey;

    @Column(nullable = false)
    private int displayOrder;

    protected ProductImage() {
    }

    ProductImage(Product product, String imageKey, int displayOrder) {
        this.product = product;
        this.imageKey = imageKey;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getImageKey() {
        return imageKey;
    }

    void setImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductImage other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
