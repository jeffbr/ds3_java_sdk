/*
 * ******************************************************************************
 *   Copyright 2014 Spectra Logic Corporation. All Rights Reserved.
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

import com.spectralogic.ds3client.Ds3Client;
import com.spectralogic.ds3client.commands.GetObjectRequest;
import com.spectralogic.ds3client.commands.PutObjectRequest;
import com.spectralogic.ds3client.models.Contents;
import com.spectralogic.ds3client.models.bulk.Ds3Object;
import com.spectralogic.ds3client.serializer.XmlProcessingException;
import com.spectralogic.ds3client.utils.Md5Hash;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.SignatureException;
import java.util.UUID;

/**
 * A wrapper around the {@link com.spectralogic.ds3client.Ds3Client} which automates common tasks.
 */
public abstract class Ds3ClientHelpers {

    public interface ObjectGetter {
        /**
         * Must save the {@code contents} for the given {@code key}.
         *
         * @param md5 The MD5 hash of the contents.  This is only set if the DS3 Endpoint returned a 'Content-MD5' as
         *            a part of the response.
         * @throws IOException
         */
        public void writeContents(String key, InputStream contents, final Md5Hash md5) throws IOException;
    }
    
    public interface ObjectPutter {
        /**
         * Must return the contents to send over DS3 for the given {@code key}.
         * 
         * @throws IOException
         */
        public InputStream getContent(String key) throws IOException;
    }
    
    public interface GetRequestModifier {
        /**
         * Modifies a get request before it's sent to the server.
         */
        public void modify(GetObjectRequest request);
    }
    
    public interface PutRequestModifier {
        /**
         * Modifies a put request before it's sent to the server.
         */
        public void modify(PutObjectRequest request);
    }
    
    /**
     * Represents a bulk job operation.
     * When you call one of the start* methods it's recommended that you save the
     * JobId so the job can be recovered in the event of a failure.
     */
    public interface Job {
        public UUID getJobId();
        public String getBucketName();
    }
    
    public interface WriteJob extends Job {
        /**
         * Calls the given {@code putter} for each object in the job remaining to be written.
         * Note that it's possible for the {@code putter} to be called simultaneously from multiple threads.
         * 
         * @throws SignatureException
         * @throws IOException
         * @throws XmlProcessingException
         */
        public void write(ObjectPutter putter) throws SignatureException, IOException, XmlProcessingException;
        
        /**
         * Sets an object that knows how to modify put requests before they're executed.
         * Note that it's possible for the {@code modifier} to be called simultaneously from multiple threads.
         */
        public WriteJob withRequestModifier(PutRequestModifier modifier);

        /**
         * Sets the maximum number of requests to execute at a time when fulfilling the job.
         */
        public WriteJob withMaxParallelRequests(int maxParallelRequests);
    }
    
    public interface ReadJob extends Job {
        /**
         * Calls the given {@code getter} for each object in the job remaining to be read.
         * Note that it's possible for the {@code getter} to be called simultaneously from multiple threads.
         * 
         * @throws SignatureException
         * @throws IOException
         * @throws XmlProcessingException
         */
        public void read(ObjectGetter getter) throws SignatureException, IOException, XmlProcessingException;

        /**
         * Sets an object that knows how to modify get requests before they're executed.
         * Note that it's possible for the {@code modifier} to be called simultaneously from multiple threads.
         */
        public ReadJob withRequestModifier(GetRequestModifier modifier);
        
        /**
         * Sets the maximum number of requests to execute at a time when fulfilling the job.
         */
        public ReadJob withMaxParallelRequests(int maxParallelRequests);
    }

    /**
     * Wraps the given {@link com.spectralogic.ds3client.Ds3ClientImpl} with helper methods.
     */
    public static Ds3ClientHelpers wrap(final Ds3Client client) {
        return new Ds3ClientHelpersImpl(client);
    }

    /**
     * Performs a bulk put job creation request and returns an {@link WriteJob}.
     * See {@link WriteJob} for information on how to write the objects for the job.
     *
     * @throws SignatureException
     * @throws IOException
     * @throws XmlProcessingException
     */
    public abstract Ds3ClientHelpers.WriteJob startWriteJob(final String bucket, final Iterable<Ds3Object> objectsToWrite)
            throws SignatureException, IOException, XmlProcessingException;

    /**
     * Performs a bulk get job creation request and returns an {@link ReadJob}.
     * See {@link ReadJob} for information on how to read the objects for the job.
     *
     * @throws SignatureException
     * @throws IOException
     * @throws XmlProcessingException
     */
    public abstract Ds3ClientHelpers.ReadJob startReadJob(final String bucket, final Iterable<Ds3Object> objectsToRead)
            throws SignatureException, IOException, XmlProcessingException;

    /**
     * Performs a bulk get job creation request for all of the objects in the given bucket and returns an {@link ReadJob}.
     *
     * @throws SignatureException
     * @throws IOException
     * @throws XmlProcessingException
     */
    public abstract Ds3ClientHelpers.ReadJob startReadAllJob(final String bucket)
            throws SignatureException, IOException, XmlProcessingException;

    /**
     * Queries job information based on job id and returns a {@link ReadJob} that can resume the job.
     * @throws SignatureException
     * @throws IOException
     * @throws XmlProcessingException
     * @throws JobRecoveryException
     */
    public abstract Ds3ClientHelpers.WriteJob recoverWriteJob(final UUID jobId)
            throws SignatureException, IOException, XmlProcessingException, JobRecoveryException;

    /**
     * Queries job information based on job id and returns a {@link WriteJob} that can resume the job.
     * @throws SignatureException
     * @throws IOException
     * @throws XmlProcessingException
     * @throws JobRecoveryException
     */
    public abstract Ds3ClientHelpers.ReadJob recoverReadJob(final UUID jobId)
            throws SignatureException, IOException, XmlProcessingException, JobRecoveryException;

    /**
     * Ensures that a bucket exists.  The the bucket does not exist, it will be created.
     * @param bucket The name of the bucket to check that it exists.
     * @throws IOException
     * @throws SignatureException
     */
    public abstract void ensureBucketExists(final String bucket) throws IOException, SignatureException;

    /**
     * Returns information about all of the objects in the bucket, regardless of how many objects the bucket contains.
     *
     * @throws SignatureException
     * @throws IOException
     */
    public abstract Iterable<Contents> listObjects(final String bucket) throws SignatureException, IOException;

    /**
     * Returns information about all of the objects in the bucket, regardless of how many objects the bucket contains.
     *
     * @throws SignatureException
     * @throws IOException
     */
    public abstract Iterable<Contents> listObjects(final String bucket, final String keyPrefix)
            throws SignatureException, IOException;

    /**
     * Returns information about all of the objects in the bucket, regardless of how many objects the bucket contains.
     *
     * @throws SignatureException
     * @throws IOException
     */
    public abstract Iterable<Contents> listObjects(final String bucket, final String keyPrefix, final int maxKeys)
            throws SignatureException, IOException;

    /**
     * Returns an object list with which you can call {@code startWriteJob} based on the files in a {@code directory}.
     * This method traverses the {@code directory} recursively.
     *
     * @throws IOException
     */
    public abstract Iterable<Ds3Object> listObjectsForDirectory(final Path directory) throws IOException;
}

