package mwagent.common;

import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.security.KeyStore;

import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TlsSupport 테스트 - ssl_verify 옵션에 따른 https 검증 모드
 */
class TlsSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void hostnameVerifier_ShouldFollowSslVerify() {
        assertThat(TlsSupport.hostnameVerifier(false)).isSameAs(NoopHostnameVerifier.INSTANCE);
        assertThat(TlsSupport.hostnameVerifier(true)).isInstanceOf(DefaultHostnameVerifier.class);
    }

    @Test
    void httpsSocketFactory_NoVerify_ShouldBuild() throws Exception {
        assertThat(TlsSupport.httpsSocketFactory(false, "", "")).isNotNull();
    }

    @Test
    void httpsSocketFactory_NoVerify_ShouldIgnoreTruststore() throws Exception {
        // 검증을 끈 기본 모드는 truststore 설정이 잘못돼도 기존처럼 동작해야 한다
        assertThat(TlsSupport.httpsSocketFactory(false, "/no/such/truststore.jks", "x")).isNotNull();
    }

    @Test
    void httpsSocketFactory_VerifyWithJvmDefault_ShouldBuild() throws Exception {
        assertThat(TlsSupport.httpsSocketFactory(true, "", "")).isNotNull();
    }

    @Test
    void httpsSocketFactory_VerifyWithTruststore_ShouldBuild() throws Exception {
        File jks = tempDir.resolve("truststore.jks").toFile();
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        try (FileOutputStream out = new FileOutputStream(jks)) {
            ks.store(out, "changeit".toCharArray());
        }

        assertThat(TlsSupport.httpsSocketFactory(true, jks.getPath(), "changeit")).isNotNull();
    }

    @Test
    void httpsSocketFactory_VerifyWithMissingTruststore_ShouldFail() {
        // 검증 모드에서 truststore 를 못 읽으면 trust-all 로 물러서지 않고 실패한다
        assertThatThrownBy(() -> TlsSupport.httpsSocketFactory(true, "/no/such/truststore.jks", "x"))
            .isInstanceOf(java.io.IOException.class);
    }

    @Test
    void describe_ShouldNameTheMode() {
        assertThat(TlsSupport.describe(false, "/a.jks")).contains("OFF");
        assertThat(TlsSupport.describe(true, "/a.jks")).contains("ON").contains("/a.jks");
        assertThat(TlsSupport.describe(true, "")).contains("JVM default cacerts");
    }
}
