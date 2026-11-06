package com.kmgtechnologies.ecommerce.media;

/**
 * An uploaded image once checked and re-encoded: JPEG bytes without any metadata of the original.
 *
 * @param jpeg         the encoded image
 * @param width        width in pixels
 * @param height       height in pixels
 * @param sourceFormat format of the upload (jpeg, png or webp), for observability
 */
public record ProcessedImage(byte[] jpeg, int width, int height, String sourceFormat) {

    public static final String CONTENT_TYPE = "image/jpeg";
}
