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

import com.spectralogic.ds3client.networking.Headers;
import com.spectralogic.ds3client.networking.WebResponse;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class MockedWebResponse implements WebResponse {
    private final InputStream responseStream;
    private final int statusCode;
    private final Headers headers;

    public MockedWebResponse(final String responseString, final int statusCode, final Map<String, String> headers) {
        this.responseStream = IOUtils.toInputStream(responseString);
        this.statusCode = statusCode;
        this.headers = new MockedHeaders(headers);
    }
    
    @Override
    public InputStream getResponseStream() throws IOException {
        return this.responseStream;
    }

    @Override
    public int getStatusCode() {
        return this.statusCode;
    }

    @Override
    public Headers getHeaders() {
        return headers;
    }

    @Override
    public void close() throws IOException {
    }
}
