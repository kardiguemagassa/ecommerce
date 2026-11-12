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
import jakarta.persistence.Version;

import java.time.Instant;

/** A customer's review of a product they bought: 1 to 5 stars, an optional comment; hidden by a moderator if abusive. */
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String comment;

    @Column(nullable = false)
    private boolean hidden;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Review() {
    }

    public Review(Product product, Customer customer, int rating, String comment, Instant now) {
        this.product = product;
        this.customer = customer;
        this.createdAt = now;
        edit(rating, comment, now);
    }

    /** The author changes their review: it keeps its date of creation and its visibility. */
    public void edit(int rating, String comment, Instant now) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("A rating is 1 to 5 stars");
        }
        this.rating = rating;
        this.comment = comment == null || comment.isBlank() ? null : comment.strip();
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public Customer getCustomer() {
        return customer;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Review other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
