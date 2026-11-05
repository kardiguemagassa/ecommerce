package com.kmgtechnologies.ecommerce.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Development and tests ({@code app.media.provision=true}): creates the bucket when missing and lets anyone read its
 * objects (standard S3 bucket policy, {@code s3:GetObject} only: no listing, no writing). Images are public by nature
 * and their keys are random. Runs at startup and fails it when the storage is unreachable, like the database would.
 */
@Component
@ConditionalOnProperty(name = "app.media.provision", havingValue = "true")
public class MediaBucketProvisioner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MediaBucketProvisioner.class);

    private final S3Client s3;
    private final String bucket;

    public MediaBucketProvisioner(S3Client s3, MediaProperties properties) {
        this.s3 = s3;
        this.bucket = properties.bucket();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!exists()) {
            s3.createBucket(request -> request.bucket(bucket));
            log.info("Media bucket {} created", bucket);
        }
        s3.putBucketPolicy(request -> request.bucket(bucket).policy(publicReadPolicy(bucket)));
    }

    static String publicReadPolicy(String bucket) {
        return """
                {"Version":"2012-10-17","Statement":[{"Sid":"PublicRead","Effect":"Allow","Principal":"*",\
                "Action":"s3:GetObject","Resource":"arn:aws:s3:::%s/*"}]}""".formatted(bucket);
    }

    private boolean exists() {
        try {
            s3.headBucket(request -> request.bucket(bucket));
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }
}
