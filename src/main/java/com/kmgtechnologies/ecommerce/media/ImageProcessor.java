package com.kmgtechnologies.ecommerce.media;

import com.kmgtechnologies.ecommerce.exception.ImageTooLargeException;
import com.kmgtechnologies.ecommerce.exception.InvalidImageException;
import com.kmgtechnologies.ecommerce.exception.UnsupportedImageException;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Turns an untrusted upload into a safe image. Nothing of the original file is kept:
 * <ol>
 *   <li>the format is read from the content (magic bytes), never from the file name or the declared type: JPEG, PNG
 *       and WebP only;</li>
 *   <li>the dimensions are read from the header before decoding, and a picture of more than 40 megapixels is refused
 *       (decompression bomb: a small file that would need gigabytes of memory);</li>
 *   <li>the image is decoded, turned upright (EXIF orientation of phone pictures), cropped to a square for a profile
 *       photo (a product image keeps its whole picture), reduced to the size of its use (never enlarged) and
 *       flattened on white;</li>
 *   <li>it is encoded again as a fresh JPEG: EXIF data (GPS position, device), comments and any payload appended to
 *       the original file are gone.</li>
 * </ol>
 * JPEG as output: the JDK writes it natively, every browser reads it, and photos are what is uploaded here.
 */
@Component
public class ImageProcessor {

    static final long MAX_PIXELS = 40_000_000L;
    static final float JPEG_QUALITY = 0.85f;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] EXIF_HEADER = {'E', 'x', 'i', 'f', 0, 0};
    private static final int EXIF_ORIENTATION_TAG = 0x0112;

    public ImageProcessor() {
        // Plug-ins (WebP, CMYK JPEG) found on the application class path, whichever thread uses ImageIO first.
        ImageIO.scanForPlugins();
        // Everything in memory: no temporary files written for each upload.
        ImageIO.setUseCache(false);
    }

    public ProcessedImage process(ImageKind kind, byte[] data) {
        if (data == null || data.length == 0) {
            throw new InvalidImageException("The file is empty");
        }
        if (data.length > kind.maxBytes()) {
            throw new ImageTooLargeException(kind.maxBytes());
        }
        String format = detectFormat(data);
        BufferedImage decoded = decode(data, format);
        BufferedImage upright = orient(decoded, "jpeg".equals(format) ? exifOrientation(data) : 1);
        BufferedImage resized = kind.cropped()
                ? cover(upright, kind.width(), kind.height())
                : contain(upright, kind.width(), kind.height());
        return new ProcessedImage(encode(resized), resized.getWidth(), resized.getHeight(), format);
    }

    static String detectFormat(byte[] data) {
        if (startsWith(data, 0, JPEG)) {
            return "jpeg";
        }
        if (startsWith(data, 0, PNG)) {
            return "png";
        }
        if (data.length >= 12 && startsWith(data, 0, "RIFF".getBytes(StandardCharsets.US_ASCII))
                && startsWith(data, 8, "WEBP".getBytes(StandardCharsets.US_ASCII))) {
            return "webp";
        }
        throw new UnsupportedImageException();
    }

    private static BufferedImage decode(byte[] data, String format) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
        if (!readers.hasNext()) {
            throw new UnsupportedImageException();
        }
        ImageReader reader = readers.next();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(input, true, true);
            long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
            if (pixels > MAX_PIXELS) {
                throw new InvalidImageException("The image has too many pixels (40 megapixels at most)");
            }
            BufferedImage image = reader.read(0);
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
                throw new InvalidImageException("The image cannot be read");
            }
            return image;
        } catch (InvalidImageException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // Damaged or crafted file: decoders fail with all kinds of exceptions.
            throw new InvalidImageException("The image cannot be read");
        } finally {
            reader.dispose();
        }
    }

    /** EXIF orientation (1 to 8) of a JPEG, 1 when absent or unreadable: segments before the image data only. */
    static int exifOrientation(byte[] data) {
        int offset = 2;
        while (offset + 4 <= data.length) {
            if ((data[offset] & 0xFF) != 0xFF) {
                return 1;
            }
            int marker = data[offset + 1] & 0xFF;
            if (marker == 0xFF) {
                offset++; // fill byte
                continue;
            }
            if (marker == 0xDA || marker == 0xD9) {
                return 1; // start of scan or end of image: no metadata after this point
            }
            int length = readUnsigned16(data, offset + 2, true);
            int start = offset + 4;
            int end = Math.min(data.length, offset + 2 + length);
            if (marker == 0xE1 && startsWith(data, start, EXIF_HEADER)) {
                return tiffOrientation(data, start + EXIF_HEADER.length, end);
            }
            if (length < 2) {
                return 1;
            }
            offset += 2 + length;
        }
        return 1;
    }

    private static int tiffOrientation(byte[] data, int tiff, int end) {
        if (tiff + 8 > end) {
            return 1;
        }
        boolean bigEndian;
        if (data[tiff] == 'M' && data[tiff + 1] == 'M') {
            bigEndian = true;
        } else if (data[tiff] == 'I' && data[tiff + 1] == 'I') {
            bigEndian = false;
        } else {
            return 1;
        }
        long firstDirectory = readUnsigned32(data, tiff + 4, bigEndian);
        if (firstDirectory < 8 || tiff + firstDirectory + 2 > end) {
            return 1;
        }
        int directory = tiff + (int) firstDirectory;
        int entries = readUnsigned16(data, directory, bigEndian);
        for (int i = 0; i < entries; i++) {
            int entry = directory + 2 + 12 * i;
            if (entry + 12 > end) {
                return 1;
            }
            if (readUnsigned16(data, entry, bigEndian) == EXIF_ORIENTATION_TAG) {
                int value = readUnsigned16(data, entry + 8, bigEndian);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    /** The picture as it should be displayed, for the eight EXIF orientations (5 to 8 swap width and height). */
    static BufferedImage orient(BufferedImage source, int orientation) {
        if (orientation == 1) {
            return source;
        }
        int w = source.getWidth();
        int h = source.getHeight();
        // AffineTransform(m00, m10, m01, m11, m02, m12): x' = m00 x + m01 y + m02, y' = m10 x + m11 y + m12
        AffineTransform transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);   // mirrored horizontally
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);  // rotated 180°
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);   // mirrored vertically
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);    // transposed
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);   // rotated 90° clockwise
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);  // transversed
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);   // rotated 90° counter-clockwise
            default -> throw new IllegalArgumentException("EXIF orientation " + orientation);
        };
        boolean swap = orientation >= 5;
        BufferedImage target = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        try {
            g.drawImage(source, transform, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /**
     * Centre crop to the ratio of {@code maxWidth x maxHeight}, then reduction to that size at most, on a white
     * background (transparent PNG and WebP).
     */
    static BufferedImage cover(BufferedImage source, int maxWidth, int maxHeight) {
        double ratio = (double) maxWidth / maxHeight;
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int cropWidth = sourceWidth;
        int cropHeight = sourceHeight;
        if ((double) sourceWidth / sourceHeight > ratio) {
            cropWidth = Math.max(1, (int) Math.round(sourceHeight * ratio));
        } else {
            cropHeight = Math.max(1, (int) Math.round(sourceWidth / ratio));
        }
        int targetWidth = Math.min(maxWidth, cropWidth);
        int targetHeight = Math.max(1, Math.min(maxHeight, (int) Math.round(targetWidth / ratio)));

        BufferedImage cropped = draw(source, (sourceWidth - cropWidth) / 2, (sourceHeight - cropHeight) / 2,
                cropWidth, cropHeight, cropWidth, cropHeight);
        return reduce(cropped, targetWidth, targetHeight);
    }

    /**
     * The whole picture, reduced to fit within {@code maxWidth x maxHeight} with its own proportions (never enlarged,
     * nothing cut), on a white background (transparent PNG and WebP).
     */
    static BufferedImage contain(BufferedImage source, int maxWidth, int maxHeight) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        double scale = Math.min(1.0, Math.min((double) maxWidth / sourceWidth, (double) maxHeight / sourceHeight));
        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));

        BufferedImage flattened = draw(source, 0, 0, sourceWidth, sourceHeight, sourceWidth, sourceHeight);
        return reduce(flattened, targetWidth, targetHeight);
    }

    /** Reduction to the target size, by halves first: one bicubic pass from a large photo aliases. */
    private static BufferedImage reduce(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage current = source;
        while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
            current = draw(current, 0, 0, current.getWidth(), current.getHeight(),
                    current.getWidth() / 2, current.getHeight() / 2);
        }
        if (current.getWidth() != targetWidth || current.getHeight() != targetHeight) {
            current = draw(current, 0, 0, current.getWidth(), current.getHeight(), targetWidth, targetHeight);
        }
        return current;
    }

    private static BufferedImage draw(BufferedImage source, int x, int y, int width, int height,
                                      int targetWidth, int targetHeight) {
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, targetWidth, targetHeight);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, targetWidth, targetHeight, x, y, x + width, y + height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] encode(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(JPEG_QUALITY);
            // No metadata passed: the file gets a bare JFIF header, nothing copied from the upload.
            writer.write(null, new IIOImage(image, null, null), parameters);
        } catch (IOException e) {
            throw new IllegalStateException("JPEG encoding failed", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        return offset >= 0 && data.length >= offset + prefix.length
                && Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
    }

    private static int readUnsigned16(byte[] data, int offset, boolean bigEndian) {
        if (offset + 2 > data.length) {
            return 0;
        }
        int first = data[offset] & 0xFF;
        int second = data[offset + 1] & 0xFF;
        return bigEndian ? first << 8 | second : second << 8 | first;
    }

    private static long readUnsigned32(byte[] data, int offset, boolean bigEndian) {
        if (offset + 4 > data.length) {
            return 0;
        }
        long value = 0;
        for (int i = 0; i < 4; i++) {
            int b = data[offset + (bigEndian ? i : 3 - i)] & 0xFF;
            value = value << 8 | b;
        }
        return value;
    }
}
