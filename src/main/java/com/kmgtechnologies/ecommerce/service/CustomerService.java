package com.kmgtechnologies.ecommerce.service;

import com.kmgtechnologies.ecommerce.dto.CustomerResponse;
import com.kmgtechnologies.ecommerce.entity.Customer;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    static final int MAX_NAME_LENGTH = 100;

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public CustomerResponse findById(Long id) {
        return customerRepository.findById(id)
                .map(CustomerResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    public CustomerResponse findByExternalId(String externalId) {
        return customerRepository.findByExternalId(externalId)
                .map(CustomerResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Customer of user " + externalId + " not found"));
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
        return CustomerResponse.from(customerRepository.saveAndFlush(customer));
    }

    private static String truncate(String name) {
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }
}
