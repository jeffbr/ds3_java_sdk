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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.spectralogic.ds3client.Ds3Client;
import com.spectralogic.ds3client.commands.AllocateJobChunkRequest;
import com.spectralogic.ds3client.commands.AllocateJobChunkResponse;
import com.spectralogic.ds3client.commands.PutObjectRequest;
import com.spectralogic.ds3client.exceptions.Ds3NoMoreRetriesException;
import com.spectralogic.ds3client.helpers.ChunkTransferrer.ItemTransferrer;
import com.spectralogic.ds3client.helpers.Ds3ClientHelpers.ObjectChannelBuilder;
import com.spectralogic.ds3client.models.Checksum;
import com.spectralogic.ds3client.models.Range;
import com.spectralogic.ds3client.models.bulk.BulkObject;
import com.spectralogic.ds3client.models.bulk.MasterObjectList;
import com.spectralogic.ds3client.models.bulk.Objects;
import com.spectralogic.ds3client.serializer.XmlProcessingException;
import com.spectralogic.ds3client.utils.Guard;
import com.spectralogic.ds3client.utils.SeekableByteChannelInputStream;
import com.spectralogic.ds3client.utils.hashing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.security.SignatureException;
import java.util.*;

class WriteJobImpl extends JobImpl {
    static private final Logger LOG = LoggerFactory.getLogger(WriteJobImpl.class);
    private final JobPartTracker partTracker;
    private final List<Objects> filteredChunks;
    private final int retryAfter; // Negative retryAfter value represent infinity retries
    private final Checksum.Type checksumType;
    private final Map<ChecksumListener, ChecksumListener> checksumListeners;
    private int retryAfterLeft; // The number of retries left
    private Ds3ClientHelpers.MetadataAccess metadataAccess = null;
    private ChecksumFunction checksumFunction = null;

    public WriteJobImpl(final Ds3Client client, final MasterObjectList masterObjectList, final int retryAfter, final Checksum.Type type) {
        super(client, masterObjectList);
        if (this.masterObjectList == null || this.masterObjectList.getObjects() == null) {
            LOG.info("Job has no data to transfer");
            this.filteredChunks = null;
            this.partTracker = null;
        } else {
            LOG.info("Ready to start transfer for job " + masterObjectList.getJobId().toString() + " with " + masterObjectList.getObjects().size() + " chunks");
            this.filteredChunks = filterChunks(this.masterObjectList.getObjects());
            this.partTracker = JobPartTrackerFactory
                    .buildPartTracker(Iterables.concat(filteredChunks));
        }
        this.retryAfter = this.retryAfterLeft = retryAfter;
        this.checksumListeners = new IdentityHashMap<>();
        this.checksumType = type;
    }

    @Override
    public void attachDataTransferredListener(final DataTransferredListener listener) {
        checkRunning();
        this.partTracker.attachDataTransferredListener(listener);
    }

    @Override
    public void attachObjectCompletedListener(final ObjectCompletedListener listener) {
        checkRunning();
        this.partTracker.attachObjectCompletedListener(listener);
    }

    @Override
    public void removeDataTransferredListener(final DataTransferredListener listener) {
        checkRunning();
        this.partTracker.removeDataTransferredListener(listener);
    }

    @Override
    public void removeObjectCompletedListener(final ObjectCompletedListener listener) {
        checkRunning();
        this.partTracker.removeObjectCompletedListener(listener);
    }

    @Override
    public void attachMetadataReceivedListener(final MetadataReceivedListener listener) {
        throw new IllegalStateException("Metadata listeners are not used with Write jobs");
    }

    @Override
    public void removeMetadataReceivedListener(final MetadataReceivedListener listener) {
        throw new IllegalStateException("Metadata listeners are not used with Write jobs");
    }

    @Override
    public void attachChecksumListener(final ChecksumListener listener) {
        checkRunning();
        this.checksumListeners.put(listener, listener);
    }

    @Override
    public void removeChecksumListener(final ChecksumListener listener) {
        checkRunning();
        this.checksumListeners.remove(listener);
    }

    @Override
    public Ds3ClientHelpers.Job withMetadata(final Ds3ClientHelpers.MetadataAccess access) {
        checkRunning();
        this.metadataAccess = access;
        return this;
    }

    @Override
    public Ds3ClientHelpers.Job withChecksum(final ChecksumFunction checksumFunction) {
        this.checksumFunction = checksumFunction;
        return this;
    }

    @Override
    public void transfer(final ObjectChannelBuilder channelBuilder)
            throws SignatureException, IOException, XmlProcessingException {
        running = true;
        LOG.debug("Starting job transfer");
        if (this.masterObjectList == null || this.masterObjectList.getObjects() == null) {
            LOG.info("There is nothing to transfer for job" + ((this.getJobId() == null) ? "" : " " + this.getJobId().toString()));
            return;
        }
        try (final JobState jobState = new JobState(channelBuilder, filteredChunks, partTracker, ImmutableMap.<String, ImmutableMultimap<BulkObject,Range>>of())) {
            final ChunkTransferrer chunkTransferrer = new ChunkTransferrer(
                new PutObjectTransferrer(jobState),
                this.client,
                jobState.getPartTracker(),
                this.maxParallelRequests
            );
            for (final Objects chunk : filteredChunks) {
                LOG.debug("Allocating chunk: " + chunk.getChunkId().toString());
                chunkTransferrer.transferChunks(this.masterObjectList.getNodes(), Collections.singletonList(filterChunk(allocateChunk(chunk))));
            }
        } catch (final SignatureException | IOException | XmlProcessingException | RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Objects allocateChunk(final Objects filtered) throws IOException, SignatureException {
        Objects chunk = null;
        while (chunk == null) {
            chunk = tryAllocateChunk(filtered);
        }
        return chunk;
    }

    private Objects tryAllocateChunk(final Objects filtered) throws IOException, SignatureException {
        final AllocateJobChunkResponse response =
                this.client.allocateJobChunk(new AllocateJobChunkRequest(filtered.getChunkId()));

        LOG.info("AllocatedJobChunkResponse status: " + response.getStatus().toString());
        switch (response.getStatus()) {
        case ALLOCATED:
            retryAfterLeft = retryAfter; // Reset the number of retries to the initial value
            return response.getObjects();
        case RETRYLATER:
            try {
                if (retryAfterLeft == 0) {
                    throw new Ds3NoMoreRetriesException(this.retryAfter);
                }
                retryAfterLeft--;

                final int retryAfter = response.getRetryAfterSeconds() * 1000;
                LOG.debug("Will retry allocate chunk call after " + retryAfter + " seconds");
                Thread.sleep(retryAfter);
                return null;
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
        default:
            assert false : "This line of code should be impossible to hit."; return null;
        }
    }

    /**
     * Filters out chunks that have already been completed.  We will get the same chunk name back from the server, but it
     * will not have any objects in it, so we remove that from the list of objects that are returned.
     * @param chunks The list to be filtered
     * @return The filtered list
     */
    private static List<Objects> filterChunks(final List<Objects> chunks) {
        final List<Objects> filteredChunks = new ArrayList<>();
        for (final Objects chunk : chunks) {
            final Objects filteredChunk = filterChunk(chunk);
            if (filteredChunk.getObjects().size() > 0) {
                filteredChunks.add(filteredChunk);
            }
        }
        return filteredChunks;
    }

    private static Objects filterChunk(final Objects chunk) {
        final Objects newChunk = new Objects();
        newChunk.setChunkId(chunk.getChunkId());
        newChunk.setChunkNumber(chunk.getChunkNumber());
        newChunk.setNodeId(chunk.getNodeId());
        newChunk.setObjects(filterObjects(chunk.getObjects()));
        return newChunk;
    }

    private static List<BulkObject> filterObjects(final List<BulkObject> list) {
        final List<BulkObject> filtered = new ArrayList<>();
        for (final BulkObject obj : list) {
            if (!obj.isInCache()) {
                filtered.add(obj);
            }
        }
        return filtered;
    }

    private final class PutObjectTransferrer implements ItemTransferrer {
        private final JobState jobState;

        private PutObjectTransferrer(final JobState jobState) {
            this.jobState = jobState;
        }

        @Override
        public void transferItem(final Ds3Client client, final BulkObject ds3Object)
                throws SignatureException, IOException {
            client.putObject(createRequest(ds3Object));
        }

        private PutObjectRequest createRequest(final BulkObject ds3Object) throws IOException {
            final SeekableByteChannel channel = jobState.getChannel(ds3Object.getName(), ds3Object.getOffset(), ds3Object.getLength());

            final PutObjectRequest request = new PutObjectRequest(
                    WriteJobImpl.this.masterObjectList.getBucketName(),
                    ds3Object.getName(),
                    WriteJobImpl.this.getJobId(),
                    ds3Object.getLength(),
                    ds3Object.getOffset(),
                    channel
            );

            if (ds3Object.getOffset() == 0 && metadataAccess != null) {
                final Map<String, String> metadata = metadataAccess.getMetadataValue(ds3Object.getName());
                if (Guard.isMapNullOrEmpty(metadata)) return request;
                final ImmutableMap<String, String> immutableMetadata = ImmutableMap.copyOf(metadata);
                for (final Map.Entry<String, String> value : immutableMetadata.entrySet()) {
                    request.withMetaData(value.getKey(), value.getValue());
                }
            }

            final String checksum = calculateChecksum(ds3Object, channel);
            if (checksum != null) {
                request.withChecksum(Checksum.value(checksum), WriteJobImpl.this.checksumType);
                emitChecksumEvents(ds3Object, WriteJobImpl.this.checksumType, checksum);
            }

            return request;
        }

        private String calculateChecksum(final BulkObject ds3Object, final SeekableByteChannel channel) throws IOException {
            if (WriteJobImpl.this.checksumType != Checksum.Type.NONE) {
                if (WriteJobImpl.this.checksumFunction == null) {
                    LOG.info("Calculating " + WriteJobImpl.this.checksumType.toString() + " checksum for blob: " + ds3Object.toString());
                    final SeekableByteChannelInputStream dataStream = new SeekableByteChannelInputStream(channel);
                    final Hasher hasher = getHasher(WriteJobImpl.this.checksumType);
                    final String checksum = hashInputStream(hasher, dataStream);
                    LOG.info("Computed checksum for blob: " + checksum);
                    return checksum;
                } else {
                    LOG.info("Getting checksum from user supplied callback for blob: " + ds3Object.toString());
                    final String checksum = WriteJobImpl.this.checksumFunction.compute(ds3Object, channel);
                    LOG.info("User supplied checksum is: " + checksum);
                    return checksum;
                }
            }
            return null;
        }

        private static final int READ_BUFFER_SIZE = 10 * 1024 * 1024;
        private String hashInputStream(final Hasher digest, final InputStream stream) throws IOException {
            final byte[] buffer = new byte[READ_BUFFER_SIZE];
            int bytesRead;

            while (true) {
                bytesRead = stream.read(buffer);

                if (bytesRead < 0) {
                    break;
                }

                digest.update(buffer, 0, bytesRead);
            }

            return digest.digest();
        }

        private Hasher getHasher(final Checksum.Type checksumType) {
            switch (checksumType) {
                case MD5: return new MD5Hasher();
                case SHA256: return new SHA256Hasher();
                case SHA512: return new SHA512Hasher();
                case CRC32: return new CRC32Hasher();
                case CRC32C: return new CRC32CHasher();
                default: throw new RuntimeException("Unknown checksum type " + checksumType.toString());
            }
        }
    }

    private void emitChecksumEvents(final BulkObject bulkObject, final Checksum.Type type, final String checksum) {
        for (final ChecksumListener listener : checksumListeners.values()) {
            listener.value(bulkObject, type, checksum);
        }
    }
}
