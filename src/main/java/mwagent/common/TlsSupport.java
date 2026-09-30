package mwagent.common;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;

/**
 * Builds the TLS socket factory for server HTTPS calls (non-mTLS).
 *
 * Modes (agent.properties):
 * - http://          : no TLS
 * - https, ssl_verify=false (default) : trusts any certificate, no hostname check (legacy behavior)
 * - https, ssl_verify=true            : verifies the certificate chain and hostname.
 *                                       Uses truststore.path if set, otherwise the JVM default cacerts
 */
public final class TlsSupport {

    private TlsSupport() {
    }

    public static SSLConnectionSocketFactory httpsSocketFactory(boolean sslVerify,
            String truststorePath, String truststorePassword) throws GeneralSecurityException, IOException {

        SSLContextBuilder builder = SSLContexts.custom().setProtocol("TLSv1.2");

        if (!sslVerify) {
            builder.loadTrustMaterial(null, (chain, authType) -> true);
        } else if (truststorePath != null && !truststorePath.isEmpty()) {
            builder.loadTrustMaterial(loadTruststore(truststorePath, truststorePassword), null);
        }
        // sslVerify without truststore: no trust material -> JVM default trust managers (cacerts)

        SSLContext sslContext = builder.build();
        return new SSLConnectionSocketFactory(sslContext, hostnameVerifier(sslVerify));
    }

    public static HostnameVerifier hostnameVerifier(boolean sslVerify) {
        return sslVerify ? new DefaultHostnameVerifier() : NoopHostnameVerifier.INSTANCE;
    }

    public static KeyStore loadTruststore(String path, String password) throws GeneralSecurityException, IOException {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (InputStream in = new FileInputStream(path)) {
            trustStore.load(in, password == null ? null : password.toCharArray());
        }
        return trustStore;
    }

    /** Short description for the startup log. */
    public static String describe(boolean sslVerify, String truststorePath) {
        if (!sslVerify) {
            return "https certificate verification OFF (ssl_verify=false)";
        }
        if (truststorePath != null && !truststorePath.isEmpty()) {
            return "https certificate verification ON (truststore: " + truststorePath + ")";
        }
        return "https certificate verification ON (JVM default cacerts)";
    }
}
