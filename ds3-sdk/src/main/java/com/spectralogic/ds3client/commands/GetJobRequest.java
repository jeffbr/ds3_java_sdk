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

import com.spectralogic.ds3client.HttpVerb;

import java.util.UUID;

public class GetJobRequest extends AbstractRequest {
    private final UUID jobId;

    public GetJobRequest(final UUID jobId) {
        this.jobId = jobId;
    }
    
    public UUID getJobId() {
        return this.jobId;
    }

    @Override
    public String getPath() {
        return "/_rest_/job/" + this.jobId.toString();
    }

    @Override
    public HttpVerb getVerb() {
        return HttpVerb.GET;
    }

    @Override
    public int hashCode() {
        return jobId.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (! (obj instanceof GetJobRequest)) return false;
        final GetJobRequest other = (GetJobRequest) obj;
        return other.getJobId().equals(this.getJobId());
    }
}
