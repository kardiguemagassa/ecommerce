package com.kmgtechnologies.ecommerce.service;

import com.kmgtechnologies.ecommerce.dto.CustomerResponse;
import com.kmgtechnologies.ecommerce.entity.Customer;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.media.ImageKind;
import com.kmgtechnologies.ecommerce.media.MediaFiles;
import com.kmgtechnologies.ecommerce.media.MediaUrls;
import com.kmgtechnologies.ecommerce.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    static final int MAX_NAME_LENGTH = 100;

    private final CustomerRepository customerRepository;
    private final MediaFiles mediaFiles;
    private final MediaUrls mediaUrls;

    public CustomerService(CustomerRepository customerRepository, MediaFiles mediaFiles, MediaUrls mediaUrls) {
        this.customerRepository = customerRepository;
        this.mediaFiles = mediaFiles;
        this.mediaUrls = mediaUrls;
    }

    public CustomerResponse findById(Long id) {
        return CustomerResponse.from(getCustomer(id), mediaUrls);
    }

    public CustomerResponse findByExternalId(String externalId) {
        return customerRepository.findByExternalId(externalId)
                .map(customer -> CustomerResponse.from(customer, mediaUrls))
                .orElseThrow(() -> new ResourceNotFoundException("Customer of user " + externalId + " not found"));
    }

    /** Profile photo of a Keycloak user, without creating the customer: null when none (or no customer yet). */
    public String findAvatarUrl(String externalId) {
        return customerRepository.findByExternalId(externalId)
                .map(customer -> mediaUrls.of(customer.getAvatarKey()))
                .orElse(null);
    }

    /** Replaces the profile photo; the previous one is deleted once the change is committed. */
    @Transactional
    public CustomerResponse replaceAvatar(Long customerId, MultipartFile file) {
        Customer customer = getCustomer(customerId);
        String previous = customer.getAvatarKey();
        customer.setAvatarKey(mediaFiles.store(ImageKind.AVATAR, file));
        mediaFiles.deleteAfterCommit(previous);
        return CustomerResponse.from(customer, mediaUrls);
    }

    @Transactional
    public void deleteAvatar(Long customerId) {
        Customer customer = getCustomer(customerId);
        mediaFiles.deleteAfterCommit(customer.getAvatarKey());
        customer.setAvatarKey(null);
    }

    /**
     * The customer of a Keycloak user, created on first use. In order:
     * <ol>
     *   <li>the customer already linked to the user (subject): its e-mail and name follow Keycloak;</li>
     *   <li>a customer created before authentication with the same e-mail, linked only if Keycloak verified that
     *       e-mail (otherwise anyone could claim an existing account by registering its address);</li>
     *   <li>a new customer.</li>
     * </ol>
     * Flushed here: a concurrent first call for the same user fails on the unique subject, and the caller reads the
     * record the other call created.
     */
    @Transactional
    public CustomerResponse provision(CustomerIdentity identity) {
        String email = identity.email().strip().toLowerCase(Locale.ROOT);
        String fullName = truncate(identity.fullName().strip());
        Customer customer = customerRepository.findByExternalId(identity.subject())
                .or(() -> identity.emailVerified()
                        ? customerRepository.findByEmailAndExternalIdIsNull(email).map(existing -> {
                            existing.linkTo(identity.subject());
                            return existing;
                        })
                        : Optional.<Customer>empty())
                .orElseGet(() -> new Customer(identity.subject(), email, fullName));
        customer.updateProfile(email, fullName);
        return CustomerResponse.from(customerRepository.saveAndFlush(customer), mediaUrls);
    }

    private Customer getCustomer(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    private static String truncate(String name) {
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }
}
