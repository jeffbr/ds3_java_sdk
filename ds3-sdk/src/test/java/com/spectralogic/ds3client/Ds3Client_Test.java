/*
 * ******************************************************************************
 *   Copyright 2014-2015 Spectra Logic Corporation. All Rights Reserved.
 *   Licensed under the Apache License, Version 2.0 (the "License"). You may not use
 *   this file except in compliance with the License. A copy of the License is located at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file.
 *   This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *   CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *   specific language governing permissions and limitations under the License.
 * ****************************************************************************
 */

package com.spectralogic.ds3client;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.spectralogic.ds3client.commands.*;
import com.spectralogic.ds3client.exceptions.ContentLengthNotMatchException;
import com.spectralogic.ds3client.models.*;
import com.spectralogic.ds3client.models.bulk.*;
import com.spectralogic.ds3client.models.bulk.Objects;
import com.spectralogic.ds3client.models.tape.Tape;
import com.spectralogic.ds3client.models.tape.TapeDrive;
import com.spectralogic.ds3client.models.tape.TapeFailure;
import com.spectralogic.ds3client.models.tape.TapeLibrary;
import com.spectralogic.ds3client.networking.FailedRequestException;
import com.spectralogic.ds3client.serializer.XmlProcessingException;
import com.spectralogic.ds3client.utils.ByteArraySeekableByteChannel;
import com.spectralogic.ds3client.utils.ResourceUtils;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SignatureException;
import java.util.*;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class Ds3Client_Test {
    private static final UUID MASTER_OBJECT_LIST_JOB_ID = UUID.fromString("1a85e743-ec8f-4789-afec-97e587a26936");
    private static final String MASTER_OBJECT_LIST_XML =
        "<MasterObjectList BucketName=\"bucket8192000000\" JobId=\"1a85e743-ec8f-4789-afec-97e587a26936\" Priority=\"NORMAL\" RequestType=\"GET\" StartDate=\"2014-07-01T20:12:52.000Z\">"
        + "  <Nodes>"
        + "    <Node EndPoint=\"10.1.18.12\" HttpPort=\"80\" HttpsPort=\"443\" Id=\"a02053b9-0147-11e4-8d6a-002590c1177c\"/>"
        + "    <Node EndPoint=\"10.1.18.13\" HttpsPort=\"443\" Id=\"95e97010-8e70-4733-926c-aeeb21796848\"/>"
        + "  </Nodes>"
        + "  <Objects ChunkId=\"f58370c2-2538-4e78-a9f8-e4d2676bdf44\" ChunkNumber=\"0\" NodeId=\"a02053b9-0147-11e4-8d6a-002590c1177c\">"
        + "    <Object Name=\"client00obj000004-8000000\" InCache=\"true\" Length=\"5368709120\" Offset=\"0\"/>"
        + "    <Object Name=\"client00obj000004-8000000\" InCache=\"true\" Length=\"2823290880\" Offset=\"5368709120\"/>"
        + "  </Objects>"
        + "  <Objects ChunkId=\"4137d768-25bb-4942-9d36-b92dfbe75e01\" ChunkNumber=\"1\" NodeId=\"95e97010-8e70-4733-926c-aeeb21796848\">"
        + "    <Object Name=\"client00obj000008-8000000\" InCache=\"true\" Length=\"2823290880\" Offset=\"5368709120\"/>"
        + "    <Object Name=\"client00obj000008-8000000\" InCache=\"true\" Length=\"5368709120\" Offset=\"0\"/>"
        + "  </Objects>"
        + "</MasterObjectList>";

    @Test
    public void getService() throws IOException, SignatureException {
        final String stringResponse = "<ListAllMyBucketsResult xmlns=\"http://doc.s3.amazonaws.com/2006-03-01\">\n" +
                "<Owner><ID>ryanid</ID><DisplayName>ryan</DisplayName></Owner><Buckets><Bucket><Name>testBucket2</Name><CreationDate>2013-12-11T23:20:09</CreationDate></Bucket><Bucket><Name>bulkTest</Name><CreationDate>2013-12-11T23:20:09</CreationDate></Bucket><Bucket><Name>bulkTest1</Name><CreationDate>2013-12-11T23:20:09</CreationDate></Bucket><Bucket><Name>bulkTest2</Name><CreationDate>2013-12-11T23:20:09</CreationDate></Bucket><Bucket><Name>bulkTest3</Name><CreationDate>2013-12-11T23:20:09</CreationDate></Bucket><Bucket><Name>bulkTest4</Name><CreationDate>2013-12-11T23:20:09</CreationDate></Bucket><Bucket><Name>bulkTest5</Name><CreationDate>2013-12-11T23:20:09</CreationDate></Bucket><Bucket><Name>bulkTest6</Name><CreationDate>2013-12-11T23:20:09</CreationDate></Bucket><Bucket><Name>testBucket3</Name><CreationDate>2013-12-11T23:20:09</CreationDate></Bucket><Bucket><Name>testBucket1</Name><CreationDate>2013-12-11T23:20:09</CreationDate></Bucket><Bucket><Name>testbucket</Name><CreationDate>2013-12-11T23:20:09</CreationDate></Bucket></Buckets></ListAllMyBucketsResult>";
        
        final List<String> expectedBucketNames = Arrays.asList(
            "testBucket2",
            "bulkTest",
            "bulkTest1",
            "bulkTest2",
            "bulkTest3",
            "bulkTest4",
            "bulkTest5",
            "bulkTest6",
            "testBucket3",
            "testBucket1",
            "testbucket"
        );
        
        final GetServiceResponse response = MockNetwork
            .expecting(HttpVerb.GET, "/", null, null)
            .returning(200, stringResponse)
            .asClient()
            .getService(new GetServiceRequest());
        final ListAllMyBucketsResult result = response.getResult();
        assertThat(result.getOwner().getDisplayName(), is("ryan"));
        assertThat(result.getOwner().getId(), is("ryanid"));
        
        final List<Bucket> buckets = result.getBuckets();
        final List<String> bucketNames = new ArrayList<>();
        for (final Bucket bucket : buckets) {
            bucketNames.add(bucket.getName());
            assertThat(bucket.getCreationDate(), is("2013-12-11T23:20:09"));
        }
        assertThat(bucketNames, is(expectedBucketNames));
    }

    @Test(expected = FailedRequestException.class)
    public void getBadService() throws IOException, SignatureException {
        MockNetwork
            .expecting(HttpVerb.GET, "/", null, null)
            .returning(400, "")
            .asClient()
            .getService(new GetServiceRequest());
    }

    @Test
    public void getBucket() throws IOException, SignatureException {
        final String xmlResponse = "<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"><Name>remoteTest16</Name><Prefix/><Marker/><MaxKeys>1000</MaxKeys><IsTruncated>false</IsTruncated><Contents><Key>user/hduser/gutenberg/20417.txt.utf-8</Key><LastModified>2014-01-03T13:26:47.000Z</LastModified><ETag>8B19F3F41868106382A677C3435BDCE5</ETag><Size>674570</Size><StorageClass>STANDARD</StorageClass><Owner><ID>ryan</ID><DisplayName>ryan</DisplayName></Owner></Contents><Contents><Key>user/hduser/gutenberg/5000.txt.utf-8</Key><LastModified>2014-01-03T13:26:47.000Z</LastModified><ETag>9DE344878423E44B129730CE22B4B137</ETag><Size>1423803</Size><StorageClass>STANDARD</StorageClass><Owner><ID>ryan</ID><DisplayName>ryan</DisplayName></Owner></Contents><Contents><Key>user/hduser/gutenberg/4300.txt.utf-8</Key><LastModified>2014-01-03T13:26:47.000Z</LastModified><ETag>33EE4519EA7DDAB27CA4E2742326D70B</ETag><Size>1573150</Size><StorageClass>DEEP</StorageClass><Owner><ID>ryan</ID><DisplayName>ryan</DisplayName></Owner></Contents></ListBucketResult>";
        
        final ListBucketResult result = MockNetwork
            .expecting(HttpVerb.GET, "/remoteTest16", null, null)
            .returning(200, xmlResponse)
            .asClient()
            .getBucket(new GetBucketRequest("remoteTest16"))
            .getResult();
        
        assertThat(result.getName(), is("remoteTest16"));
        assertThat(result.getPrefix(), is(nullValue()));
        assertThat(result.getMarker(), is(nullValue()));
        assertThat(result.getMaxKeys(), is(1000));

        final List<Contents> contentsList = result.getContentsList();
        assertThat(contentsList, is(notNullValue()));
        assertThat(contentsList.size(), is(3));
        this.assertContentsEquals(
            contentsList.get(0),
            "user/hduser/gutenberg/20417.txt.utf-8",
            "2014-01-03T13:26:47.000Z",
            "8B19F3F41868106382A677C3435BDCE5",
            674570,
            "STANDARD"
        );
        this.assertContentsEquals(
            contentsList.get(1),
            "user/hduser/gutenberg/5000.txt.utf-8",
            "2014-01-03T13:26:47.000Z",
            "9DE344878423E44B129730CE22B4B137",
            1423803,
            "STANDARD"
        );
        this.assertContentsEquals(
            contentsList.get(2),
            "user/hduser/gutenberg/4300.txt.utf-8",
            "2014-01-03T13:26:47.000Z",
            "33EE4519EA7DDAB27CA4E2742326D70B",
            1573150,
            "DEEP"
        );
    }

    private void assertContentsEquals(
            final Contents contents,
            final String key,
            final String lastModified,
            final String eTag,
            final long size,
            final String storageClass) {
        assertThat(contents.getKey(), is(key));
        assertThat(contents.getLastModified(), is(lastModified));
        assertThat(contents.geteTag(), is(eTag));
        assertThat(contents.getSize(), is(size));
        assertThat(contents.getStorageClass(), is(storageClass));
    }

    @Test
    public void getObjects() throws IOException, SignatureException {
        final Map<String, String> queryParams = new HashMap<>();
        final String bucketName = "bucket";
        queryParams.put("bucket_id", bucketName);

        final String stringResponse = "<Data><S3Object><BucketId>a24d14f3-e2f0-4bfb-ab71-f99d5ef43745</BucketId><CreationDate>2015-09-21 20:06:47.694</CreationDate><Id>e37c3ce0-12aa-4f54-87e3-42532aca0e5e</Id><Name>beowulf.txt</Name><Type>DATA</Type><Version>1</Version></S3Object><S3Object><BucketId>a24d14f3-e2f0-4bfb-ab71-f99d5ef43745</BucketId><CreationDate>2015-09-21 20:06:47.779</CreationDate><Id>dc628815-c723-4c4e-b68b-5f5d10f38af5</Id><Name>sherlock_holmes.txt</Name><Type>DATA</Type><Version>1</Version></S3Object><S3Object><BucketId>a24d14f3-e2f0-4bfb-ab71-f99d5ef43745</BucketId><CreationDate>2015-09-21 20:06:47.772</CreationDate><Id>4f6985fd-fbae-4421-ba27-66fdb96187c5</Id><Name>tale_of_two_cities.txt</Name><Type>DATA</Type><Version>1</Version></S3Object><S3Object><BucketId>a24d14f3-e2f0-4bfb-ab71-f99d5ef43745</BucketId><CreationDate>2015-09-21 20:06:47.696</CreationDate><Id>82c18910-fadb-4461-a152-bf714ae91b55</Id><Name>ulysses.txt</Name><Type>DATA</Type><Version>1</Version></S3Object></Data>";

        final List<S3Object> objects = MockNetwork
                .expecting(HttpVerb.GET, "/_rest_/object/", queryParams, null)
                .returning(200, stringResponse)
                .asClient()
                .getObjects(new GetObjectsRequest().withBucket(bucketName))
                .getS3ObjectList()
                .getObjects();

        final S3Object beowulf = new S3Object("a24d14f3-e2f0-4bfb-ab71-f99d5ef43745", "2015-09-21 20:06:47.694",
                UUID.fromString("e37c3ce0-12aa-4f54-87e3-42532aca0e5e"), "beowulf.txt",
                GetObjectsRequest.ObjectType.DATA, 1);

        final S3Object notBeowulf = new S3Object("a24d14f3-e2f0-4bfb-ab71-f99d5ef43745", "2015-09-21 20:06:47.694",
                UUID.fromString("e37c3ce0-12aa-4f54-87e3-42532aca0e5e"), "notBeowulf.txt",
                GetObjectsRequest.ObjectType.DATA, 1);

        assertThat(objects.size(), is(4));
        assertThat(s3ObjectExists(objects, beowulf), is(true));
        assertThat(s3ObjectExists(objects, notBeowulf), is(false));
    }

    private boolean s3ObjectExists(final List<S3Object> objects, final S3Object s3obj) {
        for (final S3Object obj : objects) {
            if (s3obj.equals(obj)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void putBucket() throws IOException, SignatureException {
        MockNetwork
            .expecting(HttpVerb.PUT, "/bucketName", null, null)
            .returning(200, "")
            .asClient()
            .putBucket(new PutBucketRequest("bucketName"));
    }
    
    @Test
    public void deleteBucket() throws IOException, SignatureException {
        MockNetwork
            .expecting(HttpVerb.DELETE, "/bucketName", null, null)
            .returning(204, "")
            .asClient()
            .deleteBucket(new DeleteBucketRequest("bucketName"));
    }

    @Test
    public void deleteFolder() throws IOException, SignatureException {
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("bucketId", "bucketName");
        queryParams.put("recursive", null);

        MockNetwork
                .expecting(HttpVerb.DELETE, "/_rest_/folder/folderName", queryParams, null)
                .returning(204, "")
                .asClient()
                .deleteFolder(new DeleteFolderRequest("bucketName", "folderName"));
    }

    @Test
    public void deleteObject() throws IOException, SignatureException {
        MockNetwork
            .expecting(HttpVerb.DELETE, "/bucketName/my/file.txt", null, null)
            .returning(204, "")
            .asClient()
            .deleteObject(new DeleteObjectRequest("bucketName", "my/file.txt"));
    }

    @Test
    public void multiObjectDelete() throws IOException, SignatureException {
        final List<String> objsToDelete = Lists.newArrayList("sample1.txt", "sample2.txt");
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("delete", null);
        final String payload = "<Delete><Quiet>false</Quiet><Object><Key>sample1.txt</Key></Object><Object><Key>sample2.txt</Key></Object></Delete>";

        final DeleteMultipleObjectsResponse response = MockNetwork
            .expecting(HttpVerb.POST, "/bucketName", queryParams, payload)
            .returning(200, "<DeleteResult>\n" +
                "  <Deleted>\n" +
                "    <Key>sample1.txt</Key>\n" +
                "  </Deleted>\n" +
                "  <Error>\n" +
                "    <Key>sample2.txt</Key>\n" +
                "    <Code>AccessDenied</Code>\n" +
                "    <Message>Access Denied</Message>\n" +
                "  </Error>\n" +
                "</DeleteResult>")
            .asClient()
            .deleteMultipleObjects(new DeleteMultipleObjectsRequest("bucketName", objsToDelete));
        assertThat(response.getResult().getDeletedList().size(), is(1));
        assertThat(response.getResult().getErrorList().size(), is(1));
    }

    @Test(expected = FailedRequestException.class)
    public void getBadBucket() throws IOException, SignatureException {
        MockNetwork
            .expecting(HttpVerb.GET, "/remoteTest16", null, null)
            .returning(400, "")
            .asClient()
            .getBucket(new GetBucketRequest("remoteTest16"));
    }

    @Test
    public void getObject() throws IOException, SignatureException {
        final String jobIdString = "a4a586a1-cb80-4441-84e2-48974e982d51";
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("job", jobIdString);
        queryParams.put("offset", Long.toString(0));

        final ByteArraySeekableByteChannel resultChannel = new ByteArraySeekableByteChannel();
        final String stringResponse = "Response";
        MockNetwork
            .expecting(HttpVerb.GET, "/bucketName/object", queryParams, null)
            .returning(200, stringResponse)
            .asClient()
            .getObject(new GetObjectRequest("bucketName", "object", 0, UUID.fromString(jobIdString), resultChannel));
        assertThat(resultChannel.toString(), is(stringResponse));
    }
    
    @Test
    public void putObject() throws IOException, SignatureException, URISyntaxException {
        final String jobIdString = "a4a586a1-cb80-4441-84e2-48974e982d51";
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("job", jobIdString);
        queryParams.put("offset", Long.toString(0));
        
        final Path resourcePath = ResourceUtils.loadFileResource("LoremIpsumTwice.txt");
        final byte[] fileBytes = Files.readAllBytes(resourcePath);
        final String output = new String(fileBytes, Charset.forName("UTF-8"));
        final FileChannel channel = FileChannel.open(resourcePath, StandardOpenOption.READ);
        MockNetwork
            .expecting(HttpVerb.PUT, "/bucketName/objectName", queryParams, output)
            .returning(200, "")
            .asClient()
            .putObject(new PutObjectRequest("bucketName", "objectName", UUID.fromString(jobIdString), fileBytes.length, 0, channel));
    }
    
    @Test
    public void putObjectWithMetadata() throws IOException, SignatureException, URISyntaxException {
        final String jobIdString = "a4a586a1-cb80-4441-84e2-48974e982d51";
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("job", jobIdString);
        queryParams.put("offset", Long.toString(0));
        
        final Path resourcePath = ResourceUtils.loadFileResource("LoremIpsumTwice.txt");
        final byte[] fileBytes = Files.readAllBytes(resourcePath);
        final String output = new String(fileBytes, Charset.forName("UTF-8"));
        final FileChannel channel = FileChannel.open(resourcePath, StandardOpenOption.READ);

        final PutObjectRequest por = new PutObjectRequest("bucketName", "objectName", UUID.fromString(jobIdString), fileBytes.length, 0, channel);
        
        final Multimap<String, String> expectedRequestHeaders = TreeMultimap.create();
        expectedRequestHeaders.put("Naming-Convention", "s3"); //default from AbstractRequest
        expectedRequestHeaders.put("x-amz-meta-test1", "test1value");
        expectedRequestHeaders.put("x-amz-meta-test2", "test2value");
        expectedRequestHeaders.put("x-amz-meta-test2", "test2value2");
        
        por.withMetaData("x-amz-meta-test1", "test1value");
        por.withMetaData("test2", "test2value");
        por.withMetaData("test2", "test2value2");
        MockNetwork
            .expecting(HttpVerb.PUT, "/bucketName/objectName", queryParams, expectedRequestHeaders, output)
            .returning(200, "")
            .asClient()
            .putObject(por);
    }

    @Test
    public void headObjectWithMetadata() throws IOException, SignatureException {

        final Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("x-amz-meta-key", "value");


        final HeadObjectRequest request = new HeadObjectRequest("bucket", "obj");

        MockNetwork
                .expecting(HttpVerb.HEAD, "/bucket/obj", null, null, null)
                .returning(200, "", responseHeaders)
                .asClient()
                .headObject(request);
    }

    @Test
    public void getObjectWithMetaData() throws IOException, SignatureException {

        final Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("x-amz-meta-key", "value");

        final ByteArraySeekableByteChannel resultChannel = new ByteArraySeekableByteChannel();
        final GetObjectRequest request = new GetObjectRequest("bucket", "obj", 0, UUID.randomUUID(), resultChannel);

        MockNetwork
                .expecting(HttpVerb.GET, "/bucket/obj", null, null, null)
                .returning(200, "response", responseHeaders)
                .asClient()
                .getObject(request);

        assertThat(resultChannel.toString(), is("response"));
    }

    @Test
    public void bulkPut() throws IOException, SignatureException, XmlProcessingException {
        this.runBulkTest(BulkCommand.PUT, new BulkTestDriver() {
            @Override
            public MasterObjectList performRestCall(final Ds3Client client, final String bucket, final List<Ds3Object> objects)
                    throws SignatureException, IOException, XmlProcessingException {
                return client.bulkPut(new BulkPutRequest(bucket, objects)).getResult();
            }
        });
    }

    @Test
    public void bulkGet() throws IOException, SignatureException, XmlProcessingException {
        this.runBulkTest(BulkCommand.GET, new BulkTestDriver() {
            @Override
            public MasterObjectList performRestCall(final Ds3Client client, final String bucket, final List<Ds3Object> objects)
                    throws SignatureException, IOException, XmlProcessingException {
                return client.bulkGet(new BulkGetRequest(bucket, objects)).getResult();
            }
        });
    }
    
    private interface BulkTestDriver {
        MasterObjectList performRestCall(final Ds3Client client, final String bucket, final List<Ds3Object> objects)
                throws SignatureException, IOException, XmlProcessingException;
    }
    
    public void runBulkTest(final BulkCommand command, final BulkTestDriver driver) throws IOException, SignatureException, XmlProcessingException {
        final List<Ds3Object> objects = Arrays.asList(
            new Ds3Object("file1", 256),
            new Ds3Object("file2", 1202),
            new Ds3Object("file3", 2523)
        );

        final String expectedXmlBody;

        if (command == BulkCommand.GET) {
            expectedXmlBody = "<Objects><Object Name=\"file1\"/><Object Name=\"file2\"/><Object Name=\"file3\"/></Objects>";
        }
        else {
            expectedXmlBody = "<Objects><Object Name=\"file1\" Size=\"256\"/><Object Name=\"file2\" Size=\"1202\"/><Object Name=\"file3\" Size=\"2523\"/></Objects>";
        }

        final String xmlResponse = "<MasterObjectList BucketName=\"lib\" JobId=\"9652a41a-218a-4158-af1b-064ab9e4ef71\" Priority=\"NORMAL\" RequestType=\"PUT\" StartDate=\"2014-07-29T16:08:39.000Z\"><Nodes><Node EndPoint=\"FAILED_TO_DETERMINE_DATAPATH_IP_ADDRESS\" HttpPort=\"80\" HttpsPort=\"443\" Id=\"b18ee082-1352-11e4-945e-080027ebeb6d\"/></Nodes><Objects ChunkId=\"cfa3153f-57de-41c7-b1fb-f30fa4154232\" ChunkNumber=\"0\"><Object Name=\"file2\" InCache=\"false\" Length=\"1202\" Offset=\"0\"/><Object Name=\"file1\" InCache=\"false\" Length=\"256\" Offset=\"0\"/><Object Name=\"file3\" InCache=\"false\" Length=\"2523\" Offset=\"0\"/></Objects></MasterObjectList>";
        
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("operation", command.toString());
        
        final Ds3Client client = MockNetwork
            .expecting(HttpVerb.PUT, "/_rest_/bucket/bulkTest", queryParams, expectedXmlBody)
            .returning(200, xmlResponse)
            .asClient();
        
        final List<Objects> objectListList = driver.performRestCall(client, "bulkTest", objects).getObjects();
        assertThat(objectListList.size(), is(1));
        
        final List<BulkObject> objectList = objectListList.get(0).getObjects();
        assertThat(objectList.size(), is(3));

        assertObjectEquals(objectList.get(0), "file2", 1202);
        assertObjectEquals(objectList.get(1), "file1", 256);
        assertObjectEquals(objectList.get(2), "file3", 2523);
    }
    
    private static void assertObjectEquals(final BulkObject object, final String name, final long size) {
        assertThat(object.getName(), is(name));
        assertThat(object.getLength(), is(size));
    }
    
    @Test
    public void allocateJobChunk() throws SignatureException, IOException {
        final String responseString =
            "<Objects ChunkId=\"203f6886-b058-4f7c-a012-8779176453b1\" ChunkNumber=\"3\" NodeId=\"a02053b9-0147-11e4-8d6a-002590c1177c\">"
            + "  <Object Name=\"client00obj000004-8000000\" InCache=\"true\" Length=\"5368709120\" Offset=\"0\"/>"
            + "  <Object Name=\"client00obj000004-8000000\" InCache=\"true\" Length=\"2823290880\" Offset=\"5368709120\"/>"
            + "  <Object Name=\"client00obj000003-8000000\" InCache=\"true\" Length=\"2823290880\" Offset=\"5368709120\"/>"
            + "  <Object Name=\"client00obj000003-8000000\" InCache=\"true\" Length=\"5368709120\" Offset=\"0\"/>"
            + "</Objects>";
        final UUID chunkId = UUID.fromString("203f6886-b058-4f7c-a012-8779176453b1");
        final UUID nodeId = UUID.fromString("a02053b9-0147-11e4-8d6a-002590c1177c");

        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("operation", "allocate");
        final AllocateJobChunkResponse response = MockNetwork
            .expecting(HttpVerb.PUT, "/_rest_/job_chunk/203f6886-b058-4f7c-a012-8779176453b1", queryParams, null)
            .returning(200, responseString)
            .asClient()
            .allocateJobChunk(new AllocateJobChunkRequest(chunkId));
        
        assertThat(response.getStatus(), is(AllocateJobChunkResponse.Status.ALLOCATED));
        final Objects chunk = response.getObjects();
        
        assertThat(chunk.getChunkId(), is(chunkId));
        assertThat(chunk.getChunkNumber(), is(3L));
        assertThat(chunk.getNodeId(), is(nodeId));
        
        final List<BulkObject> objects = chunk.getObjects();
        assertThat(objects.size(), is(4));

        final BulkObject object0 = objects.get(0);
        assertThat(object0.getName(), is("client00obj000004-8000000"));
        assertThat(object0.isInCache(), is(true));
        assertThat(object0.getOffset(), is(0L));
        assertThat(object0.getLength(), is(5368709120L));

        final BulkObject object1 = objects.get(1);
        assertThat(object1.getName(), is("client00obj000004-8000000"));
        assertThat(object1.isInCache(), is(true));
        assertThat(object1.getOffset(), is(5368709120L));
        assertThat(object1.getLength(), is(2823290880L));

        final BulkObject object2 = objects.get(2);
        assertThat(object2.getName(), is("client00obj000003-8000000"));
        assertThat(object2.isInCache(), is(true));
        assertThat(object2.getOffset(), is(5368709120L));
        assertThat(object2.getLength(), is(2823290880L));

        final BulkObject object3 = objects.get(3);
        assertThat(object3.getName(), is("client00obj000003-8000000"));
        assertThat(object3.isInCache(), is(true));
        assertThat(object3.getOffset(), is(0L));
        assertThat(object3.getLength(), is(5368709120L));
    }
    
    @Test
    public void allocateJobChunkReturnsRetryAfter() throws SignatureException, IOException {
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("operation", "allocate");
        final Map<String, String> headers = new HashMap<>();
        headers.put("retry-after", "300");
        final AllocateJobChunkResponse response = MockNetwork
            .expecting(HttpVerb.PUT, "/_rest_/job_chunk/203f6886-b058-4f7c-a012-8779176453b1", queryParams, null)
            .returning(503, "", headers)
            .asClient()
            .allocateJobChunk(new AllocateJobChunkRequest(UUID.fromString("203f6886-b058-4f7c-a012-8779176453b1")));
        
        assertThat(response.getStatus(), is(AllocateJobChunkResponse.Status.RETRYLATER));
        assertThat(response.getRetryAfterSeconds(), is(300));
    }
    
    @Test
    public void getAvailableJobChunks() throws SignatureException, IOException {

        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("job", MASTER_OBJECT_LIST_JOB_ID.toString());
        final GetAvailableJobChunksResponse response = MockNetwork
            .expecting(HttpVerb.GET, "/_rest_/job_chunk", queryParams, null)
            .returning(200, MASTER_OBJECT_LIST_XML)
            .asClient()
            .getAvailableJobChunks(new GetAvailableJobChunksRequest(MASTER_OBJECT_LIST_JOB_ID));
        
        assertThat(response.getStatus(), is(GetAvailableJobChunksResponse.Status.AVAILABLE));

        checkMasterObjectList(response.getMasterObjectList());
    }

    @Test
    public void getAvailableJobChunksReturnsRetryLater() throws SignatureException, IOException {
        final String responseString =
            "<MasterObjectList BucketName=\"bucket8192000000\" JobId=\"1a85e743-ec8f-4789-afec-97e587a26936\" Priority=\"NORMAL\" RequestType=\"GET\" StartDate=\"2014-07-01T20:12:52.000Z\">"
            + "  <Nodes>"
            + "    <Node EndPoint=\"10.1.18.12\" HttpPort=\"80\" HttpsPort=\"443\" Id=\"a02053b9-0147-11e4-8d6a-002590c1177c\"/>"
            + "    <Node EndPoint=\"10.1.18.13\" HttpsPort=\"443\" Id=\"95e97010-8e70-4733-926c-aeeb21796848\"/>"
            + "  </Nodes>"
            + "</MasterObjectList>";
        final UUID jobId = UUID.fromString("1a85e743-ec8f-4789-afec-97e587a26936");

        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("job", jobId.toString());
        final Map<String, String> headers = new HashMap<>();
        headers.put("Retry-After", "300");
        final GetAvailableJobChunksResponse response = MockNetwork
            .expecting(HttpVerb.GET, "/_rest_/job_chunk", queryParams, null)
            .returning(200, responseString, headers)
            .asClient()
            .getAvailableJobChunks(new GetAvailableJobChunksRequest(jobId));
        
        assertThat(response.getStatus(), is(GetAvailableJobChunksResponse.Status.RETRYLATER));
        assertThat(response.getRetryAfterSeconds(), is(300));
    }
    
    @Test
    public void getJobs() throws SignatureException, IOException {
        final String responseString =
            "<Jobs>"
            + "  <Job BucketName=\"bucket_1\" CachedSizeInBytes=\"69880\" ChunkClientProcessingOrderGuarantee=\"IN_ORDER\" CompletedSizeInBytes=\"0\" JobId=\"0807ff11-a9f6-4d55-bb92-b452c1bb00c7\" OriginalSizeInBytes=\"69880\" Priority=\"NORMAL\" RequestType=\"PUT\" StartDate=\"2014-09-04T17:23:45.000Z\" UserId=\"a7d3eff9-e6d2-4e37-8a0b-84e76211a18a\" UserName=\"spectra\" WriteOptimization=\"PERFORMANCE\">"
            + "    <Nodes>"
            + "      <Node EndPoint=\"10.10.10.10\" HttpPort=\"80\" HttpsPort=\"443\" Id=\"edb8cc38-32f2-11e4-bce1-080027ecf0d4\"/>"
            + "    </Nodes>"
            + "  </Job>"
            + "  <Job BucketName=\"bucket_2\" CachedSizeInBytes=\"0\" ChunkClientProcessingOrderGuarantee=\"IN_ORDER\" CompletedSizeInBytes=\"0\" JobId=\"c18554ba-e3a8-4905-91fd-3e6eec71bf45\" OriginalSizeInBytes=\"69880\" Priority=\"HIGH\" RequestType=\"GET\" StartDate=\"2014-09-04T17:24:04.000Z\" UserId=\"a7d3eff9-e6d2-4e37-8a0b-84e76211a18a\" UserName=\"spectra\" WriteOptimization=\"CAPACITY\">"
            + "    <Nodes>"
            + "      <Node EndPoint=\"10.10.10.10\" HttpPort=\"80\" HttpsPort=\"443\" Id=\"edb8cc38-32f2-11e4-bce1-080027ecf0d4\"/>"
            + "    </Nodes>"
            + "  </Job>"
            + "</Jobs>";
        final GetJobsResponse response = MockNetwork
            .expecting(HttpVerb.GET, "/_rest_/job", null, null)
            .returning(200, responseString)
            .asClient()
            .getJobs(new GetJobsRequest());
        
        final List<JobInfo> jobs = response.getJobs();
        assertThat(jobs.size(), is(2));
        checkJob(
                jobs.get(0),
                "bucket_1",
                69880L,
                ChunkClientProcessingOrderGuarantee.IN_ORDER,
                0L,
                UUID.fromString("0807ff11-a9f6-4d55-bb92-b452c1bb00c7"),
                69880L,
                Priority.NORMAL,
                RequestType.PUT,
                "2014-09-04T17:23:45.000Z",
                UUID.fromString("a7d3eff9-e6d2-4e37-8a0b-84e76211a18a"),
                "spectra",
                WriteOptimization.PERFORMANCE
        );
        checkJob(
                jobs.get(1),
                "bucket_2",
                0L,
                ChunkClientProcessingOrderGuarantee.IN_ORDER,
                0L,
                UUID.fromString("c18554ba-e3a8-4905-91fd-3e6eec71bf45"),
                69880L,
                Priority.HIGH,
                RequestType.GET,
                "2014-09-04T17:24:04.000Z",
                UUID.fromString("a7d3eff9-e6d2-4e37-8a0b-84e76211a18a"),
                "spectra",
                WriteOptimization.CAPACITY
        );
    }

    private static void checkJob(
            final JobInfo job,
            final String bucketName,
            final long cachedSizeInBytes,
            final ChunkClientProcessingOrderGuarantee chunkProcessingOrderGuarantee,
            final long completedSizeInBytes, final UUID jobId,
            final long originalSizeInBytes, final Priority priority,
            final RequestType requestType, final String startDate,
            final UUID userId, final String userName,
            final WriteOptimization writeOptimization) {
        assertThat(job.getBucketName(), is(bucketName));
        assertThat(job.getCachedSizeInBytes(), is(cachedSizeInBytes));
        assertThat(job.getChunkClientProcessingOrderGuarantee(), is(chunkProcessingOrderGuarantee));
        assertThat(job.getCompletedSizeInBytes(), is(completedSizeInBytes));
        assertThat(job.getJobId(), is(jobId));
        assertThat(job.getOriginalSizeInBytes(), is(originalSizeInBytes));
        assertThat(job.getPriority(), is(priority));
        assertThat(job.getRequestType(), is(requestType));
        assertThat(job.getStartDate(), is(startDate));
        assertThat(job.getUserId(), is(userId));
        assertThat(job.getUserName(), is(userName));
        assertThat(job.getWriteOptimization(), is(writeOptimization));
        final Node node = job.getNodes().get(0);
        assertThat(node.getEndpoint(), is("10.10.10.10"));
        assertThat(node.getHttpPort(), is(80));
        assertThat(node.getHttpsPort(), is(443));
    }
    
    @Test
    public void getJob() throws SignatureException, IOException{
        checkMasterObjectList(
            MockNetwork
                .expecting(HttpVerb.GET, "/_rest_/job/1a85e743-ec8f-4789-afec-97e587a26936", null, null)
                .returning(200, MASTER_OBJECT_LIST_XML)
                .asClient()
                .getJob(new GetJobRequest(UUID.fromString("1a85e743-ec8f-4789-afec-97e587a26936")))
                .getMasterObjectList()
        );
    }

    private static void checkMasterObjectList(final MasterObjectList masterObjectList) {
        assertThat(masterObjectList.getBucketName(), is("bucket8192000000"));
        assertThat(masterObjectList.getJobId(), is(MASTER_OBJECT_LIST_JOB_ID));
        assertThat(masterObjectList.getPriority(), is(Priority.NORMAL));
        assertThat(masterObjectList.getRequestType(), is(RequestType.GET));
        assertThat(masterObjectList.getStartDate(), is("2014-07-01T20:12:52.000Z"));

        final List<Node> nodes = masterObjectList.getNodes();
        assertThat(nodes.size(), is(2));
        final Node node0 = nodes.get(0);
        assertThat(node0.getEndpoint(), is("10.1.18.12"));
        assertThat(node0.getHttpPort(), is(80));
        assertThat(node0.getHttpsPort(), is(443));
        final Node node1 = nodes.get(1);
        assertThat(node1.getEndpoint(), is("10.1.18.13"));
        assertThat(node1.getHttpPort(), is(0));
        assertThat(node1.getHttpsPort(), is(443)); 
        
        final List<Objects> chunkList = masterObjectList.getObjects();
        assertThat(chunkList.size(), is(2));

        final Objects chunk0 = chunkList.get(0);
        assertThat(chunk0.getChunkId(), is(UUID.fromString("f58370c2-2538-4e78-a9f8-e4d2676bdf44")));
        assertThat(chunk0.getChunkNumber(), is(0L));
        assertThat(chunk0.getNodeId(), is(UUID.fromString("a02053b9-0147-11e4-8d6a-002590c1177c")));
        final List<BulkObject> objects0 = chunk0.getObjects();
        assertThat(objects0.size(), is(2));
        final BulkObject bulkObject0_0 = objects0.get(0);
        assertThat(bulkObject0_0.getName(), is("client00obj000004-8000000"));
        assertThat(bulkObject0_0.getOffset(), is(0L));
        assertThat(bulkObject0_0.getLength(), is(5368709120L));
        assertThat(bulkObject0_0.isInCache(), is(true));
        final BulkObject bulkObject0_1 = objects0.get(1);
        assertThat(bulkObject0_1.getName(), is("client00obj000004-8000000"));
        assertThat(bulkObject0_1.getOffset(), is(5368709120L));
        assertThat(bulkObject0_1.getLength(), is(2823290880L));
        assertThat(bulkObject0_1.isInCache(), is(true));

        final Objects chunk1 = chunkList.get(1);
        assertThat(chunk1.getChunkId(), is(UUID.fromString("4137d768-25bb-4942-9d36-b92dfbe75e01")));
        assertThat(chunk1.getChunkNumber(), is(1L));
        assertThat(chunk1.getNodeId(), is(UUID.fromString("95e97010-8e70-4733-926c-aeeb21796848")));
        final List<BulkObject> objects1 = chunk1.getObjects();
        assertThat(objects1.size(), is(2));
        final BulkObject bulkObject1_0 = objects1.get(0);
        assertThat(bulkObject1_0.getName(), is("client00obj000008-8000000"));
        assertThat(bulkObject1_0.getOffset(), is(5368709120L));
        assertThat(bulkObject1_0.getLength(), is(2823290880L));
        assertThat(bulkObject1_0.isInCache(), is(true));
        final BulkObject bulkObject1_1 = objects1.get(1);
        assertThat(bulkObject1_1.getName(), is("client00obj000008-8000000"));
        assertThat(bulkObject1_1.getOffset(), is(0L));
        assertThat(bulkObject1_1.getLength(), is(5368709120L));
        assertThat(bulkObject1_1.isInCache(), is(true));
    }
    
    @Test
    public void cancelJob() throws SignatureException, IOException {
        final CancelJobResponse response = MockNetwork
            .expecting(HttpVerb.DELETE, "/_rest_/job/1a85e743-ec8f-4789-afec-97e587a26936", null, null)
            .returning(204, "")
            .asClient()
            .cancelJob(new CancelJobRequest(UUID.fromString("1a85e743-ec8f-4789-afec-97e587a26936")));
        assertThat(response, notNullValue());
    }
    
    @Test
    public void modifyJob() throws SignatureException, IOException {
        checkMasterObjectList(
                MockNetwork
                        .expecting(HttpVerb.PUT, "/_rest_/job/1a85e743-ec8f-4789-afec-97e587a26936", null, null)
                        .returning(200, MASTER_OBJECT_LIST_XML)
                        .asClient()
                        .modifyJob(new ModifyJobRequest(MASTER_OBJECT_LIST_JOB_ID))
                        .getMasterObjectList()
        );
    }

    @Test
    public void deleteTapeDrive() throws IOException, SignatureException {
        final DeleteTapeDriveResponse response = MockNetwork
            .expecting(HttpVerb.DELETE, "/_rest_/tape_drive/30a8dbf8-12e1-49dd-bede-0b4a7e1dd773", null, null)
            .returning(204, "")
            .asClient()
            .deleteTapeDrive(new DeleteTapeDriveRequest("30a8dbf8-12e1-49dd-bede-0b4a7e1dd773"));
        assertThat(response, notNullValue());
    }

    @Test
    public void deleteTapePartition() throws IOException, SignatureException {
        final DeleteTapePartitionResponse response = MockNetwork
            .expecting(HttpVerb.DELETE, "/_rest_/tape_partition/30a8dbf8-12e1-49dd-bede-0b4a7e1dd773", null, null)
            .returning(204, "")
            .asClient()
            .deleteTapePartition(new DeleteTapePartitionRequest("30a8dbf8-12e1-49dd-bede-0b4a7e1dd773"));
        assertThat(response, notNullValue());
    }
    @Test
    public void newForNode() {
        final Ds3Client client = Ds3ClientBuilder.create("endpoint", new Credentials("access", "key")).build();

        final Node node = new Node();
        node.setEndpoint("newEndpoint");
        node.setHttpPort(80);
        node.setHttpsPort(443);

        final Ds3Client newClient = client.newForNode(node);
        assertThat(newClient.getConnectionDetails().getEndpoint(), is("newEndpoint:443"));
        assertThat(newClient.getConnectionDetails().getCredentials().getClientId(), is("access"));
        assertThat(newClient.getConnectionDetails().getCredentials().getKey(), is("key"));
    }

    @Test
    public void systemHealth() throws IOException, SignatureException {
        final String responsePayload = "<Data><MsRequiredToVerifyDataPlannerHealth>0</MsRequiredToVerifyDataPlannerHealth></Data>";

        final GetSystemHealthResponse response = MockNetwork
                .expecting(HttpVerb.GET, "/_rest_/system_health", null, null)
                .returning(200, responsePayload)
                .asClient()
                .getSystemHealth(new GetSystemHealthRequest());

        assertThat(response.getSystemHealth(), is(notNullValue()));
        assertThat(response.getSystemHealth().getTimeToVerifyHealth(), is(0L));
    }

    @Test
    public void systemInformation() throws IOException, SignatureException {
        final String responsePayload = "<Data><ApiVersion>518B3F2A95B71AC7325EFB12B2937376.15F3CC0489CBCD4648ECFF0FBF371B8A</ApiVersion><BuildInformation><Branch/><Revision/><Version/></BuildInformation><SerialNumber>UNKNOWN</SerialNumber></Data>";

        final GetSystemInformationResponse response = MockNetwork
                .expecting(HttpVerb.GET, "/_rest_/system_information", null, null)
                .returning(200, responsePayload)
                .asClient()
                .getSystemInformation(new GetSystemInformationRequest());

        assertThat(response.getSystemInformation(), is(notNullValue()));
    }

    @Test
    public void getTapeLibraries() throws IOException, SignatureException {
        final String responsePayload = "<Data><TapeLibrary><Id>f4dae25d-e52a-4430-82bd-525e4f15493c</Id><ManagementUrl>a</ManagementUrl><Name>test library</Name><SerialNumber>test library</SerialNumber></TapeLibrary><TapeLibrary><Id>82bdab72-d79a-4b43-95d7-f2c16cd9aa45</Id><ManagementUrl>a</ManagementUrl><Name>test library 2</Name><SerialNumber>test library 2</SerialNumber></TapeLibrary></Data>";

        final GetTapeLibrariesResponse response = MockNetwork
                .expecting(HttpVerb.GET, "/_rest_/tape_library", null, null)
                .returning(200, responsePayload)
                .asClient()
                .getTapeLibraries(new GetTapeLibrariesRequest());

        final List<TapeLibrary> libraries = response.getTapeLibraries();

        assertThat(libraries.size(), is(2));
        assertThat(libraries.get(0).getId().toString(), is("f4dae25d-e52a-4430-82bd-525e4f15493c"));
    }

    @Test
    public void getTapeLibrary() throws IOException, SignatureException {
        final String responsePayload = "<Data><Id>e23030e5-9b8d-4594-bdd1-15d3c45abb9f</Id><ManagementUrl>a</ManagementUrl><Name>125ca16e-60e3-43b2-a26f-0bc81843745f</Name><SerialNumber>test library</SerialNumber></Data>";

        final GetTapeLibraryResponse response = MockNetwork
                .expecting(HttpVerb.GET, "/_rest_/tape_library/e23030e5-9b8d-4594-bdd1-15d3c45abb9f", null, null)
                .returning(200, responsePayload)
                .asClient()
                .getTapeLibrary(new GetTapeLibraryRequest(UUID.fromString("e23030e5-9b8d-4594-bdd1-15d3c45abb9f")));

        assertThat(response.getTapeLibrary(), is(notNullValue()));
        assertThat(response.getTapeLibrary().getId().toString(), is("e23030e5-9b8d-4594-bdd1-15d3c45abb9f"));
    }

    @Test
    public void getTapeFailures() throws IOException, SignatureException {
        final String responsePayload = "<Data><TapeFailure><Date>2015-03-11 16:23:29.741</Date><ErrorMessage>AAA</ErrorMessage><Id>375ae624-d39f-47d8-95c0-0aaec4494ad2</Id><TapeDriveId>b06c8900-6d88-4a29-9a03-d0c4494b29ff</TapeDriveId><TapeId>badbb1e7-8654-4b38-8d3b-112c9fd68d58</TapeId><Type>BLOB_READ_FAILED</Type></TapeFailure></Data>";

        final GetTapeFailureResponse response = MockNetwork
                .expecting(HttpVerb.GET, "/_rest_/tape_failure", null, null)
                .returning(200, responsePayload)
                .asClient()
                .getTapeFailure(new GetTapeFailureRequest());

        final List<TapeFailure> tapeFailures = response.getTapeFailures();

        assertThat(tapeFailures, is(notNullValue()));
        assertThat(tapeFailures.size(), is(1));
        assertThat(tapeFailures.get(0).getId().toString(), is("375ae624-d39f-47d8-95c0-0aaec4494ad2"));
    }

    @Test
    public void getTapeDrives() throws IOException, SignatureException {
        final String responsePayload = "<Data><TapeDrive><ErrorMessage/><ForceTapeRemoval>false</ForceTapeRemoval><Id>ebeb0ec7-7912-4870-a0da-bbeb270ac049</Id><PartitionId>aa947aaa-23bf-4301-8173-2553bb1a3f1c</PartitionId><SerialNumber>test tape drive</SerialNumber><State>NORMAL</State><TapeId>b9085cd3-f1fd-4193-8763-c013d25cd135</TapeId><Type>UNKNOWN</Type></TapeDrive><TapeDrive><ErrorMessage/><ForceTapeRemoval>false</ForceTapeRemoval><Id>5dc2add1-b6e7-42a9-b551-a46339176c4b</Id><PartitionId>aa947aaa-23bf-4301-8173-2553bb1a3f1c</PartitionId><SerialNumber>test tape drive 2</SerialNumber><State>NORMAL</State><TapeId/><Type>UNKNOWN</Type></TapeDrive></Data>";

        final GetTapeDrivesResponse response = MockNetwork
                .expecting(HttpVerb.GET, "/_rest_/tape_drive", null, null)
                .returning(200, responsePayload)
                .asClient()
                .getTapeDrives(new GetTapeDrivesRequest());

        final List<TapeDrive> tapeDrives = response.getTapeDrives();

        assertThat(tapeDrives, is(notNullValue()));
        assertThat(tapeDrives.size(), is(2));
        assertThat(tapeDrives.get(0).getId().toString(), is("ebeb0ec7-7912-4870-a0da-bbeb270ac049"));
    }

    @Test
    public void getTapeDrive() throws IOException, SignatureException {
        final String responsePayload = "<Data><ErrorMessage/><ForceTapeRemoval>false</ForceTapeRemoval><Id>ff5df6c8-7e24-4e4f-815d-a8a1a4cddc98</Id><PartitionId>ca69b187-47cf-425e-b92f-c09bacc7d3b3</PartitionId><SerialNumber>test tape drive</SerialNumber><State>NORMAL</State><TapeId>0ea07c32-8ff6-443f-b7c8-420667b0df84</TapeId><Type>UNKNOWN</Type></Data>";

        final GetTapeDriveResponse response = MockNetwork
                .expecting(HttpVerb.GET, "/_rest_/tape_drive/ff5df6c8-7e24-4e4f-815d-a8a1a4cddc98", null, null)
                .returning(200, responsePayload)
                .asClient()
                .getTapeDrive(new GetTapeDriveRequest(UUID.fromString("ff5df6c8-7e24-4e4f-815d-a8a1a4cddc98")));

        final TapeDrive tapeDrive = response.getTapeDrive();

        assertThat(tapeDrive, is(notNullValue()));
        assertThat(tapeDrive.getId().toString(), is("ff5df6c8-7e24-4e4f-815d-a8a1a4cddc98"));
    }

    @Test
    public void getTapes() throws IOException, SignatureException {
        final String responsePayload = "<Data><Tape><AssignedToBucket>false</AssignedToBucket><AvailableRawCapacity>2408082046976</AvailableRawCapacity><BarCode>101000L6</BarCode><BucketId/><DescriptionForIdentification/><EjectDate/><EjectLabel/><EjectLocation/><EjectPending/><FullOfData>false</FullOfData><Id>c7c431df-f95d-4533-b350-ffd7a8a5caac</Id><LastAccessed>2015-09-04 06:53:08.236</LastAccessed><LastCheckpoint>eb77ea67-3c83-47ec-8714-cd46a97dc392:2</LastCheckpoint><LastModified>2015-08-21 16:14:30.714</LastModified><LastVerified/><PartitionId>4f8a5cbb-9837-41d9-afd1-cebed41f18f7</PartitionId><PreviousState/><SerialNumber>HP-W130501213</SerialNumber><State>NORMAL</State><TotalRawCapacity>2408088338432</TotalRawCapacity><Type>LTO6</Type><WriteProtected>false</WriteProtected></Tape></Data>";

        final GetTapesResponse response = MockNetwork
                .expecting(HttpVerb.GET, "/_rest_/tape/", null, null)
                .returning(200, responsePayload)
                .asClient()
                .getTapes(new GetTapesRequest());

        final List<Tape> tapes = response.getTapes().getTapes();

        assertThat(tapes.size(), is(not(0)));
        assertThat(tapes.get(0).getId(), is(notNullValue()));
    }

    @Test
    public void deleteTape() throws IOException, SignatureException {

        final UUID id = UUID.randomUUID();

        final DeleteTapeResponse response = MockNetwork
                .expecting(HttpVerb.DELETE, "/_rest_/tape/" + id.toString(), null, null)
                .returning(204, "")
                .asClient()
                .deleteTape(new DeleteTapeRequest(id));

        assertThat(response, is(notNullValue()));
    }

    @Test
    public void getTape() throws IOException, SignatureException {
        final String responsePayload = "<Data><AssignedToBucket>false</AssignedToBucket><AvailableRawCapacity>2408082046976</AvailableRawCapacity><BarCode>101000L6</BarCode><BucketId/><DescriptionForIdentification/><EjectDate/><EjectLabel/><EjectLocation/><EjectPending/><FullOfData>false</FullOfData><Id>c7c431df-f95d-4533-b350-ffd7a8a5caac</Id><LastAccessed>2015-09-04 06:53:08.236</LastAccessed><LastCheckpoint>eb77ea67-3c83-47ec-8714-cd46a97dc392:2</LastCheckpoint><LastModified>2015-08-21 16:14:30.714</LastModified><LastVerified/><PartitionId>4f8a5cbb-9837-41d9-afd1-cebed41f18f7</PartitionId><PreviousState/><SerialNumber>HP-W130501213</SerialNumber><State>NORMAL</State><TotalRawCapacity>2408088338432</TotalRawCapacity><Type>LTO6</Type><WriteProtected>false</WriteProtected></Data>";

        final GetTapeResponse response = MockNetwork
                .expecting(HttpVerb.GET, "/_rest_/tape/c7c431df-f95d-4533-b350-ffd7a8a5caac", null, null)
                .returning(200, responsePayload)
                .asClient()
                .getTape(new GetTapeRequest(UUID.fromString("c7c431df-f95d-4533-b350-ffd7a8a5caac")));

        final Tape tape = response.getTape();

        assertThat(tape.getId(), is(notNullValue()));
        assertThat(tape.getId(), is(UUID.fromString("c7c431df-f95d-4533-b350-ffd7a8a5caac")));
    }

    @Test(expected = ContentLengthNotMatchException.class)
    public void getObjectVerifyFullPayload() throws IOException, SignatureException {
        final String jobIdString = "a4a586a1-cb80-4441-84e2-48974e982d51";
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("job", jobIdString);
        queryParams.put("offset", Long.toString(0));

        final Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("Content-Length", "4");

        final ByteArraySeekableByteChannel resultChannel = new ByteArraySeekableByteChannel();
        final String stringResponse = "Response";

        MockNetwork
                .expecting(HttpVerb.GET, "/bucketName/object", queryParams, null)
                .returning(200, stringResponse, responseHeaders)
                .asClient()
                .getObject(new GetObjectRequest("bucketName", "object", 0, UUID.fromString(jobIdString), resultChannel));
    }
}
