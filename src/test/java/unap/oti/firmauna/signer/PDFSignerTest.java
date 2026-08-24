package unap.oti.firmauna.signer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PDFSignerTest {

    @Test
    void closesSignedOutputBeforeItIsMoved() throws Exception {
        Path directory = Files.createTempDirectory("firmauna-test-");
        Path input = directory.resolve("input.pdf");
        Path temporaryOutput = directory.resolve(".firmauna-output.pdf");
        Path savedOutput = directory.resolve("input [FU].pdf");
        try {
            try (PDDocument document = new PDDocument()) {
                document.addPage(new PDPage());
                document.save(input.toFile());
            }

            KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            X509Certificate certificate = certificate(keyPair);
            PDFSigner.signPDF(input.toFile(), temporaryOutput.toFile(), certificate, keyPair.getPrivate(),
                new java.security.cert.Certificate[]{certificate}, "Prueba", "Puno", "Prueba",
                "Firmado digitalmente por: Prueba", List.of(0),
                new PDFSigner.NormalizedStampPosition(0, 0), PDFSigner.StampLayout.HORIZONTAL,
                0, 0);

            Files.move(temporaryOutput, savedOutput);
            assertTrue(Files.isRegularFile(savedOutput));
        } finally {
            Files.deleteIfExists(savedOutput);
            Files.deleteIfExists(temporaryOutput);
            Files.deleteIfExists(input);
            Files.deleteIfExists(directory);
        }
    }

    private X509Certificate certificate(KeyPair keyPair) throws Exception {
        X500Name name = new X500Name("CN=PDF Signer Test");
        Instant now = Instant.now();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            name, BigInteger.ONE, Date.from(now.minusSeconds(60)), Date.from(now.plusSeconds(3600)),
            name, keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }
}
