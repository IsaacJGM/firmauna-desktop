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

    public static final float VERTICAL_CONTENT_HORIZONTAL_PADDING_PT = 0f;
    public static final float VERTICAL_LOGO_AREA_HEIGHT_PT = 45f;
    public static final float VERTICAL_LOGO_TOP_PADDING_PT = 0f;
    public static final float VERTICAL_LOGO_TEXT_GAP_PT = 2f;
    private static final float HELVETICA_ASCENT_RATIO = 0.718f;
    private static final float HELVETICA_DESCENT_RATIO = 0.207f;

    public enum StampLayout {
        HORIZONTAL(220f, 70f, 6f, 6.5f),
        VERTICAL(80f, 78f, 5f, 5.5f);

        private final float width;
        private final float height;
        private final float fontSize;
        private final float lineLeading;

        StampLayout(float width, float height, float fontSize, float lineLeading) {
            this.width = width;
            this.height = height;
            this.fontSize = fontSize;
            this.lineLeading = lineLeading;
        }

        public float getWidth() {
            return width;
        }

        public float getHeight() {
            return height;
        }

        public float getFontSize() {
            return fontSize;
        }

        public float getLineLeading() {
            return lineLeading;
        }
    }

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void signPDF(File inputPdf, File outputPdf,
                               X509Certificate cert, PrivateKey privateKey,
                               java.security.cert.Certificate[] chain,
                                String reason, String location, String contact,
                                String signerText, int page,
                                float x, float y, StampLayout layout) throws Exception {

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

                    if (layout == StampLayout.VERTICAL) {
                        drawVerticalStamp(cs, logo, signerText, x, y, layout);
                    } else {
                        drawHorizontalStamp(cs, logo, signerText, x, y, layout);
                    }
                }
            }

            doc.saveIncremental(new java.io.FileOutputStream(outputPdf));
        }
    }

    private static void drawHorizontalStamp(PDPageContentStream cs, PDImageXObject logo,
                                            String signerText, float x, float y,
                                            StampLayout layout) throws IOException {
        float h = layout.getHeight();
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

        drawText(cs, signerText, x + 75, y + h - 12, layout.getWidth() - 80, layout,
            Float.POSITIVE_INFINITY);
    }

    private static void drawVerticalStamp(PDPageContentStream cs, PDImageXObject logo,
                                          String signerText, float x, float y,
                                          StampLayout layout) throws IOException {
        float w = layout.getWidth();
        float h = layout.getHeight();
        float logoBottom = y + h - VERTICAL_LOGO_TOP_PADDING_PT - VERTICAL_LOGO_AREA_HEIGHT_PT;
        if (logo != null) {
            float logoAreaWidth = w - VERTICAL_CONTENT_HORIZONTAL_PADDING_PT * 2;
            float logoAreaHeight = VERTICAL_LOGO_AREA_HEIGHT_PT;
            float logoRatio = (float) logo.getWidth() / logo.getHeight();
            float logoWidth = logoAreaWidth;
            float logoHeight = logoWidth / logoRatio;

            if (logoHeight > logoAreaHeight) {
                logoHeight = logoAreaHeight;
                logoWidth = logoHeight * logoRatio;
            }

            float logoX = x + VERTICAL_CONTENT_HORIZONTAL_PADDING_PT;
            float logoY = y + h - VERTICAL_LOGO_TOP_PADDING_PT - logoHeight;
            logoBottom = logoY;
            cs.drawImage(logo, logoX, logoY, logoWidth, logoHeight);
        }

        drawText(cs, signerText,
            x + VERTICAL_CONTENT_HORIZONTAL_PADDING_PT,
            y, w - VERTICAL_CONTENT_HORIZONTAL_PADDING_PT * 2, layout, logoBottom);
    }

    public static float verticalTextBaseline(float stampBottom, StampLayout layout) {
        return verticalTextBaseline(stampBottom, layout, 1, layout.getFontSize());
    }

    public static float verticalTextBaseline(float stampBottom, StampLayout layout,
                                             int renderedLineCount, float lastLineFontSize) {
        return verticalTextBaseline(stampBottom, Float.POSITIVE_INFINITY, layout,
            renderedLineCount, lastLineFontSize);
    }

    public static float verticalTextBaseline(float stampBottom, float logoBottom,
                                             StampLayout layout, int renderedLineCount,
                                             float lastLineFontSize) {
        float bottomAnchoredBaseline = stampBottom
            + HELVETICA_DESCENT_RATIO * lastLineFontSize
            + (renderedLineCount - 1) * layout.getLineLeading();
        float logoClearanceBaseline = logoBottom - VERTICAL_LOGO_TEXT_GAP_PT
            - HELVETICA_ASCENT_RATIO * layout.getFontSize();
        // Glue the text to the logo; only push it down when that would
        // overflow past the stamp's bottom edge.
        return Math.max(bottomAnchoredBaseline, logoClearanceBaseline);
    }

    private static void drawText(PDPageContentStream cs, String signerText,
                                  float x, float y, float maxWidth,
                                  StampLayout layout, float logoBottom) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        cs.beginText();
        cs.setLeading(layout.getLineLeading());
        List<StampTextLine> lines = layoutText(signerText, layout, maxWidth);
        float baseline = layout == StampLayout.VERTICAL
            ? verticalTextBaseline(y, logoBottom, layout, lines.size(),
                lines.get(lines.size() - 1).fontSize())
            : y;
        cs.newLineAtOffset(x, baseline);
        for (StampTextLine line : lines) {
            float fontSize = line.fontSize();
            cs.setFont(font, fontSize);
            cs.showText(line.text());
            cs.newLine();
        }
        cs.endText();
    }

    public record StampTextLine(String text, float fontSize) { }

    public static List<StampTextLine> layoutText(String signerText, StampLayout layout,
                                                  float maxWidth) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        List<StampTextLine> lines = new ArrayList<>();
        for (String sourceLine : signerText.split("\\n")) {
            float fontSize = fontSizeForLine(sourceLine, layout);
            for (String line : wrapLines(sourceLine, font, fontSize, maxWidth)) {
                lines.add(new StampTextLine(line, fontSize));
            }
        }
        return lines;
    }

    private static float fontSizeForLine(String line, StampLayout layout) {
        return layout == StampLayout.VERTICAL && line.startsWith("Fecha:")
            ? 4.5f
            : layout.getFontSize();
    }

    private static List<String> wrapLines(String text, PDType1Font font,
                                          float fontSize, float maxWidth) throws IOException {
        List<String> wrappedLines = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (font.getStringWidth(line) / 1000 * fontSize <= maxWidth) {
                wrappedLines.add(line);
                continue;
            }

            StringBuilder current = new StringBuilder();
            for (String word : line.split(" ")) {
                String candidate = current.length() == 0 ? word : current + " " + word;
                if (font.getStringWidth(candidate) / 1000 * fontSize > maxWidth && current.length() > 0) {
                    wrappedLines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            if (current.length() > 0) {
                wrappedLines.add(current.toString());
            }
        }
        return wrappedLines;
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
