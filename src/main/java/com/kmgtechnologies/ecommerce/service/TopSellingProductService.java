package com.kmgtechnologies.ecommerce.service;

import com.kmgtechnologies.ecommerce.config.CacheNames;
import com.kmgtechnologies.ecommerce.dto.TopSellingProductResponse;
import com.kmgtechnologies.ecommerce.repository.OrderRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TopSellingProductService {

    private final OrderRepository orderRepository;

    public TopSellingProductService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Cacheable(cacheNames = CacheNames.TOP_SELLING_PRODUCTS, key = "#limit")
    public List<TopSellingProductResponse> findTopSelling(int limit) {
        return List.copyOf(orderRepository.findTopSelling(Limit.of(limit)));
    }
}