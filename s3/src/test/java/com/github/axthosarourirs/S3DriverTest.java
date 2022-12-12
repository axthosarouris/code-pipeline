package com.github.axthosarourirs;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static com.github.axthosarourirs.RandomUtils.randomString;
import static org.assertj.core.api.Assertions.assertThat;

class S3DriverTest {


    public static final Path EMPTY_PATH = Path.of("");

    @Test
    void shouldListAllBucketsInAccount() throws IOException {
        var bucketName = "someBucket";
        var anotherBucketName = "someOtherBucket";
        var fakeS3Client = new FakeS3Client(bucketName, anotherBucketName);
        var s3Driver = new S3Driver(fakeS3Client, bucketName);
        var bucketsListFile = s3Driver.listAllBuckets();
        var contents = IoUtils.readFileLines(bucketsListFile);
        assertThat(contents).containsExactly(bucketName, anotherBucketName);
    }

    @Test
    void shouldListAllBucketsInAccountOnlineTest() throws IOException {
        var s3Driver = new S3Driver(new FakeS3Client("someBucket"), "someBucket");
        var bucketsListFile = s3Driver.listAllBuckets();
        var contents = IoUtils.readFileLines(bucketsListFile);
        assertThat(contents).isNotEmpty();
    }

    @Test
    void shouldDeleteNonEmptyBucket() throws IOException {
        var bucket = "someBucket";
        var s3Driver = new S3Driver(new FakeS3Client(bucket), bucket);
        var fileLocation = s3Driver.insertFile(randomString(), EMPTY_PATH);
        s3Driver.deleteBucket(bucket);
    }


}