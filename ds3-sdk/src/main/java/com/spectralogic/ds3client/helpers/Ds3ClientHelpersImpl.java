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

package com.spectralogic.ds3client.helpers;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Lists;
import com.spectralogic.ds3client.Ds3Client;
import com.spectralogic.ds3client.commands.*;
import com.spectralogic.ds3client.helpers.options.ReadJobOptions;
import com.spectralogic.ds3client.helpers.options.WriteJobOptions;
import com.spectralogic.ds3client.helpers.util.PartialObjectHelpers;
import com.spectralogic.ds3client.models.Checksum;
import com.spectralogic.ds3client.models.Contents;
import com.spectralogic.ds3client.models.ListBucketResult;
import com.spectralogic.ds3client.models.Range;
import com.spectralogic.ds3client.models.bulk.*;
import com.spectralogic.ds3client.networking.FailedRequestException;
import com.spectralogic.ds3client.serializer.XmlProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class Ds3ClientHelpersImpl extends Ds3ClientHelpers {

    private final static Logger LOG = LoggerFactory.getLogger(Ds3ClientHelpersImpl.class);

    private static final int DEFAULT_MAX_KEYS = 1000;
    private final Ds3Client client;
    private final int retryAfter;

    public Ds3ClientHelpersImpl(final Ds3Client client) {
        this(client, -1);
    }

    public Ds3ClientHelpersImpl(final Ds3Client client, final int retryAfter) {
        this.client = client;
        this.retryAfter = retryAfter;
    }

    @Override
    public Ds3ClientHelpers.Job startWriteJob(final String bucket, final Iterable<Ds3Object> objectsToWrite)
            throws SignatureException, IOException, XmlProcessingException {
        return innerStartWriteJob(bucket, objectsToWrite, WriteJobOptions.create());
    }

    @Override
    public Ds3ClientHelpers.Job startWriteJob(final String bucket,
                                                   final Iterable<Ds3Object> objectsToWrite,
                                                   final WriteJobOptions options)
            throws SignatureException, IOException, XmlProcessingException {
        if (options == null) {
            return innerStartWriteJob(bucket, objectsToWrite, WriteJobOptions.create());
        }
        return innerStartWriteJob(bucket, objectsToWrite, options);
    }

    private Ds3ClientHelpers.Job innerStartWriteJob(final String bucket,
                                                         final Iterable<Ds3Object> objectsToWrite,
                                                         final WriteJobOptions options)
            throws SignatureException, IOException, XmlProcessingException {
        final BulkPutResponse prime = this.client.bulkPut(new BulkPutRequest(bucket, Lists.newArrayList(objectsToWrite))
                .withPriority(options.getPriority())
                .withWriteOptimization(options.getWriteOptimization())
                .withMaxUploadSize(options.getMaxUploadSize()));
        return new WriteJobImpl(this.client, prime.getResult(), this.retryAfter, options.getChecksumType());
    }

    @Override
    public Ds3ClientHelpers.Job startReadJob(final String bucket, final Iterable<Ds3Object> objectsToRead)
            throws SignatureException, IOException, XmlProcessingException {
        return innerStartReadJob(bucket, objectsToRead, ReadJobOptions.create());
    }

    @Override
    public Ds3ClientHelpers.Job startReadJob(final String bucket, final Iterable<Ds3Object> objectsToRead, final ReadJobOptions options)
            throws SignatureException, IOException, XmlProcessingException {
        if (options == null) {
            return innerStartReadJob(bucket, objectsToRead, ReadJobOptions.create());
        }
        return innerStartReadJob(bucket, objectsToRead, options);
    }

    private Ds3ClientHelpers.Job innerStartReadJob(final String bucket, final Iterable<Ds3Object> objectsToRead, final ReadJobOptions options)
            throws SignatureException, IOException, XmlProcessingException {
        final List<Ds3Object> objects = Lists.newArrayList(objectsToRead);
        final BulkGetResponse prime = this.client.bulkGet(new BulkGetRequest(bucket, objects)
                .withChunkOrdering(ChunkClientProcessingOrderGuarantee.NONE)
                .withPriority(options.getPriority()));

        final ImmutableMultimap<String, Range> partialRanges = PartialObjectHelpers.getPartialObjectsRanges(objects);

        return new ReadJobImpl(this.client, prime.getResult(), partialRanges, this.retryAfter);
    }

    @Override
    public Ds3ClientHelpers.Job startReadAllJob(final String bucket)
            throws SignatureException, IOException, XmlProcessingException {
        return innerStartReadAllJob(bucket, ReadJobOptions.create());
    }

    @Override
    public Ds3ClientHelpers.Job startReadAllJob(final String bucket, final ReadJobOptions options)
            throws SignatureException, IOException, XmlProcessingException {
        if (options == null) {
            return innerStartReadAllJob(bucket, ReadJobOptions.create());
        }
        return innerStartReadAllJob(bucket, options);
    }

    private Ds3ClientHelpers.Job innerStartReadAllJob(final String bucket, final ReadJobOptions options)
            throws SignatureException, IOException, XmlProcessingException {
        final Iterable<Contents> contentsList = this.listObjects(bucket);

        final List<Ds3Object> ds3Objects = new ArrayList<>();
        for (final Contents contents : contentsList) {
            ds3Objects.add(new Ds3Object(contents.getKey()));
        }

        return this.startReadJob(bucket, ds3Objects, options);
    }

    @Override
    public Ds3ClientHelpers.Job recoverWriteJob(final UUID jobId) throws SignatureException, IOException, XmlProcessingException, JobRecoveryException {
        final ModifyJobResponse jobResponse = this.client.modifyJob(new ModifyJobRequest(jobId));
        if (RequestType.PUT != jobResponse.getMasterObjectList().getRequestType()){
            throw new JobRecoveryException(RequestType.PUT.toString(), jobResponse.getMasterObjectList().getRequestType().toString() );
        }
        // TODO Need to allow the user to pass in the checksumming information again
        return new WriteJobImpl(this.client, jobResponse.getMasterObjectList(), this.retryAfter, Checksum.Type.NONE);
    }

    @Override
    //TODO add a partial object read recovery method.  That method will require the list of partial objects.
    public Ds3ClientHelpers.Job recoverReadJob(final UUID jobId) throws SignatureException, IOException, XmlProcessingException, JobRecoveryException {
        final ModifyJobResponse jobResponse = this.client.modifyJob(new ModifyJobRequest(jobId));
        if (RequestType.GET != jobResponse.getMasterObjectList().getRequestType()){
            throw new JobRecoveryException(RequestType.GET.toString(), jobResponse.getMasterObjectList().getRequestType().toString() );
        }

        return new ReadJobImpl(this.client, jobResponse.getMasterObjectList(), ImmutableMultimap.<String, Range>of(), this.retryAfter);
    }

    @Override
    public void ensureBucketExists(final String bucket) throws IOException, SignatureException {
        final HeadBucketResponse response = this.client.headBucket(new HeadBucketRequest(bucket));
        if (response.getStatus() == HeadBucketResponse.Status.DOESNTEXIST) {
            try {
                this.client.putBucket(new PutBucketRequest(bucket));
            } catch (final FailedRequestException e) {
                if (e.getStatusCode() != 409) {
                    throw e;
                }
                LOG.warn("Creating " + bucket + " failed because it was created by another thread or process");
            }
        }
    }

    @Override
    public Iterable<Contents> listObjects(final String bucket) throws SignatureException, IOException {
        return this.listObjects(bucket, null);
    }

    @Override
    public Iterable<Contents> listObjects(final String bucket, final String keyPrefix) throws SignatureException, IOException {
        return this.listObjects(bucket, keyPrefix, null, Integer.MAX_VALUE);
    }

    @Override
    public Iterable<Contents> listObjects(final String bucket, final String keyPrefix, final String nextMarker) throws SignatureException, IOException {
        return this.listObjects(bucket, keyPrefix, nextMarker, Integer.MAX_VALUE);
    }

    @Override
    public Iterable<Contents> listObjects(final String bucket, final String keyPrefix, final String nextMarker, final int maxKeys) throws SignatureException, IOException {
        final List<Contents> contentList = new ArrayList<>();

        int remainingKeys = maxKeys;
        boolean isTruncated = false;
        String marker = nextMarker;
        if (nextMarker != null) isTruncated = true;

        do {
            final GetBucketRequest request = new GetBucketRequest(bucket);
            request.withMaxKeys(Math.min(remainingKeys, DEFAULT_MAX_KEYS));
            if (keyPrefix != null) {
                request.withPrefix(keyPrefix);
            }
            if (isTruncated) {
                request.withNextMarker(marker);
            }

            final GetBucketResponse response = this.client.getBucket(request);
            final ListBucketResult result = response.getResult();

            isTruncated = result.isTruncated();
            marker = result.getNextMarker();
            remainingKeys -= result.getContentsList().size();

            for (final Contents contents : result.getContentsList()) {
                contentList.add(contents);
            }
        } while (isTruncated && remainingKeys > 0);

        return contentList;
    }

    @Override
    public Iterable<Ds3Object> listObjectsForDirectory(final Path directory) throws IOException {
        final List<Ds3Object> objects = new ArrayList<>();
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                objects.add(new Ds3Object(directory.relativize(file).toString().replace("\\", "/"), Files.size(file)));
                return FileVisitResult.CONTINUE;
            }
        });
        return objects;
    }

    public Iterable<Ds3Object> addPrefixToDs3ObjectsList(final Iterable<Ds3Object> objectsList, final String prefix) {
        final List<Ds3Object> newObjectsList = new ArrayList<>();
        for (final Ds3Object object: objectsList) {
            final Ds3Object tmpObj = new Ds3Object( prefix + object.getName(), object.getSize());
            newObjectsList.add(tmpObj);
        }
        return newObjectsList;
    }

    public Iterable<Ds3Object> removePrefixFromDs3ObjectsList(final Iterable<Ds3Object> objectsList, final String prefix) {
        final List<Ds3Object> newObjectsList = new ArrayList<>();
        for (final Ds3Object object: objectsList) {
            newObjectsList.add(new Ds3Object(stripLeadingPath(object.getName(), prefix), object.getSize()));
        }
        return newObjectsList;
    }
}
