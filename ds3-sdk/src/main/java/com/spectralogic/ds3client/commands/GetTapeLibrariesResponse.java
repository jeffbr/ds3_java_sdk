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

import com.spectralogic.ds3client.models.tape.TapeLibraries;
import com.spectralogic.ds3client.models.tape.TapeLibrary;
import com.spectralogic.ds3client.networking.WebResponse;
import com.spectralogic.ds3client.serializer.XmlOutput;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class GetTapeLibrariesResponse extends AbstractResponse{

    private List<TapeLibrary> tapeLibraries;

    public GetTapeLibrariesResponse(final WebResponse response) throws IOException {
        super(response);
    }

    @Override
    protected void processResponse() throws IOException {
        try (final WebResponse response = getResponse()) {
            this.checkStatusCode(200);
            try (final InputStream inputStream = response.getResponseStream()) {
                this.tapeLibraries = XmlOutput.fromXml(inputStream, TapeLibraries.class).getTapeLibraries();
            }
        }
    }

    public List<TapeLibrary> getTapeLibraries() {
        return tapeLibraries;
    }
}
