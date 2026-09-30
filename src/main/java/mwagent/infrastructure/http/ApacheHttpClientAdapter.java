package mwagent.infrastructure.http;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.security.KeyStore;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import mwagent.common.SecurityValidator;
import mwagent.common.TlsSupport;
import mwagent.infrastructure.config.ConfigurationProvider;

/**
 * Apache HttpClient adapter implementing HttpClient interface.
 * Supports HTTP, HTTPS (with self-signed certs), and mTLS connections.
 */
public class ApacheHttpClientAdapter implements HttpClient {

    private final Logger logger;
    private final ConfigurationProvider config;

    private CloseableHttpClient httpClient;
    private CloseableHttpClient httpsClient;
    private CloseableHttpClient mtlsClient;

    public ApacheHttpClientAdapter(ConfigurationProvider config, Logger logger) {
        this.config = config;
        this.logger = logger;
        initializeClients();
    }

    private void initializeClients() {
        // Standard HTTP client
        httpClient = HttpClients.createDefault();

        // HTTPS client with self-signed certificate support
        try {
            httpsClient = createHttpsClient();
        } catch (Exception e) {
            // Falls back to the default client, which verifies against JVM cacerts (never trust-all)
            logger.log(Level.WARNING, "Failed to create HTTPS client", e);
            httpsClient = httpClient;
        }

        // mTLS client (if configured)
        if (config.isMtlsEnabled()) {
            try {
                mtlsClient = createMtlsClient();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to create mTLS client", e);
                mtlsClient = httpsClient;
            }
        }
    }

    private CloseableHttpClient createHttpsClient() throws Exception {
        // Add BouncyCastle provider for AIX compatibility
        if ("AIX".equalsIgnoreCase(config.getOs())) {
            Security.addProvider(new BouncyCastleProvider());
        }

        // ssl_verify=false (default): trust any certificate. true: truststore.path or JVM cacerts + hostname check
        SSLConnectionSocketFactory sslSocketFactory = TlsSupport.httpsSocketFactory(
                config.isSslVerify(), config.getTruststorePath(), config.getTruststorePassword());
        logger.info(TlsSupport.describe(config.isSslVerify(), config.getTruststorePath()));

        return HttpClients.custom()
                .setSSLSocketFactory(sslSocketFactory)
                .build();
    }

    private CloseableHttpClient createMtlsClient() throws Exception {
        // Add BouncyCastle provider for AIX compatibility
        if ("AIX".equalsIgnoreCase(config.getOs())) {
            Security.addProvider(new BouncyCastleProvider());
        }

        // Load client keystore (PKCS12)
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream keyStoreStream = new java.io.FileInputStream(config.getKeystorePath())) {
            keyStore.load(keyStoreStream, config.getKeystorePassword().toCharArray());
        }

        // Load truststore (JKS)
        KeyStore trustStore = TlsSupport.loadTruststore(config.getTruststorePath(), config.getTruststorePassword());

        SSLContext sslContext = SSLContexts.custom()
                .setProtocol("TLSv1.2")
                .loadKeyMaterial(keyStore, config.getKeystorePassword().toCharArray())
                .loadTrustMaterial(trustStore, null)
                .build();

        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                sslContext,
                TlsSupport.hostnameVerifier(config.isSslVerify()));

        return HttpClients.custom()
                .setSSLSocketFactory(sslSocketFactory)
                .build();
    }

    private CloseableHttpClient selectClient(String url) {
        if (url.startsWith("https://")) {
            if (config.isMtlsEnabled() && mtlsClient != null) {
                return mtlsClient;
            }
            return httpsClient;
        }
        return httpClient;
    }

    @Override
    public HttpResponse get(String url, Map<String, String> headers) throws HttpClientException {
        HttpGet request = new HttpGet(url);

        // Add headers
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                request.addHeader(entry.getKey(), entry.getValue());
            }
        }

        CloseableHttpClient client = selectClient(url);

        try (CloseableHttpResponse response = client.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity());
            Map<String, String> responseHeaders = extractHeaders(response);

            return new HttpResponse(statusCode, body, responseHeaders);
        } catch (IOException e) {
            throw new HttpClientException("GET request failed: " + url, e);
        }
    }

    @Override
    public HttpResponse post(String url, Map<String, String> headers, String body) throws HttpClientException {
        HttpPost request = new HttpPost(url);

        // Add headers
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                request.addHeader(entry.getKey(), entry.getValue());
            }
        }

        // Set body
        if (body != null) {
            request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        }

        CloseableHttpClient client = selectClient(url);

        try (CloseableHttpResponse response = client.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            Map<String, String> responseHeaders = extractHeaders(response);

            return new HttpResponse(statusCode, responseBody, responseHeaders);
        } catch (IOException e) {
            throw new HttpClientException("POST request failed: " + url, e);
        }
    }

    @Override
    public HttpResponse postForm(String url, Map<String, String> headers, Map<String, String> formData) throws HttpClientException {
        HttpPost request = new HttpPost(url);

        // Add headers
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                request.addHeader(entry.getKey(), entry.getValue());
            }
        }

        // Build form body
        if (formData != null) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : formData.entrySet()) {
                if (sb.length() > 0) {
                    sb.append("&");
                }
                sb.append(entry.getKey()).append("=").append(entry.getValue());
            }
            request.setEntity(new StringEntity(sb.toString(), ContentType.APPLICATION_FORM_URLENCODED));
        }

        CloseableHttpClient client = selectClient(url);

        try (CloseableHttpResponse response = client.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            Map<String, String> responseHeaders = extractHeaders(response);

            return new HttpResponse(statusCode, responseBody, responseHeaders);
        } catch (IOException e) {
            throw new HttpClientException("POST form request failed: " + url, e);
        }
    }

    @Override
    public String downloadFile(String url, Map<String, String> headers, String destinationPath) throws HttpClientException {
        HttpGet request = new HttpGet(url);

        // Add headers
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                request.addHeader(entry.getKey(), entry.getValue());
            }
        }

        CloseableHttpClient client = selectClient(url);

        try (CloseableHttpResponse response = client.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != 200) {
                throw new HttpClientException("File download failed with status: " + statusCode, statusCode);
            }

            HttpEntity entity = response.getEntity();

            // Extract filename from Content-Disposition header
            String fileName = extractFileName(response);
            if (fileName == null || fileName.isEmpty()) {
                fileName = "downloaded_file";
            }
            // The name comes from the server response; never let it leave destinationPath
            if (!SecurityValidator.isValidFilename(fileName)) {
                throw new HttpClientException("Invalid file name in Content-Disposition: " + fileName, statusCode);
            }

            // Save file
            File outputFile = new File(destinationPath, fileName);
            try (InputStream in = new BufferedInputStream(entity.getContent());
                 FileOutputStream out = new FileOutputStream(outputFile)) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            return fileName;
        } catch (IOException e) {
            throw new HttpClientException("File download failed: " + url, e);
        }
    }

    private String extractFileName(CloseableHttpResponse response) {
        Header contentDisposition = response.getFirstHeader("Content-Disposition");
        if (contentDisposition != null) {
            String value = contentDisposition.getValue();
            if (value != null && value.contains("filename=")) {
                String fileName = value.substring(value.indexOf("filename=") + 9);
                fileName = fileName.replace("\"", "").trim();
                try {
                    return URLDecoder.decode(fileName, "UTF-8");
                } catch (Exception e) {
                    return fileName;
                }
            }
        }
        return null;
    }

    private Map<String, String> extractHeaders(CloseableHttpResponse response) {
        Map<String, String> headers = new HashMap<>();
        for (Header header : response.getAllHeaders()) {
            headers.put(header.getName(), header.getValue());
        }
        return headers;
    }

    @Override
    public void close() {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
            if (httpsClient != null && httpsClient != httpClient) {
                httpsClient.close();
            }
            if (mtlsClient != null && mtlsClient != httpsClient) {
                mtlsClient.close();
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error closing HTTP clients", e);
        }
    }
}
