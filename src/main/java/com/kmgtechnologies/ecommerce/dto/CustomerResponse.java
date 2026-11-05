package com.kmgtechnologies.ecommerce.dto;

import com.kmgtechnologies.ecommerce.entity.Customer;
import com.kmgtechnologies.ecommerce.media.MediaUrls;

/**
 * A customer account.
 *
 * @param avatarUrl public URL of the profile photo (JPEG, 256 x 256), null without photo
 */
public record CustomerResponse(Long id, String email, String fullName, String avatarUrl) {

    public static CustomerResponse from(Customer customer, MediaUrls urls) {
        return new CustomerResponse(customer.getId(), customer.getEmail(), customer.getFullName(),
                urls.of(customer.getAvatarKey()));
    }
}
