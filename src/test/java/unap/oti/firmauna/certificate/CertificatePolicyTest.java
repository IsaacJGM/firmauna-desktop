package unap.oti.firmauna.certificate;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CertificatePolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");

    @Test
    void acceptsValidRsaSigningCertificate() throws Exception {
        X509Certificate certificate = certificate("RSA", "SHA256withRSA",
            NOW.minus(1, ChronoUnit.DAYS), NOW.plus(1, ChronoUnit.DAYS), KeyUsage.digitalSignature);

        assertEquals(CertificatePolicy.Status.VALID, CertificatePolicy.evaluate(certificate, NOW));
    }

    @Test
    void distinguishesExpiredAndNotYetValidCertificates() throws Exception {
        X509Certificate expired = certificate("RSA", "SHA256withRSA",
            NOW.minus(2, ChronoUnit.DAYS), NOW.minus(1, ChronoUnit.DAYS), KeyUsage.digitalSignature);
        X509Certificate future = certificate("RSA", "SHA256withRSA",
            NOW.plus(1, ChronoUnit.DAYS), NOW.plus(2, ChronoUnit.DAYS), KeyUsage.digitalSignature);

        assertEquals(CertificatePolicy.Status.EXPIRED, CertificatePolicy.evaluate(expired, NOW));
        assertEquals(CertificatePolicy.Status.NOT_YET_VALID, CertificatePolicy.evaluate(future, NOW));
    }

    @Test
    void rejectsUnsupportedAlgorithmAndKeyUsage() throws Exception {
        X509Certificate ec = certificate("EC", "SHA256withECDSA",
            NOW.minus(1, ChronoUnit.DAYS), NOW.plus(1, ChronoUnit.DAYS), KeyUsage.digitalSignature);
        X509Certificate encryptionOnly = certificate("RSA", "SHA256withRSA",
            NOW.minus(1, ChronoUnit.DAYS), NOW.plus(1, ChronoUnit.DAYS), KeyUsage.keyEncipherment);

        assertEquals(CertificatePolicy.Status.UNSUPPORTED_ALGORITHM,
            CertificatePolicy.evaluate(ec, NOW));
        assertEquals(CertificatePolicy.Status.SIGNING_NOT_ALLOWED,
            CertificatePolicy.evaluate(encryptionOnly, NOW));
    }

    private X509Certificate certificate(String keyAlgorithm, String signatureAlgorithm,
                                        Instant notBefore, Instant notAfter, int keyUsage) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(keyAlgorithm);
        if ("RSA".equals(keyAlgorithm)) {
            generator.initialize(2048);
        } else {
            generator.initialize(256);
        }
        KeyPair keyPair = generator.generateKeyPair();
        X500Name name = new X500Name("CN=Certificate Policy Test");
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            name,
            BigInteger.ONE,
            Date.from(notBefore),
            Date.from(notAfter),
            name,
            keyPair.getPublic());
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(keyUsage));
        ContentSigner signer = new JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }
}
