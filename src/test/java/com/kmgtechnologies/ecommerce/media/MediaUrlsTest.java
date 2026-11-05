package com.kmgtechnologies.ecommerce.media;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaUrlsTest {

    private static MediaProperties properties(String publicUrl, String accessKey) {
        return new MediaProperties(URI.create("http://media:8333"), "us-east-1", "ecommerce-media", accessKey, "secret",
                publicUrl, false);
    }

    @Test
    void urlIsThePublicUrlFollowedByTheKey_withOrWithoutTrailingSlash() {
        assertThat(new MediaUrls(properties("/media", "key")).of("products/a.jpg")).isEqualTo("/media/products/a.jpg");
        assertThat(new MediaUrls(properties("http://localhost:8333/ecommerce-media/", "key")).of("avatars/b.jpg"))
                .isEqualTo("http://localhost:8333/ecommerce-media/avatars/b.jpg");
    }

    @Test
    void noKey_meansNoUrl() {
        assertThat(new MediaUrls(properties("/media", "key")).of(null)).isNull();
    }

    @Test
    void credentialsAreRequired_soAMissingEnvFileFailsAtStartup() {
        assertThatThrownBy(() -> properties("/media", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MEDIA_ACCESS_KEY");
    }

    @Test
    void provisionedBucketPolicy_onlyAllowsReadingObjects() {
        assertThat(MediaBucketProvisioner.publicReadPolicy("ecommerce-media"))
                .contains("\"Principal\":\"*\"", "\"Action\":\"s3:GetObject\"",
                        "\"Resource\":\"arn:aws:s3:::ecommerce-media/*\"")
                .doesNotContain("s3:ListBucket", "s3:PutObject");
    }
}
