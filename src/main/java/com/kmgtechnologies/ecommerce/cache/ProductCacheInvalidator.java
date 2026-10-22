package com.kmgtechnologies.ecommerce.cache;

import com.kmgtechnologies.ecommerce.config.CacheNames;
import com.kmgtechnologies.ecommerce.event.OrderPlacedEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ProductCacheInvalidator {

    private final CacheManager cacheManager;

    public ProductCacheInvalidator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        Cache products = cacheManager.getCache(CacheNames.PRODUCTS);
        if (products != null) {
            event.productIds().forEach(products::evictIfPresent);
        }
    }
}