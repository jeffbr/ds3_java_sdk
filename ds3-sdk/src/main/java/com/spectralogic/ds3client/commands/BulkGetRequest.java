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

package com.spectralogic.ds3client.commands;

import com.spectralogic.ds3client.BulkCommand;
import com.spectralogic.ds3client.models.bulk.ChunkClientProcessingOrderGuarantee;
import com.spectralogic.ds3client.models.bulk.Ds3Object;
import com.spectralogic.ds3client.models.bulk.Priority;
import com.spectralogic.ds3client.models.bulk.WriteOptimization;
import com.spectralogic.ds3client.serializer.XmlProcessingException;

import java.util.List;

public class BulkGetRequest extends BulkRequest {
    public BulkGetRequest(final String bucket, final List<Ds3Object> objects) throws XmlProcessingException {
        super(bucket, objects);
        getQueryParams().put("operation", "start_bulk_get");
    }

    @Override
    public BulkGetRequest withPriority(final Priority priority) {
        super.withPriority(priority);
        return this;
    }

    @Override
    public BulkGetRequest withWriteOptimization(final WriteOptimization writeOptimization) {
        super.withWriteOptimization(writeOptimization);
        return this;
    }
    
    public BulkGetRequest withChunkOrdering(final ChunkClientProcessingOrderGuarantee chunkOrdering) {
        this.chunkOrdering = chunkOrdering;
        return this;
    }
    
    public ChunkClientProcessingOrderGuarantee getChunkOrdering() {
        return this.chunkOrdering;
    }

    @Override
    public BulkCommand getCommand() {
        return BulkCommand.GET;
    }
}
