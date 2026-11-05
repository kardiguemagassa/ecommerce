package com.kmgtechnologies.ecommerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, length = 100)
    private String fullName;

    /** Subject of the Keycloak user ({@code sub}); null for customers created before authentication existed. */
    @Column(unique = true)
    private String externalId;

    /** Object key of the profile photo in the media storage; null without photo. */
    @Column(length = 200)
    private String avatarKey;

    protected Customer() {
    }

    public Customer(String email, String fullName) {
        this(null, email, fullName);
    }

    public Customer(String externalId, String email, String fullName) {
        this.externalId = externalId;
        this.email = email;
        this.fullName = fullName;
    }

    /** Attaches a customer created before authentication to its Keycloak user (same verified e-mail). */
    public void linkTo(String externalId) {
        if (this.externalId != null) {
            throw new IllegalStateException("Customer " + id + " is already linked to a user");
        }
        this.externalId = externalId;
    }

    /** Keycloak owns the profile: e-mail and name are copied from the token (no write when unchanged). */
    public void updateProfile(String email, String fullName) {
        this.email = email;
        this.fullName = fullName;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getAvatarKey() {
        return avatarKey;
    }

    public void setAvatarKey(String avatarKey) {
        this.avatarKey = avatarKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Customer other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}