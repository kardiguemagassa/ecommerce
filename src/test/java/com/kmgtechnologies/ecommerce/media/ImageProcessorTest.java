package com.kmgtechnologies.ecommerce.media;

import com.kmgtechnologies.ecommerce.exception.ImageTooLargeException;
import com.kmgtechnologies.ecommerce.exception.InvalidImageException;
import com.kmgtechnologies.ecommerce.exception.UnsupportedImageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {

    /** 64 x 48 lossless WebP, blue. */
    private static final byte[] WEBP = Base64.getDecoder().decode("UklGRiQAAABXRUJQVlA4TBcAAAAvP8ALAAdQvHoUuf8BICH8Xy9G9D/1AwA=");

    private final ImageProcessor processor = new ImageProcessor();

    @Test
    void png_isReEncodedAsJpeg_keepingTheWholePicture() throws IOException {
        ProcessedImage image = processor.process(ImageKind.PRODUCT, encode(picture(800, 400, Color.ORANGE), "png"));

        assertThat(image.sourceFormat()).isEqualTo("png");
        assertThat(image.width()).isEqualTo(800);
        assertThat(image.height()).isEqualTo(400);
        assertThat(image.jpeg()).startsWith((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
        BufferedImage decoded = decode(image.jpeg());
        assertThat(decoded.getWidth()).isEqualTo(800);
        assertThat(decoded.getHeight()).isEqualTo(400);
    }

    @Test
    void portraitProduct_isReducedWithoutBeingCut() throws IOException {
        // A kettle photographed upright: red band at the top, blue at the bottom; both must survive.
        BufferedImage portrait = picture(1500, 3000, Color.WHITE);
        Graphics2D g = portrait.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 1500, 200);
        g.setColor(Color.BLUE);
        g.fillRect(0, 2800, 1500, 200);
        g.dispose();

        ProcessedImage image = processor.process(ImageKind.PRODUCT, encode(portrait, "png"));

        assertThat(image.width()).isEqualTo(450);
        assertThat(image.height()).isEqualTo(900);
        BufferedImage decoded = decode(image.jpeg());
        assertThat(isClose(new Color(decoded.getRGB(225, 10)), Color.RED)).isTrue();
        assertThat(isClose(new Color(decoded.getRGB(225, 890)), Color.BLUE)).isTrue();
    }

    @Test
    void wideProduct_isReducedToTheWidthOfItsUse() throws IOException {
        ProcessedImage image = processor.process(ImageKind.PRODUCT, encode(picture(3200, 1200, Color.GRAY), "jpeg"));

        assertThat(image.width()).isEqualTo(1200);
        assertThat(image.height()).isEqualTo(450);
    }

    @Test
    void largePhoto_isReducedToTheSizeOfItsUse() throws IOException {
        byte[] photo = encode(picture(4000, 3000, Color.GRAY), "jpeg");

        assertThat(processor.process(ImageKind.PRODUCT, photo)).satisfies(image -> {
            assertThat(image.width()).isEqualTo(1200);
            assertThat(image.height()).isEqualTo(900);
        });
        assertThat(processor.process(ImageKind.AVATAR, photo)).satisfies(image -> {
            assertThat(image.width()).isEqualTo(256);
            assertThat(image.height()).isEqualTo(256);
        });
    }

    @Test
    void smallPicture_isNeverEnlarged_andCroppedToASquareForAProfilePhoto() throws IOException {
        byte[] small = encode(picture(120, 80, Color.GREEN), "png");

        ProcessedImage avatar = processor.process(ImageKind.AVATAR, small);
        assertThat(avatar.width()).isEqualTo(80);
        assertThat(avatar.height()).isEqualTo(80);
        ProcessedImage product = processor.process(ImageKind.PRODUCT, small);
        assertThat(product.width()).isEqualTo(120);
        assertThat(product.height()).isEqualTo(80);
    }

    @Test
    void webp_isAccepted() {
        ProcessedImage image = processor.process(ImageKind.PRODUCT, WEBP);

        assertThat(image.sourceFormat()).isEqualTo("webp");
        assertThat(image.width()).isEqualTo(64);
        assertThat(image.height()).isEqualTo(48);
    }

    @Test
    void transparentPixels_becomeWhite() throws IOException {
        byte[] transparent = encode(new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB), "png");

        BufferedImage decoded = decode(processor.process(ImageKind.AVATAR, transparent).jpeg());

        assertThat(new Color(decoded.getRGB(10, 10))).isEqualTo(Color.WHITE);
    }

    @Test
    void exifMetadata_isNotCopied_andThePhotoIsTurnedUpright() throws IOException {
        // Landscape photo, red corner top-left, taken with the phone rotated: EXIF orientation 6 (90° clockwise).
        BufferedImage landscape = picture(400, 200, Color.BLUE);
        Graphics2D g = landscape.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 100);
        g.dispose();
        byte[] photo = withExifOrientation(encode(landscape, "jpeg"), 6);
        assertThat(new String(photo, StandardCharsets.ISO_8859_1)).contains("Exif");

        ProcessedImage image = processor.process(ImageKind.PRODUCT, photo);

        assertThat(new String(image.jpeg(), StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
        // Upright: the 200 x 400 portrait, whole.
        assertThat(image.width()).isEqualTo(200);
        assertThat(image.height()).isEqualTo(400);
    }

    @ParameterizedTest(name = "orientation {0}: red corner at {1}")
    @CsvSource({"1, TL", "2, TR", "3, BR", "4, BL", "5, TL", "6, TR", "7, BR", "8, BL"})
    void exifOrientations_areApplied(int orientation, String corner) {
        BufferedImage landscape = picture(400, 200, Color.BLUE);
        Graphics2D g = landscape.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 100);
        g.dispose();

        BufferedImage upright = ImageProcessor.orient(landscape, orientation);

        assertThat(upright.getWidth()).isEqualTo(orientation >= 5 ? 200 : 400);
        int x = corner.endsWith("L") ? 5 : upright.getWidth() - 6;
        int y = corner.startsWith("T") ? 5 : upright.getHeight() - 6;
        assertThat(new Color(upright.getRGB(x, y), true)).isEqualTo(Color.RED);
    }

    @Test
    void exifOrientation_defaultsTo1WhenAbsentOrDamaged() throws IOException {
        byte[] plain = encode(picture(10, 10, Color.RED), "jpeg");

        assertThat(ImageProcessor.exifOrientation(plain)).isEqualTo(1);
        assertThat(ImageProcessor.exifOrientation(withExifOrientation(plain, 6))).isEqualTo(6);
        assertThat(ImageProcessor.exifOrientation(Arrays.copyOf(withExifOrientation(plain, 6), 12))).isEqualTo(1);
    }

    @Test
    void fileThatIsNotAnImage_isUnsupported_whateverItsName() {
        assertThatThrownBy(() -> processor.process(ImageKind.AVATAR, "<svg onload=alert(1)>".getBytes()))
                .isInstanceOf(UnsupportedImageException.class);
        assertThatThrownBy(() -> processor.process(ImageKind.AVATAR, "GIF89a....".getBytes()))
                .isInstanceOf(UnsupportedImageException.class);
    }

    @Test
    void emptyFile_isInvalid() {
        assertThatThrownBy(() -> processor.process(ImageKind.AVATAR, new byte[0]))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void fileAboveTheLimitOfItsKind_isTooLarge() {
        assertThatThrownBy(() -> processor.process(ImageKind.AVATAR, new byte[1024 * 1024 + 1]))
                .isInstanceOf(ImageTooLargeException.class)
                .hasMessage("The image must not exceed 1 MB");
    }

    @Test
    void decompressionBomb_isRefusedBeforeDecoding() throws IOException {
        // Header of a 100 000 x 100 000 PNG (10 gigapixels) on top of a tiny file.
        byte[] bomb = encode(picture(10, 10, Color.RED), "png");
        writeBigEndian(bomb, 16, 100_000);
        writeBigEndian(bomb, 20, 100_000);

        assertThatThrownBy(() -> processor.process(ImageKind.PRODUCT, bomb))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("too many pixels");
    }

    @Test
    void damagedImage_isInvalid() throws IOException {
        byte[] truncated = Arrays.copyOf(encode(picture(400, 300, Color.RED), "png"), 60);

        assertThatThrownBy(() -> processor.process(ImageKind.PRODUCT, truncated))
                .isInstanceOf(InvalidImageException.class);
    }

    private static BufferedImage picture(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, bytes)).isTrue();
        return bytes.toByteArray();
    }

    private static BufferedImage decode(byte[] data) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(data));
    }

    /** Inserts an APP1 EXIF segment (big-endian TIFF, one entry: orientation) right after the JPEG start marker. */
    static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        byte[] tiff = {'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0,
                0, 0, 0, 0};
        byte[] header = {'E', 'x', 'i', 'f', 0, 0};
        int length = 2 + header.length + tiff.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(new byte[]{(byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) length}, 0, 4);
        out.write(header, 0, header.length);
        out.write(tiff, 0, tiff.length);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static void writeBigEndian(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 24);
        data[offset + 1] = (byte) (value >>> 16);
        data[offset + 2] = (byte) (value >>> 8);
        data[offset + 3] = (byte) value;
    }

    /** JPEG shifts colours a little: close enough on each channel. */
    private static boolean isClose(Color actual, Color expected) {
        return Math.abs(actual.getRed() - expected.getRed()) < 40
                && Math.abs(actual.getGreen() - expected.getGreen()) < 40
                && Math.abs(actual.getBlue() - expected.getBlue()) < 40;
    }
}
