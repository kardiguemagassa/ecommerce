package com.kmgtechnologies.ecommerce.service;

import com.kmgtechnologies.ecommerce.dto.CustomerRequest;
import com.kmgtechnologies.ecommerce.dto.CustomerResponse;
import com.kmgtechnologies.ecommerce.entity.Customer;
import com.kmgtechnologies.ecommerce.exception.DuplicateResourceException;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public CustomerResponse findById(Long id) {
        return customerRepository.findById(id)
                .map(CustomerResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (customerRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("A customer with email '" + email + "' already exists");
        }
        Customer customer = customerRepository.save(new Customer(email, request.fullName().strip()));
        return CustomerResponse.from(customer);
    }
}