package com.kmgtechnologies.ecommerce.media;

/**
 * What an uploaded image is for: its size once processed, its weight limit and where it is stored.
 * Product images are 4:3 (catalog cards and product page), avatars square (navigation bar and profile).
 */
public enum ImageKind {

    PRODUCT("products", 1200, 900, 2 * 1024 * 1024),
    AVATAR("avatars", 256, 256, 1024 * 1024);

    private final String folder;
    private final int width;
    private final int height;
    private final long maxBytes;

    ImageKind(String folder, int width, int height, long maxBytes) {
        this.folder = folder;
        this.width = width;
        this.height = height;
        this.maxBytes = maxBytes;
    }

    /** Key prefix in the bucket. */
    public String folder() {
        return folder;
    }

    /** Largest width of the processed image; smaller sources are cropped, never enlarged. */
    public int width() {
        return width;
    }

    /** Largest height of the processed image. */
    public int height() {
        return height;
    }

    /** Largest accepted upload, in bytes. */
    public long maxBytes() {
        return maxBytes;
    }
}
