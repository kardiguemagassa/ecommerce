package com.kmgtechnologies.ecommerce.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        //setCacheNames(List.of()) Désactive la création dynamique de caches. Une faute de frappe (@Cacheable("prodcuts"))
        // provoque une erreur claire, au lieu de créer en silence un cache sans limite ni expiration.
        manager.setCacheNames(List.of());
        //setAllowNullValues(false)	On ne met jamais null en cache.
        manager.setAllowNullValues(false);
        manager.registerCustomCache(CacheNames.PRODUCTS, Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats()
                .build());
        manager.registerCustomCache(CacheNames.TOP_SELLING_PRODUCTS, Caffeine.newBuilder()
                .maximumSize(50)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats()
                .build());
        return new TransactionAwareCacheManagerProxy(manager);
    }
}