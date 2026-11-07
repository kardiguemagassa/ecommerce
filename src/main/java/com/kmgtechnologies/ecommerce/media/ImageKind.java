package com.kmgtechnologies.ecommerce.media;

/**
 * What an uploaded image is for: its size once processed, its weight limit, whether it is cropped and where it is
 * stored. A product image keeps its whole picture, within 1200 x 900: the shop shows it in a 4:3 frame, letterboxed,
 * so a portrait product (a kettle, a bottle) is never cut. A profile photo is cropped to a square, shown in a circle.
 */
public enum ImageKind {

    PRODUCT("products", 1200, 900, 2 * 1024 * 1024, false),
    AVATAR("avatars", 256, 256, 1024 * 1024, true);

    private final String folder;
    private final int width;
    private final int height;
    private final long maxBytes;
    private final boolean cropped;

    ImageKind(String folder, int width, int height, long maxBytes, boolean cropped) {
        this.folder = folder;
        this.width = width;
        this.height = height;
        this.maxBytes = maxBytes;
        this.cropped = cropped;
    }

    /** Key prefix in the bucket. */
    public String folder() {
        return folder;
    }

    /** Largest width of the processed image; smaller sources are never enlarged. */
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

    /** True: centre crop to the ratio of {@link #width()} x {@link #height()}; false: the whole picture, reduced. */
    public boolean cropped() {
        return cropped;
    }
}
