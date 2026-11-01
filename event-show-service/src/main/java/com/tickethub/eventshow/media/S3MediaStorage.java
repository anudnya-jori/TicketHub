package com.tickethub.eventshow.media;

import com.tickethub.eventshow.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.Optional;

/** Amazon S3 implementation. The bucket is PRIVATE: files are only reachable through this service. */
@Component
public class S3MediaStorage implements MediaStorage {

    private static final Logger log = LoggerFactory.getLogger(S3MediaStorage.class);

    private final S3Client s3;
    private final String bucket;

    public S3MediaStorage(S3Client s3, @Value("${media.s3.bucket}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        try {
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucket).key(key).contentType(contentType)
                    .serverSideEncryption(ServerSideEncryption.AES256)
                    .build(), RequestBody.fromBytes(bytes));
        } catch (SdkException e) {
            throw unavailable("put", key, e);
        }
    }

    @Override
    public Optional<StoredObject> get(String key) {
        try {
            ResponseBytes<GetObjectResponse> object = s3.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            return Optional.of(new StoredObject(object.asByteArray(), object.response().contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (SdkException e) {
            throw unavailable("get", key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            // deleting a replaced poster is housekeeping: log, never fail the request
            log.warn("MEDIA_DELETE_FAILED key={}: {}", key, e.getMessage());
        }
    }

    private ApiException unavailable(String op, String key, SdkException e) {
        log.error("MEDIA_STORAGE_FAILED op={} key={}: {}", op, key, e.getMessage());
        return ApiException.unavailable("MEDIA_STORAGE_UNAVAILABLE",
                "Media storage is temporarily unavailable. Please try again later.");
    }
}
