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

import com.google.common.base.Joiner;
import com.google.common.collect.Multimap;
import com.google.common.net.UrlEscapers;
import com.spectralogic.ds3client.commands.Ds3Request;
import com.spectralogic.ds3client.commands.PutObjectRequest;
import com.spectralogic.ds3client.models.Checksum;
import com.spectralogic.ds3client.models.SignatureDetails;
import com.spectralogic.ds3client.networking.*;
import com.spectralogic.ds3client.utils.DateFormatter;
import com.spectralogic.ds3client.utils.SSLSetupException;
import com.spectralogic.ds3client.utils.Signature;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Map;

public class NetworkClientImpl implements NetworkClient {
    final static private Logger LOG = LoggerFactory.getLogger(NetworkClientImpl.class);
    final static private String HOST = "HOST";
    final static private String DATE = "DATE";
    final static private String AUTHORIZATION = "Authorization";
    final static private String CONTENT_TYPE = "Content-Type";
    final static private String CONTENT_MD5 = "Content-MD5";
    final static private String CONTENT_SHA256 = "Content-SHA256";
    final static private String CONTENT_SHA512 = "Content-SHA512";
    final static private String CONTENT_CRC32 = "Content-CRC32";
    final static private String CONTENT_CRC32C = "Content-CRC32C";
    final static private int MAX_CONNECTION_PER_ROUTE = 50;
    final static private int MAX_CONNECTION_TOTAL = 100;

    final private ConnectionDetails connectionDetails;

    final private CloseableHttpClient client;
    final private HttpHost host;

    public NetworkClientImpl(final ConnectionDetails connectionDetails) {
        if (connectionDetails == null) throw new AssertionError("ConnectionDetails cannot be null");
        try {
            this.connectionDetails = connectionDetails;
            this.host = buildHost(connectionDetails);
            this.client = createDefaultClient(connectionDetails);
        } catch (final MalformedURLException e) {
            // TODO In 3.0 we should remove this try catch and expose the exception so that
            // we do not create a client that has a bad host url
            throw new RuntimeException(e);
        }
    }


    public NetworkClientImpl(final ConnectionDetails connectionDetails, final CloseableHttpClient client) {
        if (connectionDetails == null) throw new AssertionError("ConnectionDetails cannot be null");
        if (client == null) throw new AssertionError("CloseableHttpClient cannot be null");
        try {
            this.connectionDetails = connectionDetails;
            this.host = buildHost(connectionDetails);
            this.client = client;
        } catch (final MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }


    private static CloseableHttpClient createDefaultClient(final ConnectionDetails connectionDetails) {
        final PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setDefaultMaxPerRoute(MAX_CONNECTION_PER_ROUTE);
        connectionManager.setMaxTotal(MAX_CONNECTION_TOTAL);

        if (connectionDetails.isHttps() && !connectionDetails.isCertificateVerification()) {
            try {

                final SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, new TrustStrategy() {
                    @Override
                    public boolean isTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
                        return true;
                    }
                }).useTLS().build();

                final SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, new AllowAllHostnameVerifier());
                return HttpClients.custom()
                        .setConnectionManager(connectionManager)
                        .setSSLSocketFactory(
                        sslsf).build();

            } catch (final NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
                throw new SSLSetupException(e);
            }
        }
        else {
            return HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .build();
        }
    }

    private static HttpHost buildHost(final ConnectionDetails connectionDetails) throws MalformedURLException {
        final URI proxyUri = connectionDetails.getProxy();
        if (proxyUri != null) {
            return new HttpHost(proxyUri.getHost(), proxyUri.getPort(), proxyUri.getScheme());
        } else {
            final URL url = NetUtils.buildUrl(connectionDetails, "/");
            return new HttpHost(url.getHost(), NetUtils.getPort(url), url.getProtocol());
        }
    }

    @Override
    public ConnectionDetails getConnectionDetails() {
        return this.connectionDetails;
    }

    @Override
    public WebResponse getResponse(final Ds3Request request) throws IOException, SignatureException {
        try (final RequestExecutor requestExecutor = new RequestExecutor(this.client, host, request)) {
            int redirectCount = 0;
            do {
                final CloseableHttpResponse response = requestExecutor.execute();
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_TEMPORARY_REDIRECT) {
                    redirectCount++;
                    response.close();
                    LOG.info("Performing retry - attempt: " + redirectCount);
                }
                else {
                    LOG.info("Got response from server");
                    return new WebResponseImpl(response);
                }
            } while (redirectCount < this.connectionDetails.getRetries());
            
            throw new TooManyRedirectsException(redirectCount);
        }
    }

    @Override
    public void close() throws IOException {
        this.client.close();
    }

    private class RequestExecutor implements Closeable {
        private final Ds3Request ds3Request;
        private final InputStream content;
        private final HttpHost host;
        private final String hash;
        private final Checksum.Type checksumType;
        private final CloseableHttpClient client;

        public RequestExecutor(final CloseableHttpClient client, final HttpHost host, final Ds3Request ds3Request) throws IOException {
            this.client = client;
            this.ds3Request = ds3Request;
            this.host = host;
            this.content = ds3Request.getStream();
            if (this.content != null && !this.content.markSupported()) {
                throw new RequiresMarkSupportedException();
            }

            LOG.info("Sending request: " + this.ds3Request.getVerb() + " " + this.host.toString() + "" + this.ds3Request.getPath());
            this.checksumType = ds3Request.getChecksumType();
            this.hash = this.buildHash();
        }
        
        public CloseableHttpResponse execute() throws IOException, SignatureException {
            if (this.content != null) {
                this.content.reset();
            }
            
            final HttpRequest httpRequest = this.buildHttpRequest();
            this.addHeaders(httpRequest);
            return client.execute(this.host, httpRequest, this.getContext());
        }

        private HttpRequest buildHttpRequest() throws IOException {
            final String verb = this.ds3Request.getVerb().toString();
            final String path = this.buildPath();
            if (this.content != null) {
                final BasicHttpEntityEnclosingRequest httpRequest = new BasicHttpEntityEnclosingRequest(verb, path);

                final Ds3InputStreamEntity entityStream = new Ds3InputStreamEntity(this.content, this.ds3Request.getSize(), ContentType.create(this.ds3Request.getContentType()), this.ds3Request.getPath());
                entityStream.setBufferSize(NetworkClientImpl.this.connectionDetails.getBufferSize());
                httpRequest.setEntity(entityStream);
                return httpRequest;
            } else {
                return new BasicHttpRequest(verb, path);
            }
        }

        private String buildPath() {
            String path = UrlEscapers.urlFragmentEscaper().escape(this.ds3Request.getPath());
            final Map<String, String> queryParams = this.ds3Request.getQueryParams();
            if (!queryParams.isEmpty()) {
                path += "?" + NetUtils.buildQueryString(queryParams);
            }
            return path;
        }

        private void addHeaders(final HttpRequest httpRequest) throws IOException, SignatureException {
            // Add common headers.
            final String date = DateFormatter.dateToRfc882();
            httpRequest.addHeader(HOST, NetUtils.buildHostField(NetworkClientImpl.this.connectionDetails));
            httpRequest.addHeader(DATE, date);
            httpRequest.addHeader(CONTENT_TYPE, this.ds3Request.getContentType());
            
            // Add custom headers.
            for(final Map.Entry<String, String> header: this.ds3Request.getHeaders().entries()) {
                httpRequest.addHeader(header.getKey(), header.getValue());
            }
            
            // Add the hash header.
            if (!this.hash.isEmpty()) {
                httpRequest.addHeader(getHashType(ds3Request.getChecksumType()), this.hash);
            }
            
            // Add the signature header.
            httpRequest.addHeader(AUTHORIZATION, this.getSignature(new SignatureDetails(
                this.ds3Request.getVerb(),
                this.hash,
                this.ds3Request.getContentType(),
                date,
                canonicalizeAmzHeaders(this.ds3Request.getHeaders()),
                canonicalizeResource(this.ds3Request.getPath(), this.ds3Request.getQueryParams()),
                NetworkClientImpl.this.connectionDetails.getCredentials()
            )));
        }

        private String getHashType(final Checksum.Type checksumType) {
            switch (checksumType) {
                case MD5: return CONTENT_MD5;
                case SHA256: return CONTENT_SHA256;
                case SHA512: return CONTENT_SHA512;
                case CRC32: return CONTENT_CRC32;
                case CRC32C: return CONTENT_CRC32C;
                case NONE:
                default:
                    return "";
            }
        }


        private String canonicalizeResource(final String path, final Map<String, String> queryParams) {

            final StringBuilder canonicalizedResource = new StringBuilder();
            canonicalizedResource.append(UrlEscapers.urlFragmentEscaper().escape(path));

            if (queryParams.containsKey("delete")) {
                canonicalizedResource.append("?delete");
            }

            return canonicalizedResource.toString();
        }

        private String canonicalizeAmzHeaders(
                final Multimap<String, String> customHeaders) {
            final StringBuilder ret = new StringBuilder();
            for (final Map.Entry<String, Collection<String>> header : customHeaders
                    .asMap().entrySet()) {
                final String key = header.getKey().toLowerCase();
                if (key.startsWith(PutObjectRequest.AMZ_META_HEADER)
                        && header.getValue().size() > 0) {
                    ret.append(key).append(":");
                    ret.append(Joiner.on(",").join(header.getValue()));
                    ret.append('\n');
                }
            }
            return ret.toString();
        }

        private String buildHash() throws IOException {
            return this.ds3Request.getChecksum().match(new HashGeneratingMatchHandler(this.content, this.checksumType));
        }

        private String getSignature(final SignatureDetails details) throws SignatureException {
            return "AWS " + NetworkClientImpl.this.connectionDetails.getCredentials().getClientId() + ':' + Signature.signature(details);
        }
        
        private HttpClientContext getContext() {
            final HttpClientContext context = new HttpClientContext();
            context.setRequestConfig(
                RequestConfig
                    .custom()
                    .setRedirectsEnabled(false)
                    .build()
            );
            return context;
        }

        @Override
        public void close() throws IOException {
            if (this.content != null) {
                this.content.close();
            }
        }
    }
}
