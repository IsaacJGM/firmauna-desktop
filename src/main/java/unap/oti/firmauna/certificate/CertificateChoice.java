package unap.oti.firmauna.certificate;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * A certificate backed by a private key in the active signing provider.
 */
public record CertificateChoice(String alias, X509Certificate certificate,
                                List<Certificate> certificateChain) {
    public CertificateChoice {
        certificateChain = List.copyOf(certificateChain);
    }
}
