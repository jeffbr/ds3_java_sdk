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

public class GetJobsRequest extends AbstractRequest {

    private boolean fullDetails;

    public GetJobsRequest() {}

    public GetJobsRequest withFullDetails(final boolean fullDetails) {
        this.fullDetails = fullDetails;
        if(this.fullDetails) {
            this.getQueryParams().put("full_details", null);
        } else {
            this.getQueryParams().remove("full_details");
        }
        return this;
    }

    public boolean isFullDetails() { return this.fullDetails; }

    @Override
    public String getPath() {
        return "/_rest_/job";
    }

    @Override
    public HttpVerb getVerb() {
        return HttpVerb.GET;
    }
}
