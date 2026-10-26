package com.kadaibook.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

public class S3StorageService implements StorageService {

    private final S3Client s3;
    private final String bucket;
    private final String kmsKeyId;

    public S3StorageService(S3Client s3, String bucket, String kmsKeyId) {
        this.s3 = s3;
        this.bucket = bucket;
        this.kmsKeyId = kmsKeyId;
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .serverSideEncryption(ServerSideEncryption.AWS_KMS)
                .ssekmsKeyId(kmsKeyId)
                .build();
        s3.putObject(request, RequestBody.fromBytes(data));
    }

    @Override
    public byte[] get(String key) {
        try (var response = s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            return response.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("could not read page image from S3", exception);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
