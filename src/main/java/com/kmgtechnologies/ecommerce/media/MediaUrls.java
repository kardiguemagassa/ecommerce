package com.kmgtechnologies.ecommerce.media;

import org.springframework.stereotype.Component;

/** Public address of a stored object: the configured public URL followed by the object key. */
@Component
public class MediaUrls {

    private final String publicUrl;

    public MediaUrls(MediaProperties properties) {
        this.publicUrl = properties.publicUrl();
    }

    /** The URL of the object, or null without object (no image). */
    public String of(String key) {
        return key == null ? null : publicUrl + "/" + key;
    }
}
