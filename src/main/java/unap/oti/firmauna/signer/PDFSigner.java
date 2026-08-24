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
import java.security.Provider;
import java.security.Security;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.function.Consumer;

/**
 * Signs PDFs with detached PKCS#7 signature + optional visual stamp overlay.
 */
public class PDFSigner {

    public static final float VERTICAL_CONTENT_HORIZONTAL_PADDING_PT = 0f;
    public static final float VERTICAL_LOGO_AREA_HEIGHT_PT = 45f;
    public static final float VERTICAL_LOGO_TOP_PADDING_PT = 0f;
    public static final float VERTICAL_LOGO_TEXT_GAP_PT = 2f;
    public static final float HORIZONTAL_LOGO_AREA_WIDTH_PT = 65f;
    public static final float HORIZONTAL_LOGO_TEXT_GAP_PT = 2f;
    public static final float HORIZONTAL_TEXT_X_PT = HORIZONTAL_LOGO_AREA_WIDTH_PT + HORIZONTAL_LOGO_TEXT_GAP_PT;
    // 210 stamp width - 66 text start = 144.
    public static final float HORIZONTAL_TEXT_MAX_WIDTH_PT = 103f;
    private static final float HELVETICA_ASCENT_RATIO = 0.718f;
    private static final float HELVETICA_DESCENT_RATIO = 0.207f;

    public enum StampLayout {
        HORIZONTAL(170f, 45f, 6f, 6.5f),
        VERTICAL(80f, 81f, 6f, 5.5f);

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
        try (PDDocument source = Loader.loadPDF(inputPdf)) {
            if (page < 0 || page >= source.getNumberOfPages()) {
                throw new IllegalArgumentException("Selected page is outside the document");
            }
            NormalizedStampPosition position = normalizeStampPosition(source.getPage(page).getCropBox(),
                new double[]{x, y}, layout, 0f, 0f);
            signPDF(inputPdf, outputPdf, cert, privateKey, chain, reason, location, contact, signerText,
                List.of(page), position, layout, 0f, 0f);
        }
    }

    /**
     * Adds all requested visual stamps before producing one detached PDF signature.
     */
    public static void signPDF(File inputPdf, File outputPdf,
                               X509Certificate cert, PrivateKey privateKey,
                               java.security.cert.Certificate[] chain,
                               String reason, String location, String contact,
                               String signerText, List<Integer> pages,
                               NormalizedStampPosition normalizedPosition, StampLayout layout,
                               float safeMargin, float topOverflow) throws Exception {
        signPDF(inputPdf, outputPdf, cert, privateKey, chain, reason, location, contact, signerText,
            pages, normalizedPosition, layout, safeMargin, topOverflow, null, null);
    }

    public static void signPDF(File inputPdf, File outputPdf,
                               X509Certificate cert, PrivateKey privateKey,
                               java.security.cert.Certificate[] chain,
                               String reason, String location, String contact,
                               String signerText, List<Integer> pages,
                               NormalizedStampPosition normalizedPosition, StampLayout layout,
                               float safeMargin, float topOverflow,
                               Provider signatureProvider) throws Exception {
        signPDF(inputPdf, outputPdf, cert, privateKey, chain, reason, location, contact, signerText,
            pages, normalizedPosition, layout, safeMargin, topOverflow, signatureProvider, null);
    }

    public static void signPDF(File inputPdf, File outputPdf,
                               X509Certificate cert, PrivateKey privateKey,
                               java.security.cert.Certificate[] chain,
                               String reason, String location, String contact,
                               String signerText, List<Integer> pages,
                               NormalizedStampPosition normalizedPosition, StampLayout layout,
                               float safeMargin, float topOverflow,
                               Provider signatureProvider, Consumer<String> progress) throws Exception {

        try (PDDocument doc = Loader.loadPDF(inputPdf)) {
            if (pages == null || pages.isEmpty()) {
                throw new IllegalArgumentException("At least one page must be selected");
            }

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

            report(progress, "Preparando la firma digital...");
            SignerImpl signer = new SignerImpl(cert, privateKey, chain, signatureProvider, progress);
            SignatureOptions signatureOptions = new SignatureOptions();
            signatureOptions.setPreferredSignatureSize(16384);
            doc.addSignature(signature, signer, signatureOptions);

            // Step 2: Add visible stamps while retaining one detached signature.
            PDImageXObject logo = loadLogo(doc);
            for (int page : pages) {
                if (page < 0 || page >= doc.getNumberOfPages()) {
                    throw new IllegalArgumentException("Selected page is outside the document");
                }
                PDPage pg = doc.getPage(page);
                float[] position = resolveStampPosition(pg.getCropBox(), normalizedPosition, layout,
                    safeMargin, topOverflow);
                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, pg, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    if (layout == StampLayout.VERTICAL) {
                        drawVerticalStamp(cs, logo, signerText, position[0], position[1], layout);
                    } else {
                        drawHorizontalStamp(cs, logo, signerText, position[0], position[1], layout);
                    }
                }
            }

            report(progress, "Solicitando la firma criptográfica...");
            // Windows cannot rename the signed temporary file until PDFBox closes this stream.
            try (OutputStream output = new FileOutputStream(outputPdf)) {
                doc.saveIncremental(output);
            }
            report(progress, "Firma criptográfica generada.");
        }
    }

    private static void report(Consumer<String> progress, String message) {
        if (progress != null) {
            progress.accept(message);
        }
    }

    private static PDImageXObject loadLogo(PDDocument document) throws IOException {
        try (InputStream input = PDFSigner.class.getClassLoader().getResourceAsStream("assets/LogoUNA.png")) {
            return input == null ? null
                : PDImageXObject.createFromByteArray(document, input.readAllBytes(), "LogoUNA");
        }
    }

    public record NormalizedStampPosition(float horizontal, float vertical) { }

    /**
     * Converts a stamp's lower-left position into the CropBox's safe placement area.
     */
    public static NormalizedStampPosition normalizeStampPosition(PDRectangle cropBox, double[] rectangle,
                                                                  StampLayout layout, float safeMargin,
                                                                  float topOverflow) {
        float[] bounds = stampBounds(cropBox, layout, safeMargin, topOverflow);
        return new NormalizedStampPosition(
            normalize((float) rectangle[0], bounds[0], bounds[1]),
            normalize((float) rectangle[1], bounds[2], bounds[3]));
    }

    /**
     * Resolves a normalized safe placement for a page with its own visible CropBox.
     */
    public static float[] resolveStampPosition(PDRectangle cropBox, NormalizedStampPosition position,
                                               StampLayout layout, float safeMargin, float topOverflow) {
        float[] bounds = stampBounds(cropBox, layout, safeMargin, topOverflow);
        return new float[]{
            interpolate(bounds[0], bounds[1], position.horizontal()),
            interpolate(bounds[2], bounds[3], position.vertical())
        };
    }

    private static float[] stampBounds(PDRectangle cropBox, StampLayout layout, float safeMargin,
                                       float topOverflow) {
        float minX = cropBox.getLowerLeftX() + safeMargin;
        float maxX = cropBox.getUpperRightX() - layout.getWidth() - safeMargin;
        float minY = cropBox.getLowerLeftY() + safeMargin;
        float maxY = cropBox.getUpperRightY() - layout.getHeight() - safeMargin - topOverflow;
        if (maxX < minX || maxY < minY) {
            throw new IllegalArgumentException("Page CropBox is too small for the selected stamp layout");
        }
        return new float[]{minX, maxX, minY, maxY};
    }

    private static float normalize(float value, float min, float max) {
        return max == min ? 0f : clamp((value - min) / (max - min), 0f, 1f);
    }

    private static float interpolate(float min, float max, float normalized) {
        return min + (max - min) * clamp(normalized, 0f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    private static void drawHorizontalStamp(PDPageContentStream cs, PDImageXObject logo,
                                            String signerText, float x, float y,
                                            StampLayout layout) throws IOException {
        float h = layout.getHeight();
        if (logo != null) {
            float logoAreaWidth = HORIZONTAL_LOGO_AREA_WIDTH_PT;
            float logoAreaHeight = h;
            float logoRatio = (float) logo.getWidth() / logo.getHeight();
            float logoWidth = logoAreaWidth;
            float logoHeight = logoWidth / logoRatio;

            if (logoHeight > logoAreaHeight) {
                logoHeight = logoAreaHeight;
                logoWidth = logoHeight * logoRatio;
            }

            float logoX = x + (logoAreaWidth - logoWidth) / 2;
            float logoY = y + (h - logoHeight) / 2;
            cs.drawImage(logo, logoX, logoY, logoWidth, logoHeight);
        }

        drawText(cs, signerText, x + HORIZONTAL_TEXT_X_PT, y,
            HORIZONTAL_TEXT_MAX_WIDTH_PT, layout,
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

    public static float horizontalTextBaseline(float stampBottom, StampLayout layout,
                                               List<StampTextLine> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Horizontal stamp text must contain at least one line");
        }

        float firstLineAscent = HELVETICA_ASCENT_RATIO * lines.get(0).fontSize();
        float lastLineDescent = HELVETICA_DESCENT_RATIO
            * lines.get(lines.size() - 1).fontSize();
        float interlineHeight = (lines.size() - 1) * layout.getLineLeading();
        float textBlockVisualHeight = firstLineAscent + interlineHeight + lastLineDescent;
        float textBlockCenter = stampBottom + layout.getHeight() / 2;

        // Center the rendered glyph bounds, not the baselines, in the logo area.
        return textBlockCenter - textBlockVisualHeight / 2
            + interlineHeight + lastLineDescent;
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
            : horizontalTextBaseline(y, layout, lines);
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
            float fontSize = layout.getFontSize();
            for (String line : wrapLines(sourceLine, font, fontSize, maxWidth)) {
                lines.add(new StampTextLine(line, fontSize));
            }
        }
        return lines;
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
        private final Provider signatureProvider;
        private final Consumer<String> progress;

        SignerImpl(X509Certificate cert, PrivateKey pk, java.security.cert.Certificate[] chain,
                   Provider signatureProvider, Consumer<String> progress) throws Exception {
            this.cert = cert;
            this.privateKey = pk;
            this.certStore = new JcaCertStore(chain == null || chain.length == 0
                ? List.of(cert)
                : Arrays.asList(chain));
            this.sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256withRSA");
            this.signatureProvider = signatureProvider;
            this.progress = progress;
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
                        Signature sig = signatureProvider == null
                            ? Signature.getInstance("SHA256withRSA")
                            : Signature.getInstance("SHA256withRSA", signatureProvider);
                        report(progress, "Autorizando la clave privada...");
                        sig.initSign(privateKey);
                        report(progress, "Generando la firma digital...");
                        sig.update(stream.toByteArray());
                        byte[] signature = sig.sign();
                        report(progress, "Firma digital recibida.");
                        return signature;
                    } catch (Exception e) {
                        throw new RuntimeException("No se pudo generar la firma: " + rootMessage(e), e);
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

        private static String rootMessage(Exception exception) {
            Throwable cause = exception;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            String message = cause.getMessage();
            return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
        }
    }
}
