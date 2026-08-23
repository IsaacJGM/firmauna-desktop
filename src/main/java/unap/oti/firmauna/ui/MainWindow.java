package unap.oti.firmauna.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;

import unap.oti.firmauna.pkcs11.TokenProvider;
import unap.oti.firmauna.signer.PDFSigner;
import unap.oti.firmauna.signer.PDFSigner.StampLayout;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainWindow {

    private final Stage stage;
    private final Label statusLabel = new Label("Seleccione un PDF para firmar.");
    private final ComboBox<String> reasonCombo = new ComboBox<>();
    private final TextField roleField = new TextField();
    private final ToggleGroup stampLayoutGroup = new ToggleGroup();
    private volatile StampLayout stampLayout = StampLayout.HORIZONTAL;
    private final Button signBtn = new Button("Firmar PDF");
    private final Button selectBtn = new Button("Seleccionar PDF...");
    private final Label pageLabel = new Label("Página 1 de 1");
    private Button prevPageBtn;
    private Button nextPageBtn;
    private final Label fileInfoLabel = new Label("Ningún documento seleccionado.");
    private final Label stepPdfLabel = new Label("○ PDF cargado");
    private final Label stepPositionLabel = new Label("○ Posicioná la firma");
    private final Label stepSignLabel = new Label("○ Firmá con tu token");
    private final Label stepSaveLabel = new Label("○ PDF guardado automáticamente");
    private final Label previewHelpLabel = new Label("Seleccioná un PDF para comenzar.");

    // PDF preview canvas + draggable signature box (integrated, no separate dialog)
    // Leave room for page navigation and contextual help below the preview.
    private final Canvas canvas = new Canvas(560, 560);
    private final GraphicsContext gc = canvas.getGraphicsContext2D();

    private File selectedPdf;
    private PDDocument currentDoc;
    private X509Certificate cert;
    private PrivateKey privateKey;
    private java.security.cert.Certificate[] certChain;

    private int currentPage = 0;
    private int totalPages = 1;
    // Box + render transform
    private double boxX = 40, boxY = 40;
    private double dragStartX, dragStartY;
    private boolean dragging;
    private double canvasScale = 1.0;
    private double offsetX = 0, offsetY = 0;
    private final double renderScale = 1.5;
    private double pdfToCanvasScaleX = 1.0, pdfToCanvasScaleY = 1.0;
    private double pdfPageW = 0, pdfPageH = 0;
    private double cropLlx = 0, cropLly = 0;
    // Visual-only inset. The real signing rectangle remains unchanged.
    private static final double PREVIEW_BOX_INSET_PT = 0.0;
    private static final double STAMP_SAFE_MARGIN_PT = 5.0;
    // The existing horizontal logo placement extends three points above its layout box.
    private static final double HORIZONTAL_LOGO_TOP_OVERFLOW_PT = 3.0;
    private double boxWcanvas = StampLayout.HORIZONTAL.getWidth(), boxHcanvas = StampLayout.HORIZONTAL.getHeight();
    private Image pageImage;
    private final Image stampPreviewLogo = new Image(
        MainWindow.class.getResourceAsStream("/assets/LogoUNA.png"));

    public MainWindow(Stage stage) {
        this.stage = stage;
        stage.setTitle("FirmaUNA");
        stage.setMinWidth(1050);
        stage.setMinHeight(680);

        reasonCombo.getItems().addAll(
            "Soy el autor del documento",
            "En señal de conformidad",
            "Doy V.B.",
            "Por encargo",
            "Firma titular",
            "Firma por encargo",
            "Firma recepción",
            "Firma notificación"
        );
        reasonCombo.setValue("Soy el autor del documento");
        reasonCombo.valueProperty().addListener((observable, previous, current) -> redrawPreviewIfAvailable());
        roleField.textProperty().addListener((observable, previous, current) -> redrawPreviewIfAvailable());
        stampLayoutGroup.selectedToggleProperty().addListener((observable, previous, current) -> {
            if (current != null && current.getUserData() instanceof StampLayout layout) {
                stampLayout = layout;
                for (javafx.scene.control.Toggle toggle : stampLayoutGroup.getToggles()) {
                    if (toggle instanceof ToggleButton card) {
                        card.setStyle(toggle == current ? selectedLayoutCardStyle() : layoutCardStyle());
                    }
                }
                updatePreviewHelp();
                updateStampLayout();
            }
        });

        setupCanvasDrag();
    }

    public void show() {
        stage.setScene(new Scene(buildRoot(), 1100, 720));
        stage.show();
    }

    private VBox buildRoot() {
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #306080; -fx-padding: 12 16;");
        header.setAlignment(Pos.CENTER_LEFT);

        ImageView headerLogo = new ImageView(new Image(
            getClass().getResourceAsStream("/assets/unapicono.png")));
        headerLogo.setFitHeight(40);
        headerLogo.setPreserveRatio(true);
        headerLogo.setSmooth(true);

        Label title = new Label("FirmaUNA");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");
        Label subtitleLbl = new Label("Firma Digital · UNA Puno");
        subtitleLbl.setStyle("-fx-text-fill: #b0c4de; -fx-font-size: 11px;");

        HBox titleGroup = new HBox(10, headerLogo, new VBox(2, title, subtitleLbl));
        titleGroup.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(titleGroup);

        SplitPane split = new SplitPane();
        split.setDividerPositions(0.62);

        // LEFT: preview + page navigation
        VBox left = new VBox(8);
        left.setPadding(new Insets(8));
        left.setStyle("-fx-background-color: #e8e8e8; -fx-border-color: #ccc;");
        left.setAlignment(Pos.CENTER);

        HBox nav = new HBox(12);
        nav.setAlignment(Pos.CENTER);
        prevPageBtn = new Button("<<");
        nextPageBtn = new Button(">>");
        prevPageBtn.setStyle("-fx-font-weight: bold;");
        nextPageBtn.setStyle("-fx-font-weight: bold;");
        prevPageBtn.setOnAction(e -> prevPage());
        nextPageBtn.setOnAction(e -> nextPage());
        nav.getChildren().addAll(prevPageBtn, pageLabel, nextPageBtn);
        updatePageNavigation();

        previewHelpLabel.setWrapText(true);
        previewHelpLabel.setMaxWidth(520);
        previewHelpLabel.setAlignment(Pos.CENTER);
        previewHelpLabel.setStyle("-fx-text-fill: #555; -fx-font-size: 11px; -fx-padding: 2 8 4 8;");

        VBox.setVgrow(canvas, Priority.ALWAYS);
        left.getChildren().addAll(canvas, nav, previewHelpLabel);

        // RIGHT: controls (no PIN field; PIN is requested in a modal on Firmar)
        VBox controls = new VBox(10);
        controls.setPadding(new Insets(15));
        controls.setMinWidth(300);

        selectBtn.setMaxWidth(Double.MAX_VALUE);
        selectBtn.setOnAction(e -> openFile());

        fileInfoLabel.setWrapText(true);
        fileInfoLabel.setStyle("-fx-text-fill: #555; -fx-font-size: 11px;");

        reasonCombo.setMaxWidth(Double.MAX_VALUE);
        roleField.setPromptText("Ej. Docente Universitario");
        roleField.setMaxWidth(Double.MAX_VALUE);

        ToggleButton horizontalLayout = createLayoutCard("Horizontal", false, StampLayout.HORIZONTAL);
        horizontalLayout.setToggleGroup(stampLayoutGroup);
        horizontalLayout.setUserData(StampLayout.HORIZONTAL);
        horizontalLayout.setSelected(true);
        ToggleButton verticalLayout = createLayoutCard("Vertical", true, StampLayout.VERTICAL);
        verticalLayout.setToggleGroup(stampLayoutGroup);
        verticalLayout.setUserData(StampLayout.VERTICAL);
        horizontalLayout.setStyle(selectedLayoutCardStyle());
        HBox layoutChoices = new HBox(10, horizontalLayout, verticalLayout);

        signBtn.setMaxWidth(Double.MAX_VALUE);
        signBtn.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-font-weight: bold;");
        signBtn.setOnAction(e -> signDocument());

        VBox documentSection = createSection(
            "1. Elegí el documento",
            "Seleccioná el PDF que querés firmar.",
            selectBtn,
            fileInfoLabel
        );
        VBox signatureDataSection = createSection(
            "2. Revisá tus datos",
            "Confirmá el certificado, motivo y rol.",
            new Label("Motivo:"),
            reasonCombo,
            new Label("Cargo:"),
            roleField
        );
        VBox formatSection = createSection(
            "3. Elegí el formato",
            "Elegí horizontal o vertical y arrastrá la firma en el PDF.",
            new Label("Formato de estampilla:"),
            layoutChoices
        );
        VBox actionsSection = createSection(
            "4. Firmá tu documento",
            "La app guarda el archivo automáticamente.",
            signBtn,
            createWorkflowSection(),
            statusLabel
        );

        controls.getChildren().addAll(
            documentSection,
            new Separator(),
            signatureDataSection,
            new Separator(),
            formatSection,
            new Separator(),
            actionsSection
        );

        split.getItems().addAll(left, controls);

        HBox footer = new HBox(new Label("OTI — Subunidad de Gobierno Electrónico — UNA Puno"));
        footer.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 8px;");
        footer.setAlignment(Pos.CENTER);

        VBox root = new VBox(header, split, footer);
        VBox.setVgrow(split, Priority.ALWAYS);
        return root;
    }

    private VBox createSection(String title, String help, javafx.scene.Node... content) {
        Label sectionTitle = new Label(title);
        sectionTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #306080; -fx-font-size: 12px;");
        Label sectionHelp = new Label(help);
        sectionHelp.setWrapText(true);
        sectionHelp.setStyle("-fx-text-fill: #777; -fx-font-size: 10px;");
        VBox sectionHeader = new VBox(2, sectionTitle, sectionHelp);
        VBox section = new VBox(6);
        section.getChildren().add(sectionHeader);
        section.getChildren().addAll(content);
        return section;
    }

    private VBox createWorkflowSection() {
        Label title = new Label("PROCESO");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #555; -fx-font-size: 11px;");
        VBox workflow = new VBox(3, title, stepPdfLabel, stepPositionLabel, stepSignLabel, stepSaveLabel);
        updateWorkflow(0);
        return workflow;
    }

    private void updateWorkflow(int completedStep) {
        Label[] steps = {stepPdfLabel, stepPositionLabel, stepSignLabel, stepSaveLabel};
        for (int index = 0; index < steps.length; index++) {
            boolean completed = index < completedStep;
            boolean active = index == completedStep && completedStep < steps.length;
            String marker = completed ? "✓ " : active ? "● " : "○ ";
            String label = switch (index) {
                case 0 -> "PDF cargado";
                case 1 -> "Posicioná la firma";
                case 2 -> "Firmá con tu token";
                default -> "PDF guardado automáticamente";
            };
            steps[index].setText(marker + label);
            steps[index].setStyle(completed
                ? "-fx-text-fill: #198754;"
                : active
                    ? "-fx-text-fill: #306080; -fx-font-weight: bold;"
                    : "-fx-text-fill: #999;");
        }
    }

    private void updatePreviewHelp() {
        if (selectedStampLayout() == StampLayout.VERTICAL) {
            previewHelpLabel.setText("Arrastrá la estampilla vertical hasta la ubicación deseada. El logo queda arriba y el texto abajo.");
        } else {
            previewHelpLabel.setText("Arrastrá la estampilla horizontal hasta la ubicación deseada.");
        }
    }

    private ToggleButton createLayoutCard(String title, boolean vertical, StampLayout layout) {
        ToggleButton card = new ToggleButton(title);
        card.setToggleGroup(stampLayoutGroup);
        card.setUserData(layout);
        card.setContentDisplay(ContentDisplay.TOP);
        card.setGraphic(createMiniLayoutPreview(vertical));
        card.setPrefSize(130, 92);
        card.setMinSize(130, 92);
        card.setMaxSize(130, 92);
        card.setStyle(layoutCardStyle());
        return card;
    }

    private javafx.scene.Node createMiniLayoutPreview(boolean vertical) {
        Label logo = new Label("LOGO");
        logo.setStyle("-fx-background-color: #e7eef8; -fx-border-color: #8aa9cf; -fx-padding: 4px; -fx-font-size: 9px;");
        Label text = new Label("Texto\nFirma");
        text.setStyle("-fx-background-color: #f4f4f4; -fx-border-color: #bbb; -fx-padding: 3px; -fx-font-size: 8px;");

        if (vertical) {
            VBox preview = new VBox(3, logo, text);
            preview.setAlignment(Pos.CENTER);
            return preview;
        }

        HBox preview = new HBox(4, logo, text);
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    private String layoutCardStyle() {
        return "-fx-background-color: white; -fx-border-color: #c8c8c8; -fx-border-width: 1px; " +
            "-fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 6px;";
    }

    private String selectedLayoutCardStyle() {
        return "-fx-background-color: #eef5ff; -fx-border-color: #306080; -fx-border-width: 2px; " +
            "-fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 6px;";
    }

    // ---------- PDF loading & rendering ----------

    private void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            resetDocumentState();
            selectedPdf = file;
            updateDocumentChooserLabel();
            fileInfoLabel.setText("Archivo: " + file.getName() +
                "\nPáginas: cargando..." +
                "\nTamaño: " + formatFileSize(file.length()));
            statusLabel.setText("Cargando: " + file.getName());
            previewHelpLabel.setText("Cargando el PDF...");
            new Thread(() -> loadPdf(file)).start();
        }
    }

    private void resetDocumentState() {
        if (currentDoc != null) {
            try {
                currentDoc.close();
            } catch (Exception ignored) {
                // Closing an already closed document is harmless during a reset.
            }
            currentDoc = null;
        }
        selectedPdf = null;
        cert = null;
        privateKey = null;
        certChain = null;
        currentPage = 0;
        totalPages = 1;
        pageImage = null;
        pageLabel.setText("Página 1 de 1");
        updatePageNavigation();
        fileInfoLabel.setText("Ningún documento seleccionado.");
        statusLabel.setText("Seleccione un PDF para firmar.");
        previewHelpLabel.setText("Seleccioná un PDF para comenzar.");
        updateWorkflow(0);
        signBtn.setDisable(false);
        updateDocumentChooserLabel();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.setFill(Color.web("#e8e8e8"));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    private void loadPdf(File file) {
        loadPdf(file, () -> { }, () -> { });
    }

    private void loadPdf(File file, Runnable onLoaded, Runnable onLoadError) {
        try {
            if (currentDoc != null) currentDoc.close();
            currentDoc = Loader.loadPDF(file);
            totalPages = currentDoc.getNumberOfPages();
            Platform.runLater(() -> {
                pageLabel.setText("Página 1 de " + totalPages);
                updatePageNavigation();
                fileInfoLabel.setText("Archivo: " + file.getName() +
                    "\nPáginas: " + totalPages +
                    "\nTamaño: " + formatFileSize(file.length()));
                renderPage();
                updateWorkflow(1);
                statusLabel.setText("PDF cargado. Arrastre el recuadro y presione Firmar.");
                updatePreviewHelp();
                onLoaded.run();
            });
        } catch (Exception e) {
            Platform.runLater(() -> {
                statusLabel.setText("Error al cargar PDF: " + e.getMessage());
                onLoadError.run();
            });
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void prevPage() {
        if (currentPage > 0) {
            currentPage--;
            pageLabel.setText("Página " + (currentPage + 1) + " de " + totalPages);
            updatePageNavigation();
            renderPage();
        }
    }

    private void nextPage() {
        if (currentPage < totalPages - 1) {
            currentPage++;
            pageLabel.setText("Página " + (currentPage + 1) + " de " + totalPages);
            updatePageNavigation();
            renderPage();
        }
    }

    private void updatePageNavigation() {
        if (prevPageBtn != null) {
            prevPageBtn.setDisable(currentPage == 0);
            nextPageBtn.setDisable(currentPage >= totalPages - 1);
        }
    }

    private void renderPage() {
        new Thread(() -> {
            try {
                PDPage page = currentDoc.getPage(currentPage);
                PDRectangle cb = page.getCropBox();
                pdfPageW = cb.getWidth();
                pdfPageH = cb.getHeight();
                cropLlx = cb.getLowerLeftX();
                cropLly = cb.getLowerLeftY();

                PDFRenderer renderer = new PDFRenderer(currentDoc);
                BufferedImage bi = renderer.renderImage(currentPage, (float) renderScale);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bi, "png", baos);
                Image fxImage = new Image(new ByteArrayInputStream(baos.toByteArray()));

                double sw = canvas.getWidth() / fxImage.getWidth();
                double sh = canvas.getHeight() / fxImage.getHeight();
                canvasScale = Math.min(sw, sh);
                double w = fxImage.getWidth() * canvasScale;
                double h = fxImage.getHeight() * canvasScale;
                offsetX = (canvas.getWidth() - w) / 2;
                offsetY = (canvas.getHeight() - h) / 2;
                pdfToCanvasScaleX = w / pdfPageW;
                pdfToCanvasScaleY = h / pdfPageH;

                updateBoxDimensions();

                // Default position: bottom-right of the visible page (resets per page)
                if (!dragging) {
                    boxX = offsetX + (pdfPageW - STAMP_SAFE_MARGIN_PT) * pdfToCanvasScaleX - boxWcanvas;
                    boxY = offsetY + (pdfPageH - STAMP_SAFE_MARGIN_PT) * pdfToCanvasScaleY - boxHcanvas;
                }
                clampBoxToPage();

                pageImage = fxImage;
                double imgW = w, imgH = h;
                Platform.runLater(() -> {
                    gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
                    gc.setFill(Color.web("#e8e8e8"));
                    gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
                    gc.drawImage(fxImage, offsetX, offsetY, imgW, imgH);
                    drawBox();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
                    gc.setStroke(Color.RED);
                    gc.strokeText("Error: " + e.getMessage(), 20, 50);
                });
            }
        }).start();
    }

    private void drawBox() {
        StampLayout layout = selectedStampLayout();
        double stampScale = boxWcanvas / layout.getWidth();
        double inset = PREVIEW_BOX_INSET_PT * stampScale;

        gc.setFill(Color.rgb(30, 144, 255, 0.15));
        gc.fillRect(boxX + inset, boxY + inset,
            boxWcanvas - inset * 2, boxHcanvas - inset * 2);
        drawStampPreview();
        gc.setStroke(Color.DODGERBLUE);
        gc.setLineWidth(1.5);
        gc.strokeRect(boxX + inset, boxY + inset,
            boxWcanvas - inset * 2, boxHcanvas - inset * 2);
    }

    private void redrawPreviewIfAvailable() {
        if (pageImage != null) {
            redrawBoxOnly();
        }
    }

    private void drawStampPreview() {
        if (stampPreviewLogo.isError()) {
            return;
        }

        StampLayout layout = selectedStampLayout();
        double stampScale = boxWcanvas / layout.getWidth();
        double logoAreaWidth = layout == StampLayout.HORIZONTAL ? PDFSigner.HORIZONTAL_LOGO_AREA_WIDTH_PT
            : layout.getWidth() - PDFSigner.VERTICAL_CONTENT_HORIZONTAL_PADDING_PT * 2;
        double logoAreaHeight = layout == StampLayout.HORIZONTAL ? layout.getHeight()
            : PDFSigner.VERTICAL_LOGO_AREA_HEIGHT_PT;
        double logoRatio = stampPreviewLogo.getWidth() / stampPreviewLogo.getHeight();
        double logoWidth = logoAreaWidth;
        double logoHeight = logoWidth / logoRatio;

        if (logoHeight > logoAreaHeight) {
            logoHeight = logoAreaHeight;
            logoWidth = logoHeight * logoRatio;
        }

        double logoPdfX = layout == StampLayout.HORIZONTAL
            ? (logoAreaWidth - logoWidth) / 2
            : PDFSigner.VERTICAL_CONTENT_HORIZONTAL_PADDING_PT;
        double logoPdfY = layout == StampLayout.HORIZONTAL
            ? (layout.getHeight() - logoHeight) / 2
            : layout.getHeight() - PDFSigner.VERTICAL_LOGO_TOP_PADDING_PT - logoHeight;
        double logoCanvasX = boxX + logoPdfX * stampScale;
        double logoCanvasY = boxY + (layout.getHeight() - (logoPdfY + logoHeight)) * stampScale;

        gc.save();
        gc.beginPath();
        gc.rect(boxX, boxY, boxWcanvas, boxHcanvas);
        gc.closePath();
        gc.clip();

        gc.drawImage(stampPreviewLogo, logoCanvasX, logoCanvasY,
            logoWidth * stampScale, logoHeight * stampScale);

        gc.setFill(Color.BLACK);
        java.util.List<PDFSigner.StampTextLine> textLines = previewStampLines(layout);
        double baseline = layout == StampLayout.HORIZONTAL
            ? PDFSigner.horizontalTextBaseline(0, layout, textLines)
            : PDFSigner.verticalTextBaseline(0, (float) logoPdfY, layout, textLines.size(),
                textLines.get(textLines.size() - 1).fontSize());
        for (PDFSigner.StampTextLine line : textLines) {
            double textX = layout == StampLayout.HORIZONTAL ? PDFSigner.HORIZONTAL_TEXT_X_PT
                : PDFSigner.VERTICAL_CONTENT_HORIZONTAL_PADDING_PT;
            gc.setFont(Font.font("Helvetica", line.fontSize() * stampScale));
            gc.fillText(line.text(), boxX + textX * stampScale,
                boxY + (layout.getHeight() - baseline) * stampScale);
            baseline -= layout.getLineLeading();
        }
        gc.restore();
    }

    private java.util.List<PDFSigner.StampTextLine> previewStampLines(StampLayout layout) {
        String signerName = cert == null
            ? "Nombre del firmante"
            : extractCN(cert.getSubjectX500Principal().getName()).replaceFirst("\\s+(?=FAU\\b)", "\n");
        String reason = reasonCombo.getValue();
        String role = roleField.getText().trim();

        String signerText = "Firmado digitalmente por:\n" + signerName +
            "\nMotivo: " + reason +
            (role.isEmpty() ? "" : "\n" + role) +
            "\nFecha: " + ZonedDateTime.now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'XXX"));
        double maxWidth = layout == StampLayout.HORIZONTAL ? PDFSigner.HORIZONTAL_TEXT_MAX_WIDTH_PT
            : layout.getWidth() - PDFSigner.VERTICAL_CONTENT_HORIZONTAL_PADDING_PT * 2;
        try {
            return PDFSigner.layoutText(signerText, layout, (float) maxWidth);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Unable to layout stamp text", e);
        }
    }

    private void redrawBoxOnly() {
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.setFill(Color.web("#e8e8e8"));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        if (pageImage != null) {
            gc.drawImage(pageImage, offsetX, offsetY,
                pageImage.getWidth() * canvasScale, pageImage.getHeight() * canvasScale);
        }
        drawBox();
    }

    private StampLayout selectedStampLayout() {
        return stampLayout;
    }

    private void updateStampLayout() {
        double previousBoxWidth = boxWcanvas;
        updateBoxDimensions();
        if (pageImage != null) {
            if (selectedStampLayout() == StampLayout.VERTICAL && boxWcanvas < previousBoxWidth) {
                boxX += previousBoxWidth - boxWcanvas;
            }
            clampBoxToPage();
            redrawBoxOnly();
        }
    }

    private void updateBoxDimensions() {
        StampLayout layout = selectedStampLayout();
        boxWcanvas = layout.getWidth() * pdfToCanvasScaleX;
        boxHcanvas = layout.getHeight() * pdfToCanvasScaleY;
    }

    private void clampBoxToPage() {
        if (pdfPageW <= 0 || pdfPageH <= 0) {
            return;
        }

        double minX = offsetX + STAMP_SAFE_MARGIN_PT * pdfToCanvasScaleX;
        double minY = offsetY + (STAMP_SAFE_MARGIN_PT + stampTopOverflowPt()) * pdfToCanvasScaleY;
        double maxX = offsetX + (pdfPageW - STAMP_SAFE_MARGIN_PT) * pdfToCanvasScaleX - boxWcanvas;
        double maxY = offsetY + (pdfPageH - STAMP_SAFE_MARGIN_PT) * pdfToCanvasScaleY - boxHcanvas;
        boxX = clamp(boxX, minX, maxX);
        boxY = clamp(boxY, minY, maxY);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    private double stampTopOverflowPt() {
        return selectedStampLayout() == StampLayout.HORIZONTAL ? HORIZONTAL_LOGO_TOP_OVERFLOW_PT : 0;
    }

    // ---------- Dragging the signature box ----------

    private void setupCanvasDrag() {
        canvas.setOnMousePressed(e -> {
            double mx = e.getX();
            double my = e.getY();
            if (mx >= boxX && mx <= boxX + boxWcanvas &&
                my >= boxY && my <= boxY + boxHcanvas) {
                dragging = true;
                dragStartX = mx - boxX;
                dragStartY = my - boxY;
            } else {
                dragging = false;
                boxX = mx - boxWcanvas / 2;
                boxY = my - boxHcanvas / 2;
                clampBoxToPage();
                redrawBoxOnly();
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (dragging) {
                boxX = e.getX() - dragStartX;
                boxY = e.getY() - dragStartY;
                clampBoxToPage();
                redrawBoxOnly();
            }
        });

        canvas.setOnMouseReleased(e -> dragging = false);
    }

    // Convert the on-screen box to a PDF rectangle (bottom-left anchored, CropBox aware)
    private double[] getPdfRect() {
        double ixLeft = (boxX - offsetX) / pdfToCanvasScaleX;
        double iyBottom = ((boxY + boxHcanvas) - offsetY) / pdfToCanvasScaleY;
        StampLayout layout = selectedStampLayout();
        double pdfX = clamp(cropLlx + ixLeft,
            cropLlx + STAMP_SAFE_MARGIN_PT,
            cropLlx + pdfPageW - layout.getWidth() - STAMP_SAFE_MARGIN_PT);
        double pdfYbottom = clamp(cropLly + pdfPageH - iyBottom,
            cropLly + STAMP_SAFE_MARGIN_PT,
            cropLly + pdfPageH - layout.getHeight() - STAMP_SAFE_MARGIN_PT - stampTopOverflowPt());
        return new double[]{pdfX, pdfYbottom, layout.getWidth(), layout.getHeight()};
    }

    // ---------- Signing ----------

    private void signDocument() {
        if (selectedPdf == null) {
            showAlert("Primero seleccione un archivo PDF.");
            return;
        }
        SigningRequest request = new SigningRequest(
            selectedPdf,
            reasonCombo.getSelectionModel().getSelectedItem(),
            roleField.getText().trim(),
            currentPage,
            getPdfRect(),
            selectedStampLayout()
        );
        updateWorkflow(2);
        previewHelpLabel.setText("Validá el PIN del token para completar la firma.");
        openPinModal(request);
    }

    private void openPinModal(SigningRequest request) {
        Stage modal = new Stage();
        modal.initModality(Modality.APPLICATION_MODAL);
        modal.initOwner(stage);
        modal.setTitle("PIN del Token");

        PasswordField pinField = new PasswordField();
        pinField.setPromptText("PIN de su token Bit4id");

        Button okBtn = new Button("Firmar");
        okBtn.setStyle("-fx-background-color: #198754; -fx-text-fill: white;");
        Button cancelBtn = new Button("Cancelar");
        cancelBtn.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white;");

        AtomicBoolean pinSubmitted = new AtomicBoolean(false);
        okBtn.setOnAction(e -> {
            String pin = pinField.getText();
            if (!pin.isBlank()) {
                pinSubmitted.set(true);
                modal.close();
                doLoginAndChooseCertificate(pin.trim(), request);
            }
        });
        cancelBtn.setOnAction(e -> modal.close());
        modal.setOnHidden(e -> {
            if (!pinSubmitted.get()) {
                restoreAfterSigningCancellation();
            }
        });

        HBox buttons = new HBox(10, cancelBtn, okBtn);
        buttons.setAlignment(Pos.CENTER);
        VBox root = new VBox(10, new Label("Ingrese el PIN de su token:"), pinField, buttons);
        root.setPadding(new Insets(15));
        root.setAlignment(Pos.CENTER);
        modal.setScene(new Scene(root, 320, 150));
        modal.showAndWait();
    }

    private void doLoginAndChooseCertificate(String pin, SigningRequest request) {
        statusLabel.setText("Conectando con el token...");
        signBtn.setDisable(true);
        selectBtn.setDisable(true);

        Thread worker = new Thread(() -> {
            TokenProvider provider = new TokenProvider();
            try {
                provider.login(pin);
                List<TokenProvider.CertificateChoice> choices = provider.getCertificateChoices();
                Platform.runLater(() -> openCertificateSelectionModal(provider, choices, request));
            } catch (Exception e) {
                provider.logout();
                Platform.runLater(() -> {
                    statusLabel.setText("Error de token: " + e.getMessage());
                    signBtn.setDisable(false);
                    selectBtn.setDisable(false);
                });
            }
        });
        worker.setDaemon(true);
        worker.start();

        Thread timeoutGuard = new Thread(() -> {
            try {
                worker.join(25_000);
            } catch (InterruptedException ignored) {}
            if (worker.isAlive()) {
                Platform.runLater(() -> {
                    statusLabel.setText("Timeout: token no respondió. Verifique conexión e intente de nuevo.");
                    signBtn.setDisable(false);
                    selectBtn.setDisable(false);
                });
                worker.interrupt();
            }
        });
        timeoutGuard.setDaemon(true);
        timeoutGuard.start();
    }

    private void openCertificateSelectionModal(TokenProvider provider,
                                                List<TokenProvider.CertificateChoice> choices,
                                                SigningRequest request) {
        Stage modal = new Stage();
        modal.initModality(Modality.APPLICATION_MODAL);
        modal.initOwner(stage);
        modal.setTitle("Elegí tu certificado");

        ToggleGroup certificateGroup = new ToggleGroup();
        VBox certificateList = new VBox(10);
        for (int index = 0; index < choices.size(); index++) {
            TokenProvider.CertificateChoice choice = choices.get(index);
            RadioButton option = new RadioButton(extractCN(choice.certificate().getSubjectX500Principal().getName()));
            option.setToggleGroup(certificateGroup);
            option.setUserData(choice);
            option.setSelected(index == 0);
            option.setStyle("-fx-font-weight: bold;");

            Label details = new Label(
                "Titular: " + choice.certificate().getSubjectX500Principal().getName() +
                "\nEmisor: " + choice.certificate().getIssuerX500Principal().getName() +
                "\nVence: " + formatCertificateDate(choice.certificate()) +
                "\nEstado: " + certificateStatus(choice.certificate())
            );
            details.setWrapText(true);
            details.setStyle("-fx-text-fill: #555; -fx-font-size: 11px;");
            VBox card = new VBox(4, option, details);
            card.setPadding(new Insets(10));
            card.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #c8c8c8; -fx-border-radius: 4px;");
            certificateList.getChildren().add(card);
        }

        Button signWithCertificateBtn = new Button("Firmar con este certificado");
        signWithCertificateBtn.setStyle("-fx-background-color: #198754; -fx-text-fill: white;");
        Button cancelBtn = new Button("Cancelar");
        AtomicBoolean certificateSelected = new AtomicBoolean(false);
        signWithCertificateBtn.setOnAction(e -> {
            RadioButton selected = (RadioButton) certificateGroup.getSelectedToggle();
            if (selected == null) {
                return;
            }
            try {
                TokenProvider.CertificateChoice choice = (TokenProvider.CertificateChoice) selected.getUserData();
                provider.selectKeyAlias(choice.alias());
                certificateSelected.set(true);
                modal.close();
                performSigning(provider, request);
            } catch (Exception exception) {
                statusLabel.setText("Error al seleccionar certificado: " + exception.getMessage());
            }
        });
        cancelBtn.setOnAction(e -> modal.close());
        modal.setOnHidden(e -> {
            if (!certificateSelected.get()) {
                provider.logout();
                restoreAfterSigningCancellation();
            }
        });

        HBox actions = new HBox(10, cancelBtn, signWithCertificateBtn);
        actions.setAlignment(Pos.CENTER);
        VBox root = new VBox(14, new Label("Elegí el certificado con el que querés firmar:"), certificateList, actions);
        root.setPadding(new Insets(18));
        modal.setScene(new Scene(root, 640, Math.min(220 + choices.size() * 130, 700)));
        modal.showAndWait();
    }

    private String formatCertificateDate(X509Certificate certificate) {
        return DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(certificate.getNotAfter().toInstant());
    }

    private String certificateStatus(X509Certificate certificate) {
        Instant now = Instant.now();
        Instant expiration = certificate.getNotAfter().toInstant();
        if (expiration.isBefore(now)) {
            return "Vencido";
        }
        return expiration.isBefore(now.plusSeconds(30L * 24 * 60 * 60)) ? "Por vencer" : "Válido";
    }

    private void restoreAfterSigningCancellation() {
        updateWorkflow(1);
        previewHelpLabel.setText("La firma fue cancelada.");
        statusLabel.setText("Firma cancelada.");
        signBtn.setDisable(false);
        selectBtn.setDisable(false);
    }

    private void performSigning(TokenProvider provider, SigningRequest request) {
        Thread signing = new Thread(() -> {
            try {
                cert = provider.getCertificate();
                privateKey = provider.getPrivateKey();
                certChain = provider.getCertificateChain();
                String cn = extractCN(cert.getSubjectX500Principal().getName());
                Platform.runLater(() -> {
                    statusLabel.setText("Token verificado: " + cn);
                    redrawPreviewIfAvailable();
                });
                String displayName = cn.replaceFirst("\\s+(?=FAU\\b)", "\n");

                String signerText = "Firmado digitalmente por:\n" + displayName +
                    "\nMotivo: " + request.reason() +
                    (request.role().isEmpty() ? "" : "\n" + request.role()) +
                    "\nFecha: " + ZonedDateTime.now(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'XXX"));

                Path outputDirectory = request.inputPdf().toPath().toAbsolutePath().getParent();
                Path temporaryOutput = Files.createTempFile(outputDirectory, ".firmauna-", ".pdf");
                try {
                    PDFSigner.signPDF(
                    request.inputPdf(), temporaryOutput.toFile(),
                    cert, privateKey, certChain,
                    request.reason(), "Puno, Per\u00fa", cn,
                    signerText, request.page(),
                    (float) request.rectangle()[0], (float) request.rectangle()[1], request.layout()
                    );
                    Path savedOutput = moveToNextSignedOutput(temporaryOutput, request.inputPdf());
                    Platform.runLater(() -> loadSavedSignedPdf(savedOutput.toFile()));
                } finally {
                    Files.deleteIfExists(temporaryOutput);
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error de firma: " + e.getMessage());
                    signBtn.setDisable(false);
                    selectBtn.setDisable(false);
                    e.printStackTrace();
                });
            } finally {
                provider.logout();
            }
        });
        signing.setDaemon(true);
        signing.start();
    }

    private void loadSavedSignedPdf(File savedPdf) {
        resetDocumentState();
        selectedPdf = savedPdf;
        updateDocumentChooserLabel();
        fileInfoLabel.setText("Archivo: " + savedPdf.getName() + "\nPáginas: cargando..." +
            "\nTamaño: " + formatFileSize(savedPdf.length()));
        statusLabel.setText("Abriendo documento firmado: " + savedPdf.getName());
        previewHelpLabel.setText("Abriendo el PDF firmado...");
        loadPdf(savedPdf, () -> {
            updateWorkflow(4);
            previewHelpLabel.setText("El documento firmado está abierto y listo para firmarse nuevamente.");
            statusLabel.setText("Documento firmado y guardado: " + savedPdf.getName());
            signBtn.setDisable(false);
            selectBtn.setDisable(false);
            showSignedDocumentModal(savedPdf);
        }, () -> {
            signBtn.setDisable(false);
            selectBtn.setDisable(false);
        });
    }

    private Path moveToNextSignedOutput(Path temporaryOutput, File inputPdf) throws java.io.IOException {
        Path inputPath = inputPdf.toPath().toAbsolutePath();
        Path directory = inputPath.getParent();
        String fileName = inputPath.getFileName().toString();
        String stem = fileName.replaceFirst("(?i)\\.pdf$", "")
            .replaceFirst(" (?:\\d+ )?\\[FU]$", "");

        for (int number = 1; ; number++) {
            String suffix = number == 1 ? " [FU].pdf" : " " + number + " [FU].pdf";
            Path output = directory.resolve(stem + suffix);
            try {
                return Files.move(temporaryOutput, output);
            } catch (FileAlreadyExistsException ignored) {
                // Try the next sequence number without replacing an existing signed file.
            }
        }
    }

    private void updateDocumentChooserLabel() {
        selectBtn.setText(selectedPdf == null ? "Seleccionar PDF..." : "Abrir otro PDF...");
    }

    private void showSignedDocumentModal(File savedPdf) {
        Stage modal = new Stage();
        modal.initModality(Modality.APPLICATION_MODAL);
        modal.initOwner(stage);
        modal.setTitle("Documento firmado");
        modal.setResizable(false);

        Label successIcon = new Label("✔");
        successIcon.setStyle(
            "-fx-text-fill: white; -fx-background-color: #198754; -fx-background-radius: 30px;" +
            " -fx-alignment: center; -fx-min-width: 60px; -fx-max-width: 60px;" +
            " -fx-min-height: 60px; -fx-max-height: 60px; -fx-font-size: 28px;" +
            " -fx-font-weight: bold;");

        Label heading = new Label("Tu documento fue firmado");
        heading.setStyle("-fx-text-fill: #198754; -fx-font-weight: bold; -fx-font-size: 18px;");

        Label fileNameLabel = new Label(savedPdf.getName());
        fileNameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        fileNameLabel.setWrapText(true);
        fileNameLabel.setMaxWidth(380);
        fileNameLabel.setAlignment(Pos.CENTER);

        Label details = new Label(
            "Guardado en la misma carpeta del original\ny ya está abierto en la app para firmar de nuevo.");
        details.setWrapText(true);
        details.setStyle("-fx-text-fill: #555; -fx-font-size: 12px;");
        details.setTextAlignment(TextAlignment.CENTER);

        Button signAgainBtn = new Button("✔ Volver a firmar");
        signAgainBtn.setStyle("-fx-background-color: #198754; -fx-text-fill: white; -fx-font-weight: bold;");
        signAgainBtn.setOnAction(e -> modal.close());
        Button showInFinderBtn = new Button("📂 Ver ubicación");
        showInFinderBtn.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white;");
        Label finderMessage = new Label();
        finderMessage.setWrapText(true);
        finderMessage.setStyle("-fx-text-fill: #a94442; -fx-font-size: 11px;");
        showInFinderBtn.setOnAction(e -> revealInFinder(savedPdf, finderMessage));

        HBox actions = new HBox(10, showInFinderBtn, signAgainBtn);
        actions.setAlignment(Pos.CENTER);

        Label tip = new Label("Consejo: podés firmar este PDF varias veces si necesitás más firmas.");
        tip.setWrapText(true);
        tip.setStyle("-fx-text-fill: #999; -fx-font-size: 11px;");
        tip.setAlignment(Pos.CENTER);
        tip.setTextAlignment(TextAlignment.CENTER);

        VBox root = new VBox(10, successIcon, heading, fileNameLabel, details,
            actions, finderMessage, new Separator(), tip);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);
        modal.setScene(new Scene(root, 430, 340));
        modal.showAndWait();
    }

    private void revealInFinder(File savedPdf, Label finderMessage) {
        if (!System.getProperty("os.name", "").startsWith("Mac")) {
            finderMessage.setText("Finder solo está disponible en macOS.");
            return;
        }

        Path savedPath = savedPdf.toPath().toAbsolutePath();
        Thread finder = new Thread(() -> {
            try {
                Process process = new ProcessBuilder("open", "-R", savedPath.toString()).start();
                if (process.waitFor() != 0) {
                    Platform.runLater(() -> finderMessage.setText("No se pudo mostrar el archivo en Finder."));
                }
            } catch (Exception e) {
                Platform.runLater(() -> finderMessage.setText("No se pudo mostrar el archivo en Finder."));
            }
        });
        finder.setDaemon(true);
        finder.start();
    }

    private record SigningRequest(File inputPdf, String reason, String role, int page,
                                  double[] rectangle, StampLayout layout) { }

    private String extractCN(String dn) {
        for (String part : dn.split(",")) {
            String p = part.trim();
            if (p.startsWith("CN=")) return p.substring(3);
        }
        return dn;
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Atención");
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
