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

import com.spectralogic.ds3client.models.bulk.MasterObjectList;
import com.spectralogic.ds3client.networking.WebResponse;
import com.spectralogic.ds3client.serializer.XmlOutput;

import java.io.IOException;
import java.io.InputStream;

public class GetAvailableJobChunksResponse extends AbstractResponse {
    private Status status;
    private MasterObjectList masterObjectList;
    private int retryAfterSeconds;

    public enum Status {
        AVAILABLE, RETRYLATER
    }
    
    public Status getStatus() {
        return this.status;
    }

    public MasterObjectList getMasterObjectList() {
        return this.masterObjectList;
    }
    
    public int getRetryAfterSeconds() {
        return this.retryAfterSeconds;
    }

    public GetAvailableJobChunksResponse(final WebResponse response) throws IOException {
        super(response);
    }

    @Override
    protected void processResponse() throws IOException {
        try (final WebResponse webResponse = this.getResponse()) {
            this.checkStatusCode(200);
            switch (this.getStatusCode()) {
            case 200:
                this.masterObjectList = parseMasterObjectList(webResponse);
                if (this.masterObjectList.getObjects() == null) {
                    this.status = Status.RETRYLATER;
                    this.retryAfterSeconds = parseRetryAfter(webResponse);
                } else {
                    this.status = Status.AVAILABLE;
                }
                break;
            default:
                assert false : "checkStatusCode should have made it impossible to reach this line.";
            }
        }
    }

    private static MasterObjectList parseMasterObjectList(final WebResponse webResponse) throws IOException {
        try (final InputStream content = webResponse.getResponseStream()) {
            return XmlOutput.fromXml(content, MasterObjectList.class);
        }
    }

    private static int parseRetryAfter(final WebResponse webResponse) {
        final String retryAfter = webResponse.getHeaders().get("Retry-After").get(0);
        if (retryAfter == null) {
            throw new RetryAfterExpectedException();
        }
        return Integer.parseInt(retryAfter);
    }
}
