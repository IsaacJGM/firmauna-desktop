package unap.oti.firmauna.certificate;

import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Supplies signing credentials without exposing platform-specific keystore details to the UI.
 */
public interface SigningProvider extends AutoCloseable {

    void open(char[] authorization) throws Exception;

    boolean requiresApplicationPin();

    String getDisplayName();

    List<CertificateChoice> getCertificateChoices() throws Exception;

    void selectKeyAlias(String alias) throws Exception;

    X509Certificate getCertificate() throws Exception;

    Certificate[] getCertificateChain() throws Exception;

    PrivateKey getPrivateKey() throws Exception;

    /**
     * An explicit signature provider when the private key requires one, such as SunMSCAPI.
     */
    default Provider getSignatureProvider() {
        return null;
    }

    @Override
    void close();
}
