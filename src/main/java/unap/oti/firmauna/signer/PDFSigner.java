package unap.oti.firmauna.signer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

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
            signatureOptions.setPreferredSignatureSize(16384);
            doc.addSignature(signature, signer, signatureOptions);

            // Step 2: Add visible stamp with logo + text
            if (page >= 0 && page < doc.getNumberOfPages()) {
                PDPage pg = doc.getPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, pg, PDPageContentStream.AppendMode.APPEND, true, true)) {

                    // Load logo from classpath
                    PDImageXObject logo = null;
                    try (InputStream is = PDFSigner.class.getClassLoader().getResourceAsStream("assets/LogoUNA.png")) {
                        if (is != null) {
                            logo = PDImageXObject.createFromByteArray(doc, is.readAllBytes(), "LogoUNA");
                        }
                    }

                    // Draw logo on the left side while preserving its original aspect ratio.
                    if (logo != null) {
                        float logoAreaWidth = 65;
                        float logoAreaHeight = h - 10;
                        float logoRatio = (float) logo.getWidth() / logo.getHeight();
                        float logoWidth = logoAreaWidth;
                        float logoHeight = logoWidth / logoRatio;

                        if (logoHeight > logoAreaHeight) {
                            logoHeight = logoAreaHeight;
                            logoWidth = logoHeight * logoRatio;
                        }

                        float logoX = x + 5 + (logoAreaWidth - logoWidth) / 2;
                        float logoY = y + (h - logoHeight) / 2 + 8;
                        cs.drawImage(logo, logoX, logoY, logoWidth, logoHeight);
                    }

                    // Wrap long lines to fit stamp width (~140px at 7pt)
                    PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                    float fontSize = 7;
                    float maxWidth = w - 80; // leave room for logo + margin
                    List<String> wrappedLines = new ArrayList<>();
                    for (String line : signerText.split("\n")) {
                        try {
                            float width = font.getStringWidth(line) / 1000 * fontSize;
                            if (width <= maxWidth) {
                                wrappedLines.add(line);
                            } else {
                                // Word-wrap: split into words and reassemble
                                String[] words = line.split(" ");
                                StringBuilder current = new StringBuilder();
                                for (String word : words) {
                                    String test = current.length() == 0 ? word : current + " " + word;
                                    float testWidth = font.getStringWidth(test) / 1000 * fontSize;
                                    if (testWidth > maxWidth && current.length() > 0) {
                                        wrappedLines.add(current.toString());
                                        current = new StringBuilder(word);
                                    } else {
                                        current = new StringBuilder(test);
                                    }
                                }
                                if (current.length() > 0) {
                                    wrappedLines.add(current.toString());
                                }
                            }
                        } catch (Exception e) {
                            wrappedLines.add(line);
                        }
                    }

                    // Draw wrapped text next to logo
                    cs.beginText();
                    cs.setFont(font, fontSize);
                    cs.newLineAtOffset(x + 75, y + h - 12);
                    cs.setLeading(8);
                    for (String line : wrappedLines) {
                        cs.showText(line);
                        cs.newLine();
                    }
                    cs.endText();
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

                // encapsulate=false creates a DETACHED signature that does NOT
                // embed the PDF content. With true (encapsulate), the entire PDF
                // content is embedded, making the CMS signature huge (280KB+ for
                // a real document) and impossible to reserve space for.
                CMSSignedData signed = gen.generate(new CMSProcessableByteArray(bytes), false);
                return signed.getEncoded();
            } catch (Exception e) {
                throw new IOException("CMS signing failed", e);
            }
        }
    }
}
