package com.github.axthosarourirs;

import nva.commons.core.paths.UriWrapper;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static com.github.axthosarourirs.RandomUtils.randomString;
import static java.util.Objects.nonNull;

public class S3Driver {

    private static final String EMPTY_FRAGMENT = null;
    private final S3Client s3;
    private final String bucketName;

    public S3Driver(S3Client s3, String bucketName) {
        this.s3 = s3;
        this.bucketName = bucketName;
    }

    public static S3Driver create(String bucketName) {
        return new S3Driver(S3Client.create(), bucketName);
    }

    public URI listAllBuckets() throws IOException {
        List<String> bucketNames = this.s3.listBuckets()
                .buckets().stream()
                .map(Bucket::name)
                .collect(Collectors.toList());
        var outputFile = Path.of("existingBuckets.txt").toUri();
        return IoUtils.writeToFile(outputFile, bucketNames);
    }

    public void deleteBucket(String bucketName) {
        deleteVersions(bucketName);
        deleteObjects(bucketName);
        s3.deleteBucket(DeleteBucketRequest.builder().bucket(bucketName).build());

    }



    private void deleteVersions(String bucketName) {
        var nextMarker = deleteVersionsBatch(bucketName, null);
        while (nonNull(nextMarker)) {
            nextMarker = deleteVersionsBatch(bucketName, nextMarker);
        }
    }

    private void deleteObjects(String bucketName) {
        var nextMarker = deleteObjectsBatch(bucketName, null);
        while (nonNull(nextMarker)) {
            nextMarker = deleteObjectsBatch(bucketName, nextMarker);
        }
    }

    private String deleteObjectsBatch(String bucketName, String startAfter) {
        var listObjectsRequest = ListObjectsV2Request.builder().bucket(bucketName).startAfter(startAfter).build();
        var listingResult = s3.listObjectsV2(listObjectsRequest);
        deleteObjects(listingResult);
        return listingResult.continuationToken();
    }



    private void deleteObjects(ListObjectsV2Response listingResult) {
        var identifiers = listingResult.contents().stream()
                .map(object -> ObjectIdentifier.builder().key(object.key()).build())
                .collect(Collectors.toList());
        s3.deleteObjects(DeleteObjectsRequest.builder().bucket(bucketName).delete(Delete.builder().objects(identifiers).build()).build());
    }

    private void deleteVersions(ListObjectVersionsResponse listingResult) {
        var identifiers = listingResult.versions().stream()
                .map(version -> ObjectIdentifier.builder().key(version.key()).versionId(version.versionId()).build())
                .collect(Collectors.toList());
        s3.deleteObjects(DeleteObjectsRequest.builder().delete(Delete.builder().objects(identifiers).build()).build());
    }

    private String deleteVersionsBatch(String bucketName, String startPoint) {
        var listRequest = ListObjectVersionsRequest.builder().bucket(bucketName).keyMarker(startPoint).build();
        var versions = this.s3.listObjectVersions(listRequest);
        deleteVersions(versions);
        return versions.nextKeyMarker();
    }

    public URI insertFile(String someContent, Path folder) {
        byte[] byteContent = transformContentToBytes(someContent);
        var putObjectRequest = createPutRequest(folder,byteContent.length);
        PutObjectResponse response = s3.putObject(putObjectRequest, RequestBody.fromBytes(byteContent));
        return UriWrapper.fromUri("s3://" + bucketName).addChild(putObjectRequest.key()).getUri();
    }

    private PutObjectRequest createPutRequest(Path folder, long length) {
        return PutObjectRequest.builder()
                .bucket(bucketName)
                .key(folder.resolve(randomString()).toString())
                .contentLength(length)
                .build();
    }

    private byte[] transformContentToBytes(String input) {
        return input.getBytes(StandardCharsets.UTF_8);
    }


}
