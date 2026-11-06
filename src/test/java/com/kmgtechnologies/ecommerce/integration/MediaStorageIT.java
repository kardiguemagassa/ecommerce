package com.kmgtechnologies.ecommerce.integration;

import com.kmgtechnologies.ecommerce.dto.CustomerResponse;
import com.kmgtechnologies.ecommerce.dto.ProductResponse;
import com.kmgtechnologies.ecommerce.entity.Category;
import com.kmgtechnologies.ecommerce.entity.Product;
import com.kmgtechnologies.ecommerce.media.ImageKind;
import com.kmgtechnologies.ecommerce.media.MediaFiles;
import com.kmgtechnologies.ecommerce.media.MediaProperties;
import com.kmgtechnologies.ecommerce.repository.CategoryRepository;
import com.kmgtechnologies.ecommerce.repository.ProductRepository;
import com.kmgtechnologies.ecommerce.service.CustomerIdentity;
import com.kmgtechnologies.ecommerce.service.CustomerService;
import com.kmgtechnologies.ecommerce.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Images against a real SeaweedFS (same image as compose): bucket provisioned at startup, objects public for reading
 * only, and the storage kept in step with the database on commit and rollback.
 */
@IntegrationTest
class MediaStorageIT {

    private static final String ACCESS_KEY = "media-test";
    private static final String SECRET_KEY = "media-test-secret";

    /** Same image and command as the "media" service of compose.yml: update both together. */
    static final GenericContainer<?> SEAWEEDFS = new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:4.52"))
            .withCommand("mini", "-dir=/data", "-webdav=false", "-s3.port.iceberg=0", "-s3.port.lance=0")
            .withEnv("AWS_ACCESS_KEY_ID", ACCESS_KEY)
            .withEnv("AWS_SECRET_ACCESS_KEY", SECRET_KEY)
            .withExposedPorts(8333)
            .waitingFor(Wait.forHttp("/healthz").forPort(8333).withStartupTimeout(Duration.ofMinutes(2)));

    static {
        SEAWEEDFS.start();
    }

    @DynamicPropertySource
    static void media(DynamicPropertyRegistry registry) {
        String endpoint = "http://" + SEAWEEDFS.getHost() + ":" + SEAWEEDFS.getMappedPort(8333);
        registry.add("app.media.endpoint", () -> endpoint);
        registry.add("app.media.access-key", () -> ACCESS_KEY);
        registry.add("app.media.secret-key", () -> SECRET_KEY);
        registry.add("app.media.public-url", () -> endpoint + "/ecommerce-media");
        registry.add("app.media.provision", () -> "true");
    }

    private final HttpClient browser = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private MediaFiles mediaFiles;

    @Autowired
    private MediaProperties mediaProperties;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Product product;

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.save(new Category("Media " + UUID.randomUUID()));
        product = productRepository.save(new Product("Lamp", null, new BigDecimal("34.50"), 3, category));
    }

    @Test
    void productImage_isServedPubliclyAsJpeg_andCachedByBrowsers() throws Exception {
        ProductResponse response = productService.replaceImage(product.getId(), upload(1600, 900));

        assertThat(response.imageUrl()).matches(mediaProperties.publicUrl() + "/products/[0-9a-f-]{36}\\.jpg");
        assertThat(productService.findById(product.getId()).imageUrl()).isEqualTo(response.imageUrl());
        HttpResponse<byte[]> image = get(response.imageUrl());
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.headers().firstValue("Content-Type")).hasValue("image/jpeg");
        assertThat(image.headers().firstValue("Cache-Control")).hasValueSatisfying(value -> assertThat(value).contains("immutable"));
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(image.body()));
        // 16:9 upload: reduced to the width of its use, nothing cut.
        assertThat(stored.getWidth()).isEqualTo(1200);
        assertThat(stored.getHeight()).isEqualTo(675);
    }

    @Test
    void replacingTheImage_deletesThePreviousObject() throws Exception {
        String first = productService.replaceImage(product.getId(), upload(400, 300)).imageUrl();

        String second = productService.replaceImage(product.getId(), upload(400, 300)).imageUrl();

        assertThat(second).isNotEqualTo(first);
        assertThat(get(second).statusCode()).isEqualTo(200);
        assertThat(get(first).statusCode()).isNotEqualTo(200);
    }

    @Test
    void deletingTheProduct_deletesItsImage() throws Exception {
        String url = productService.replaceImage(product.getId(), upload(400, 300)).imageUrl();

        productService.delete(product.getId());

        assertThat(get(url).statusCode()).isNotEqualTo(200);
    }

    @Test
    void rolledBackTransaction_leavesNoOrphanObject() throws Exception {
        String key = transactionTemplate.execute(status -> {
            String stored = mediaFiles.store(ImageKind.PRODUCT, upload(400, 300));
            status.setRollbackOnly();
            return stored;
        });

        assertThat(get(mediaProperties.publicUrl() + "/" + key).statusCode()).isNotEqualTo(200);
    }

    @Test
    void avatar_isSquare_andErasedOnDeletion() throws Exception {
        CustomerResponse customer = customerService.provision(new CustomerIdentity(UUID.randomUUID().toString(),
                "media-" + UUID.randomUUID() + "@example.com", true, "Media Test"));

        String url = customerService.replaceAvatar(customer.id(), upload(800, 600)).avatarUrl();
        BufferedImage avatar = ImageIO.read(new ByteArrayInputStream(get(url).body()));
        assertThat(avatar.getWidth()).isEqualTo(256);
        assertThat(avatar.getHeight()).isEqualTo(256);

        customerService.deleteAvatar(customer.id());

        assertThat(customerService.findById(customer.id()).avatarUrl()).isNull();
        assertThat(get(url).statusCode()).isNotEqualTo(200);
    }

    @Test
    void anonymousVisitors_canReadObjects_butNeitherListNorWriteTheBucket() throws Exception {
        assertThat(get(mediaProperties.publicUrl()).statusCode()).isNotEqualTo(200);

        HttpResponse<byte[]> write = browser.send(HttpRequest.newBuilder(URI.create(mediaProperties.publicUrl() + "/intruder.jpg"))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[]{1})).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(write.statusCode()).isEqualTo(403);
    }

    private HttpResponse<byte[]> get(String url) throws IOException, InterruptedException {
        return browser.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static MockMultipartFile upload(int width, int height) {
        BufferedImage picture = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = picture.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        try {
            ImageIO.write(picture, "png", png);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new MockMultipartFile("file", "picture.png", "image/png", png.toByteArray());
    }
}
