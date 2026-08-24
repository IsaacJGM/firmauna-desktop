package unap.oti.firmauna.certificate;

import java.security.cert.X509Certificate;
import java.time.Instant;

/**
 * Determines whether a certificate is compatible with the current PDF signing algorithm.
 */
public final class CertificatePolicy {

    public enum Status {
        VALID,
        NOT_YET_VALID,
        EXPIRED,
        UNSUPPORTED_ALGORITHM,
        SIGNING_NOT_ALLOWED
    }

    private CertificatePolicy() {
    }

    public static Status evaluate(X509Certificate certificate, Instant now) {
        if (certificate.getNotBefore().toInstant().isAfter(now)) {
            return Status.NOT_YET_VALID;
        }
        if (!certificate.getNotAfter().toInstant().isAfter(now)) {
            return Status.EXPIRED;
        }
        if (!"RSA".equalsIgnoreCase(certificate.getPublicKey().getAlgorithm())) {
            return Status.UNSUPPORTED_ALGORITHM;
        }

        boolean[] keyUsage = certificate.getKeyUsage();
        boolean allowsSigning = keyUsage == null
            || keyUsage.length > 0 && keyUsage[0]
            || keyUsage.length > 1 && keyUsage[1];
        return allowsSigning ? Status.VALID : Status.SIGNING_NOT_ALLOWED;
    }
}
