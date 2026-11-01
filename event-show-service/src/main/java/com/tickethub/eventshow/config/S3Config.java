package com.tickethub.eventshow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.time.Duration;

/**
 * S3 client. Credentials are never configured here: the AWS default provider chain finds them
 * (environment variables locally, an IAM role on AWS). Every call has a time limit (ST-16).
 */
@Configuration
public class S3Config {

    private static final Logger log = LoggerFactory.getLogger(S3Config.class);

    @Bean
    public S3Client s3Client(@Value("${media.s3.region}") String region,
                             @Value("${media.s3.endpoint:}") String endpoint,
                             @Value("${media.s3.path-style:false}") boolean pathStyle) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .forcePathStyle(pathStyle)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(8))
                        .apiCallAttemptTimeout(Duration.ofSeconds(4))
                        .build());
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    /** Dev convenience: make sure the (private) bucket exists. Never stops the service from starting. */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket(ApplicationReadyEvent event) {
        var env = event.getApplicationContext().getEnvironment();
        if (!env.getProperty("media.s3.create-bucket-on-start", Boolean.class, false)) {
            return;
        }
        String bucket = env.getProperty("media.s3.bucket");
        S3Client s3 = event.getApplicationContext().getBean(S3Client.class);
        try {
            try {
                s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            } catch (NoSuchBucketException missing) {
                s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("Created media bucket {}", bucket);
            }
            s3.putPublicAccessBlock(PutPublicAccessBlockRequest.builder().bucket(bucket)
                    .publicAccessBlockConfiguration(PublicAccessBlockConfiguration.builder()
                            .blockPublicAcls(true).ignorePublicAcls(true)
                            .blockPublicPolicy(true).restrictPublicBuckets(true).build())
                    .build());
        } catch (Exception e) {
            log.warn("MEDIA_BUCKET_CHECK_FAILED bucket={} (poster upload will return 503 until storage is reachable): {}",
                    bucket, e.getMessage());
        }
    }
}
