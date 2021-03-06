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

import com.spectralogic.ds3client.commands.*;
import com.spectralogic.ds3client.models.bulk.*;
import com.spectralogic.ds3client.utils.ByteArraySeekableByteChannel;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

public class ResponseBuilders {
    public static BulkGetResponse bulkGetResponse(final MasterObjectList masterObjectList) {
        final BulkGetResponse response = mock(BulkGetResponse.class);
        when(response.getResult()).thenReturn(masterObjectList);
        return response;
    }

    public static BulkPutResponse bulkPutResponse(final MasterObjectList masterObjectList) {
        final BulkPutResponse response = mock(BulkPutResponse.class);
        when(response.getResult()).thenReturn(masterObjectList);
        return response;
    }

    public static ModifyJobResponse modifyJobResponse(final MasterObjectList masterObjectList) {
        final ModifyJobResponse response = mock(ModifyJobResponse.class);
        when(response.getMasterObjectList()).thenReturn(masterObjectList);
        return response;
    }

    public static GetAvailableJobChunksResponse availableJobChunks(final MasterObjectList masterObjectList) {
        final GetAvailableJobChunksResponse response = mock(GetAvailableJobChunksResponse.class);
        when(response.getStatus()).thenReturn(GetAvailableJobChunksResponse.Status.AVAILABLE);
        when(response.getMasterObjectList()).thenReturn(masterObjectList);
        return response;
    }
    
    public static GetAvailableJobChunksResponse retryGetAvailableAfter(final int retryAfterInSeconds) {
        final GetAvailableJobChunksResponse response = mock(GetAvailableJobChunksResponse.class);
        when(response.getStatus()).thenReturn(GetAvailableJobChunksResponse.Status.RETRYLATER);
        when(response.getRetryAfterSeconds()).thenReturn(retryAfterInSeconds);
        return response;
    }
    
    public static AllocateJobChunkResponse allocated(final Objects chunk) {
        final AllocateJobChunkResponse response = mock(AllocateJobChunkResponse.class);
        when(response.getStatus()).thenReturn(AllocateJobChunkResponse.Status.ALLOCATED);
        when(response.getObjects()).thenReturn(chunk);
        return response;
    }
    
    public static AllocateJobChunkResponse retryAllocateLater(final int retryAfterInSeconds) {
        final AllocateJobChunkResponse response = mock(AllocateJobChunkResponse.class);
        when(response.getStatus()).thenReturn(AllocateJobChunkResponse.Status.RETRYLATER);
        when(response.getRetryAfterSeconds()).thenReturn(retryAfterInSeconds);
        return response;
    }
    
    public static MasterObjectList jobResponse(
            final UUID jobId,
            final String bucketName,
            final RequestType requestType,
            final long originalSizeInBytes,
            final long cachedSizeInBytes,
            final long completedSizeInBytes,
            final ChunkClientProcessingOrderGuarantee chunkClientProcessingOrderGuarantee,
            final Priority priority,
            final String startDate,
            final UUID userId,
            final String userName,
            final WriteOptimization writeOptimization,
            final List<Node> nodes,
            final List<Objects> objects) {
        final MasterObjectList masterObjectList = new MasterObjectList();
        masterObjectList.setJobId(jobId);
        masterObjectList.setBucketName(bucketName);
        masterObjectList.setRequestType(requestType);
        masterObjectList.setOriginalSizeInBytes(originalSizeInBytes);
        masterObjectList.setCachedSizeInBytes(cachedSizeInBytes);
        masterObjectList.setCompletedSizeInBytes(completedSizeInBytes);
        masterObjectList.setChunkClientProcessingOrderGuarantee(chunkClientProcessingOrderGuarantee);
        masterObjectList.setPriority(priority);
        masterObjectList.setStartDate(startDate);
        masterObjectList.setUserId(userId);
        masterObjectList.setUserName(userName);
        masterObjectList.setWriteOptimization(writeOptimization);
        masterObjectList.setNodes(nodes);
        masterObjectList.setObjects(objects);
        return masterObjectList;
    }
    
    public static Node basicNode(final UUID nodeId, final String endpoint) {
        return node(nodeId, endpoint, 80, 443);
    }

    public static Node node(
            final UUID nodeId,
            final String endpoint,
            final int httpPort,
            final int httpsPort) {
        final Node node = new Node();
        node.setId(nodeId);
        node.setEndpoint(endpoint);
        node.setHttpPort(httpPort);
        node.setHttpsPort(httpsPort);
        return node;
    }

    public static Objects chunk(
            final long chunkNumber,
            final UUID chunkId,
            final UUID nodeId,
            final BulkObject ... chunkList) {
        final Objects objects = new Objects();
        objects.setChunkNumber(chunkNumber);
        objects.setChunkId(chunkId);
        objects.setNodeId(nodeId);
        objects.setObjects(Arrays.asList(chunkList));
        return objects;
    }
    
    public static BulkObject object(
            final String name,
            final long offset,
            final long length,
            final boolean inCache) {
        final BulkObject bulkObject = new BulkObject();
        bulkObject.setName(name);
        bulkObject.setOffset(offset);
        bulkObject.setLength(length);
        bulkObject.setInCache(inCache);
        return bulkObject;
    }
    
    public static Answer<GetObjectResponse> getObjectAnswer(final String result) {
        return new Answer<GetObjectResponse>() {
            @Override
            public GetObjectResponse answer(final InvocationOnMock invocation) throws Throwable {
                writeToChannel(result, ((GetObjectRequest)invocation.getArguments()[0]).getDestinationChannel());
                return mock(GetObjectResponse.class);
            }
        };
    }
    
    public static SeekableByteChannel channelWithContents(final String contents) {
        return writeToChannel(contents, new ByteArraySeekableByteChannel());
    }

    private static <T extends WritableByteChannel> T writeToChannel(final String contents, final T channel) {
        final Writer writer = Channels.newWriter(channel, "UTF-8");
        try {
            writer.write(contents);
            writer.flush();
        } catch (final IOException e) {
            throw new RuntimeException();
        }
        return channel;
    }

    public static HeadBucketResponse headBucket(final HeadBucketResponse.Status status) {
        final HeadBucketResponse response = mock(HeadBucketResponse.class);
        when(response.getStatus()).thenReturn(status);
        return response;
    }
}
