package com.kmgtechnologies.ecommerce.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.net.URI;

/**
 * Object storage of product images and avatars, over the S3 API: SeaweedFS in development (compose service
 * {@code media}), any S3 provider in production (AWS, Scaleway, OVHcloud...) with another endpoint and credentials.
 *
 * @param endpoint      S3 API as the application reaches it
 * @param region        signing region (any value for SeaweedFS)
 * @param bucket        bucket of the shop's media
 * @param accessKey     credentials of the application (from .env, never in Git)
 * @param secretKey     credentials of the application (from .env, never in Git)
 * @param publicUrl     where browsers read the objects: the bucket itself in development, {@code /media} behind
 *                      nginx, or a CDN; an object key is appended to it
 * @param provision     create the bucket when missing and make its objects publicly readable at startup (development
 *                      and tests; in production the bucket is provisioned with the infrastructure)
 */
@ConfigurationProperties("app.media")
public record MediaProperties(URI endpoint, String region, String bucket, String accessKey, String secretKey,
                              String publicUrl, boolean provision) {

    public MediaProperties {
        Assert.notNull(endpoint, "app.media.endpoint is required");
        Assert.hasText(region, "app.media.region is required");
        Assert.hasText(bucket, "app.media.bucket is required");
        Assert.hasText(accessKey, "app.media.access-key is required (MEDIA_ACCESS_KEY in .env)");
        Assert.hasText(secretKey, "app.media.secret-key is required (MEDIA_SECRET_KEY in .env)");
        Assert.hasText(publicUrl, "app.media.public-url is required");
        publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }
}
