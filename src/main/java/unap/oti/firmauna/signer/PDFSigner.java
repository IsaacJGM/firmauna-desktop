package unap.oti.firmauna.signer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.io.*;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Calendar;

/**
 * Signs PDFs with detached PKCS#7 signature + optional visual stamp overlay.
 */
public class PDFSigner {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void signPDF(File inputPdf, File outputPdf,
                               X509Certificate cert, PrivateKey privateKey,
                               java.security.cert.Certificate[] chain,
                               String reason, String location, String contact,
                               String signerText, int page,
                               float x, float y, float w, float h) throws Exception {

        try (PDDocument doc = Loader.loadPDF(inputPdf)) {

            // Step 1: Add invisible digital signature
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            String firstName = signerText;
            int nl = signerText.indexOf('\n');
            if (nl > 0) firstName = signerText.substring(0, nl);
            signature.setName(firstName.substring(0, Math.min(50, firstName.length())));
            signature.setLocation(location);
            signature.setReason(reason);
            signature.setContactInfo(contact);
            signature.setSignDate(Calendar.getInstance());

            SignerImpl signer = new SignerImpl(cert, privateKey, chain);
            SignatureOptions signatureOptions = new SignatureOptions();
            // RENIEC cert chain is ~7KB raw, but CMS encoding overhead can
            // push it past 16KB. Reserve 32KB to be safe.
            signatureOptions.setPreferredSignatureSize(32768);
            System.out.println("[DEBUG] SignatureOptions reserved size: " + signatureOptions.getPreferredSignatureSize());
            System.out.println("[DEBUG] SignerImpl class: " + signer.getClass().getName());
            System.out.println("[DEBUG] PDFSigner classloader: " + PDFSigner.class.getClassLoader());
            doc.addSignature(signature, signer, signatureOptions);

            // Step 2: Add visible text stamp
            if (page >= 0 && page < doc.getNumberOfPages()) {
                PDPage pg = doc.getPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, pg, PDPageContentStream.AppendMode.APPEND, true, true)) {

                    cs.beginText();
                    cs.setFont(new PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD), 8);
                    cs.newLineAtOffset(x, y);
                    cs.setLeading(11);
                    for (String line : signerText.split("\n")) {
                        cs.showText(line);
                        cs.newLine();
                    }
                    cs.endText();

                    // Draw border
                    cs.setStrokingColor(0, 0.3f, 0.6f);
                    cs.setLineWidth(1f);
                    cs.addRect(x - 3, y - 3, w, h);
                    cs.stroke();
                }
            }

            doc.saveIncremental(new java.io.FileOutputStream(outputPdf));
        }
    }

    private static class SignerImpl implements SignatureInterface {
        private final X509Certificate cert;
        private final PrivateKey privateKey;
        private final JcaCertStore certStore;
        private final AlgorithmIdentifier sigAlgId;

        SignerImpl(X509Certificate cert, PrivateKey pk, java.security.cert.Certificate[] chain) throws Exception {
            this.cert = cert;
            this.privateKey = pk;
            this.certStore = new JcaCertStore(Arrays.asList(chain));
            this.sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256withRSA");
        }

        @Override
        public byte[] sign(InputStream content) throws java.io.IOException {
            try {
                byte[] bytes = content.readAllBytes();
                CMSSignedDataGenerator gen = new CMSSignedDataGenerator();

                // Custom ContentSigner using java.security.Signature directly.
                // BC's JcaContentSignerBuilder uppercases the algorithm name and
                // routes to SunJCE which lacks SHA256WITHRSA and rejects PKCS#11
                // P11Key with "not a RSAPrivateKey instance". Using Signature
                // directly resolves via SunRsaSign which handles both correctly.
                ContentSigner signer = new ContentSigner() {
                    private final ByteArrayOutputStream stream = new ByteArrayOutputStream();

                    @Override
                    public AlgorithmIdentifier getAlgorithmIdentifier() {
                        return sigAlgId;
                    }

                    @Override
                    public OutputStream getOutputStream() {
                        return stream;
                    }

                    @Override
                    public byte[] getSignature() {
                        try {
                            Signature sig = Signature.getInstance("SHA256withRSA");
                            sig.initSign(privateKey);
                            sig.update(stream.toByteArray());
                            return sig.sign();
                        } catch (Exception e) {
                            throw new RuntimeException("Signature generation failed", e);
                        }
                    }
                };

                gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                    new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                    .build(signer, cert));
                gen.addCertificates(certStore);

                CMSSignedData signed = gen.generate(new CMSProcessableByteArray(bytes), true);
                return signed.getEncoded();
            } catch (Exception e) {
                throw new IOException("CMS signing failed", e);
            }
        }
    }
}