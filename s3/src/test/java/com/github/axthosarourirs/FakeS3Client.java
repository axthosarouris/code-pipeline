package com.github.axthosarourirs;

import nva.commons.core.paths.UnixPath;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static nva.commons.core.attempt.Try.attempt;

public class FakeS3Client implements S3Client {

    private static final int DEFAULT_MAX_KEYS = 10;
    private final List<String> bucketNames;
    Map<EmulatedBucket, Map<UnixPath, ByteBuffer>> contents;

    public FakeS3Client(String bucketName, String... moreBucketNames) {
        this.bucketNames = Stream.concat(Stream.of(bucketName), Arrays.stream(moreBucketNames)).collect(Collectors.toList());
        this.contents = new ConcurrentHashMap<>();
    }


    @Override
    public ListBucketsResponse listBuckets() {
        var buckets = bucketNames.stream().map(bucketName -> Bucket.builder().name(bucketName).build()).collect(Collectors.toList());
        return ListBucketsResponse.builder().buckets(buckets).build();

    }

    public ListObjectVersionsResponse listObjectVersions(ListObjectVersionsRequest listObjectVersionsRequest)
            throws AwsServiceException, SdkClientException, S3Exception {
        return ListObjectVersionsResponse.builder().build();
    }

    public ListObjectsV2Response listObjectsV2(ListObjectsV2Request listObjectsV2Request) throws NoSuchBucketException,
            AwsServiceException, SdkClientException, S3Exception {
        var bucket = new EmulatedBucket(listObjectsV2Request.bucket());

        var allPaths = contents.get(bucket).keySet().stream()
                .map(UnixPath::toString)
                .sorted()
                .collect(Collectors.toList());
        var s3Objects = allPaths.stream()
                .map(path -> S3Object.builder().key(path).build())
                .collect(Collectors.toList());
        var startIndex = Math.max(allPaths.indexOf(listObjectsV2Request.startAfter()), 0);
        var maxKeys = attempt(() -> Math.min(listObjectsV2Request.maxKeys(), DEFAULT_MAX_KEYS))
                .orElse(fail -> DEFAULT_MAX_KEYS);
        int endIndex = Math.min(startIndex + maxKeys, allPaths.size());
        var partialResult = allPaths.subList(startIndex, endIndex);
        var nextStartPoint = endIndex < allPaths.size() ? partialResult.get(endIndex) : null;
        return ListObjectsV2Response.builder().contents(s3Objects).nextContinuationToken(nextStartPoint).build();

    }

    @Override
    public DeleteObjectsResponse deleteObjects(DeleteObjectsRequest deleteObjectsRequest) throws AwsServiceException,
            SdkClientException, S3Exception {
        var bucket = new EmulatedBucket(deleteObjectsRequest.bucket());
        deleteObjectsRequest.delete()
                .objects().stream()
                .map(ObjectIdentifier::key)
                .forEach(key -> contents.get(bucket).remove(UnixPath.of(key)));
        return DeleteObjectsResponse.builder().build();
    }

    @Override
    public DeleteBucketResponse deleteBucket(DeleteBucketRequest deleteBucketRequest) throws AwsServiceException,
            SdkClientException, S3Exception {
        var bucket = new EmulatedBucket(deleteBucketRequest.bucket());
        if (contents.get(bucket).isEmpty()) {
            return DeleteBucketResponse.builder().build();
        }
        throw new IllegalStateException("Cannot delete non empty bucket");
    }


    @Override
    public PutObjectResponse putObject(PutObjectRequest putObjectRequest, RequestBody requestBody) throws AwsServiceException, SdkClientException, S3Exception {
        attempt(() -> insertContents(putObjectRequest, requestBody)).orElseThrow();
        return PutObjectResponse.builder().build();
    }

    private Void insertContents(PutObjectRequest request, RequestBody requestBody) throws IOException {
        try (var content = requestBody.contentStreamProvider().newStream()) {
            var bytes = ByteBuffer.wrap(content.readAllBytes());
            insertToBucket(request, bytes);
            return null;
        }
    }

    private void insertToBucket(PutObjectRequest request, ByteBuffer bytes) {
        var bucket = createBucket(request);
        contents.get(bucket).put(UnixPath.of(request.key()), bytes);
    }

    private EmulatedBucket createBucket(PutObjectRequest request) {
        var bucket = new EmulatedBucket(request.bucket());
        if (!contents.containsKey(bucket)) {
            contents.put(bucket, new ConcurrentHashMap<>());
        }
        return bucket;
    }


    @Override
    public String serviceName() {
        return "FakeS3Driver";
    }

    @Override
    public void close() {

    }

    private static class EmulatedBucket {

        private final String bucketName;

        public EmulatedBucket(String bucketName) {
            this.bucketName = bucketName;
        }


        public String getBucketName() {
            return this.bucketName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EmulatedBucket that = (EmulatedBucket) o;
            return Objects.equals(bucketName, that.bucketName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bucketName);
        }
    }
}
