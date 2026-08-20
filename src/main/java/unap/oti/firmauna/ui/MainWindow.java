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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
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
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class MainWindow {

    private final Stage stage;
    private final Label statusLabel = new Label("Seleccione un PDF para firmar.");
    private final ComboBox<String> reasonCombo = new ComboBox<>();
    private final TextField roleField = new TextField();
    private final ToggleGroup stampLayoutGroup = new ToggleGroup();
    private volatile StampLayout stampLayout = StampLayout.HORIZONTAL;
    private final Button signBtn = new Button("Firmar mi PDF");
    private final Button saveBtn = new Button("Guardar PDF firmado");
    private final Label pageLabel = new Label("Página 1 de 1");
    private Button prevPageBtn;
    private Button nextPageBtn;
    private final Label fileInfoLabel = new Label("Ningún documento seleccionado.");
    private final Label stepPdfLabel = new Label("○ PDF cargado");
    private final Label stepPositionLabel = new Label("○ Posicioná la firma");
    private final Label stepSignLabel = new Label("○ Firmá con tu token");
    private final Label stepSaveLabel = new Label("○ Guardá el documento");
    private final Label previewHelpLabel = new Label("Seleccioná un PDF para comenzar.");

    // PDF preview canvas + draggable signature box (integrated, no separate dialog)
    // Leave room for page navigation and contextual help below the preview.
    private final Canvas canvas = new Canvas(560, 560);
    private final GraphicsContext gc = canvas.getGraphicsContext2D();

    private File selectedPdf;
    private File signedPdf;
    private PDDocument currentDoc;
    private X509Certificate cert;
    private PrivateKey privateKey;
    private java.security.cert.Certificate[] certChain;

    private int currentPage = 0;
    private int totalPages = 1;
    private boolean signed = false;

    // Box + render transform
    private double boxX = 40, boxY = 40;
    private double dragStartX, dragStartY;
    private boolean dragging;
    private double canvasScale = 1.0;
    private double offsetX = 0, offsetY = 0;
    private final double renderScale = 1.5;
    private double pdfPageW = 0, pdfPageH = 0;
    private double cropLlx = 0, cropLly = 0;
    // Visual-only inset. The real signing rectangle remains unchanged.
    private static final double PREVIEW_BOX_INSET_PT = 5.0;
    private double boxWcanvas = StampLayout.HORIZONTAL.getWidth(), boxHcanvas = StampLayout.HORIZONTAL.getHeight();
    private Image pageImage;
    private final Image stampPreviewLogo = new Image(
        MainWindow.class.getResourceAsStream("/assets/LogoUNA.png"));

    public MainWindow(Stage stage) {
        this.stage = stage;
        stage.setTitle("FirmaUNA 2.0");
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
        saveBtn.setDisable(true);

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

        Label title = new Label("firmaUNA 2.0");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");
        Label subtitleLbl = new Label("Firma Digital · Token RENIEC · UNA Puno");
        subtitleLbl.setStyle("-fx-text-fill: #b0c4de; -fx-font-size: 11px;");
        header.getChildren().addAll(new VBox(2, title, subtitleLbl));

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

        Button selectBtn = new Button("Seleccionar PDF...");
        selectBtn.setMaxWidth(Double.MAX_VALUE);
        selectBtn.setOnAction(e -> openFile());

        fileInfoLabel.setWrapText(true);
        fileInfoLabel.setStyle("-fx-text-fill: #555; -fx-font-size: 11px;");

        Button newDocumentBtn = new Button("Nuevo documento");
        newDocumentBtn.setMaxWidth(Double.MAX_VALUE);
        newDocumentBtn.setOnAction(e -> newDocument());

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

        saveBtn.setMaxWidth(Double.MAX_VALUE);
        saveBtn.setStyle("-fx-background-color: #198754; -fx-text-fill: white; -fx-font-weight: bold;");
        saveBtn.setOnAction(e -> saveDocument());

        HBox actionButtons = new HBox(8, signBtn, saveBtn);
        actionButtons.setFillHeight(true);
        HBox.setHgrow(signBtn, Priority.ALWAYS);
        HBox.setHgrow(saveBtn, Priority.ALWAYS);
        signBtn.setMaxWidth(Double.MAX_VALUE);
        saveBtn.setMaxWidth(Double.MAX_VALUE);

        VBox documentSection = createSection(
            "DOCUMENTO",
            selectBtn,
            fileInfoLabel,
            newDocumentBtn
        );
        VBox signatureDataSection = createSection(
            "DATOS DE FIRMA",
            new Label("Motivo:"),
            reasonCombo,
            new Label("Cargo:"),
            roleField
        );
        VBox formatSection = createSection(
            "FORMATO",
            new Label("Formato de estampilla:"),
            layoutChoices
        );
        VBox actionsSection = createSection(
            "ACCIONES",
            actionButtons,
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

    private VBox createSection(String title, javafx.scene.Node... content) {
        Label sectionTitle = new Label(title);
        sectionTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #306080; -fx-font-size: 12px;");
        VBox section = new VBox(6);
        section.getChildren().add(sectionTitle);
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
                default -> "Guardá el documento";
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

    private void newDocument() {
        if (signed) {
            showAlert("Este documento ya fue firmado. Seleccione otro PDF para iniciar un nuevo documento.");
        }
        openFile();
    }

    private void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            resetDocumentState();
            selectedPdf = file;
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
        signedPdf = null;
        cert = null;
        privateKey = null;
        certChain = null;
        signed = false;
        currentPage = 0;
        totalPages = 1;
        pageImage = null;
        pageLabel.setText("Página 1 de 1");
        updatePageNavigation();
        fileInfoLabel.setText("Ningún documento seleccionado.");
        statusLabel.setText("Seleccione un PDF para firmar.");
        previewHelpLabel.setText("Seleccioná un PDF para comenzar.");
        updateWorkflow(0);
        saveBtn.setDisable(true);
        signBtn.setDisable(false);
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.setFill(Color.web("#e8e8e8"));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    private void loadPdf(File file) {
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
            });
        } catch (Exception e) {
            Platform.runLater(() -> statusLabel.setText("Error al cargar PDF: " + e.getMessage()));
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

                updateBoxDimensions();

                // Default position: bottom-right of the page (resets per page)
                if (!dragging) {
                    boxX = canvas.getWidth() - boxWcanvas - 12;
                    boxY = canvas.getHeight() - boxHcanvas - 12;
                }

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
        double logoAreaWidth = layout == StampLayout.HORIZONTAL ? 65 : layout.getWidth() - 10;
        double logoAreaHeight = layout == StampLayout.HORIZONTAL ? layout.getHeight() - 10 : 45;
        double logoRatio = stampPreviewLogo.getWidth() / stampPreviewLogo.getHeight();
        double logoWidth = logoAreaWidth;
        double logoHeight = logoWidth / logoRatio;

        if (logoHeight > logoAreaHeight) {
            logoHeight = logoAreaHeight;
            logoWidth = logoHeight * logoRatio;
        }

        double logoPdfX = layout == StampLayout.HORIZONTAL
            ? 5 + (logoAreaWidth - logoWidth) / 2
            : 5;
        double logoPdfY = layout == StampLayout.HORIZONTAL
            ? (layout.getHeight() - logoHeight) / 2 + 8
            : layout.getHeight() - 5 - logoAreaHeight + (logoAreaHeight - logoHeight) / 2;
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
        gc.setFont(Font.font("Helvetica", layout.getFontSize() * stampScale));
        double baseline = layout == StampLayout.HORIZONTAL ? layout.getHeight() - 12 : 64;
        for (String line : previewStampLines(layout)) {
            double textX = layout == StampLayout.HORIZONTAL ? 75 : 5;
            gc.fillText(line, boxX + textX * stampScale,
                boxY + (layout.getHeight() - baseline) * stampScale);
            baseline -= layout.getLineLeading();
        }
        gc.restore();
    }

    private java.util.List<String> previewStampLines(StampLayout layout) {
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
        return wrapPreviewLines(signerText, layout == StampLayout.HORIZONTAL ? 140 : 130,
            layout.getFontSize());
    }

    private java.util.List<String> wrapPreviewLines(String text, double maxWidth, double fontSize) {
        Font font = Font.font("Helvetica", fontSize);
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String line : text.split("\\n")) {
            StringBuilder current = new StringBuilder();
            for (String word : line.split(" ")) {
                String candidate = current.length() == 0 ? word : current + " " + word;
                Text measure = new Text(candidate);
                measure.setFont(font);
                if (measure.getLayoutBounds().getWidth() > maxWidth && current.length() > 0) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            if (current.length() > 0) {
                lines.add(current.toString());
            }
        }
        return lines;
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
        updateBoxDimensions();
        if (pageImage != null) {
            boxX = Math.min(boxX, canvas.getWidth() - boxWcanvas);
            boxY = Math.min(boxY, canvas.getHeight() - boxHcanvas);
            redrawBoxOnly();
        }
    }

    private void updateBoxDimensions() {
        StampLayout layout = selectedStampLayout();
        boxWcanvas = layout.getWidth() * renderScale * canvasScale;
        boxHcanvas = layout.getHeight() * renderScale * canvasScale;
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
                boxX = Math.max(0, Math.min(mx - boxWcanvas / 2, canvas.getWidth() - boxWcanvas));
                boxY = Math.max(0, Math.min(my - boxHcanvas / 2, canvas.getHeight() - boxHcanvas));
                redrawBoxOnly();
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (dragging) {
                boxX = Math.max(0, Math.min(e.getX() - dragStartX, canvas.getWidth() - boxWcanvas));
                boxY = Math.max(0, Math.min(e.getY() - dragStartY, canvas.getHeight() - boxHcanvas));
                redrawBoxOnly();
            }
        });

        canvas.setOnMouseReleased(e -> dragging = false);
    }

    // Convert the on-screen box to a PDF rectangle (bottom-left anchored, CropBox aware)
    private double[] getPdfRect() {
        double ixLeft = (boxX - offsetX) / (canvasScale * renderScale);
        double iyBottom = ((boxY + boxHcanvas) - offsetY) / (canvasScale * renderScale);
        double pdfX = cropLlx + ixLeft;
        double pdfYbottom = cropLly + pdfPageH - iyBottom;
        StampLayout layout = selectedStampLayout();
        return new double[]{pdfX, pdfYbottom, layout.getWidth(), layout.getHeight()};
    }

    // ---------- Signing ----------

    private void signDocument() {
        if (selectedPdf == null) {
            showAlert("Primero seleccione un archivo PDF.");
            return;
        }
        if (signed) {
            showAlert("Este documento ya fue firmado. Para volver a firmar, seleccione el PDF nuevamente.");
            return;
        }
        updateWorkflow(2);
        previewHelpLabel.setText("Validá el PIN del token para completar la firma.");
        openPinModal();
    }

    private void openPinModal() {
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

        okBtn.setOnAction(e -> {
            String pin = pinField.getText();
            if (!pin.isBlank()) {
                modal.close();
                doLoginAndSign(pin.trim());
            }
        });
        cancelBtn.setOnAction(e -> modal.close());

        HBox buttons = new HBox(10, cancelBtn, okBtn);
        buttons.setAlignment(Pos.CENTER);
        VBox root = new VBox(10, new Label("Ingrese el PIN de su token:"), pinField, buttons);
        root.setPadding(new Insets(15));
        root.setAlignment(Pos.CENTER);
        modal.setScene(new Scene(root, 320, 150));
        modal.showAndWait();
    }

    private void doLoginAndSign(String pin) {
        statusLabel.setText("Conectando con el token...");
        signBtn.setDisable(true);

        Thread worker = new Thread(() -> {
            try {
                doLoginAndSignBlocking(pin);
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error de token: " + e.getMessage());
                    signBtn.setDisable(false);
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
                });
                worker.interrupt();
            }
        });
        timeoutGuard.setDaemon(true);
        timeoutGuard.start();
    }

    private void doLoginAndSignBlocking(String pin) throws Exception {
        Platform.runLater(() -> statusLabel.setText("Autenticando en el token..."));

        TokenProvider provider = new TokenProvider();
        provider.login(pin);
        cert = provider.getCertificate();
        privateKey = provider.getPrivateKey();
        certChain = provider.getCertificateChain();

        String cn = extractCN(cert.getSubjectX500Principal().getName());
        Platform.runLater(() -> statusLabel.setText("Token verificado: " + cn));

        performSigning();
    }

    private void performSigning() {
        Thread signing = new Thread(() -> {
            try {
                String reason = reasonCombo.getSelectionModel().getSelectedItem();
                String role = roleField.getText().trim();
                String cn = extractCN(cert.getSubjectX500Principal().getName());
                String displayName = cn.replaceFirst("\\s+(?=FAU\\b)", "\n");

                String signerText = "Firmado digitalmente por:\n" + displayName +
                    "\nMotivo: " + reason +
                    (role.isEmpty() ? "" : "\n" + role) +
                    "\nFecha: " + ZonedDateTime.now(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'XXX"));

                File output = File.createTempFile("firmado-", "-FD.pdf");
                signedPdf = output;

                double[] rect = getPdfRect();

                PDFSigner.signPDF(
                    selectedPdf, signedPdf,
                    cert, privateKey, certChain,
                    reason, "Puno, Per\u00fa", cn,
                    signerText, currentPage,
                    (float) rect[0], (float) rect[1], selectedStampLayout()
                );

                signed = true;
                Platform.runLater(() -> {
                    statusLabel.setText("PDF firmado correctamente en la página " + (currentPage + 1) + ". Guarde el archivo.");
                    updateWorkflow(3);
                    previewHelpLabel.setText("Firma realizada. Guardá el PDF firmado.");
                    saveBtn.setDisable(false);
                    signBtn.setDisable(true);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error de firma: " + e.getMessage());
                    signBtn.setDisable(false);
                    e.printStackTrace();
                });
            }
        });
        signing.setDaemon(true);
        signing.start();
    }

    private void saveDocument() {
        if (signedPdf == null) {
            showAlert("No hay un PDF firmado. Primero firme un documento.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar PDF firmado");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        if (selectedPdf != null) {
            String name = selectedPdf.getName().replaceAll(".pdf$", "");
            chooser.setInitialFileName(name + "[Firmado].pdf");
        }
        File dest = chooser.showSaveDialog(stage);
        if (dest != null) {
            try {
                java.nio.file.Files.copy(signedPdf.toPath(), dest.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                updateWorkflow(4);
                previewHelpLabel.setText("Documento guardado correctamente.");
                statusLabel.setText("Guardado: " + dest.getName());
            } catch (java.io.IOException e) {
                showAlert("Error al guardar: " + e.getMessage());
            }
        }
    }

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
