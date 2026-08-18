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
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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
    private final Button signBtn = new Button("Firmar mi PDF");
    private final Button saveBtn = new Button("Guardar PDF firmado");
    private final Label pageLabel = new Label("Pág 1/1");

    // PDF preview canvas + draggable signature box (integrated, no separate dialog)
    private final Canvas canvas = new Canvas(560, 680);
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
    private static final double STAMP_W = 220, STAMP_H = 70;
    private double boxWcanvas = STAMP_W, boxHcanvas = STAMP_H;
    private Image pageImage;

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
        Button prevBtn = new Button("<<");
        Button nextBtn = new Button(">>");
        prevBtn.setStyle("-fx-font-weight: bold;");
        nextBtn.setStyle("-fx-font-weight: bold;");
        prevBtn.setOnAction(e -> prevPage());
        nextBtn.setOnAction(e -> nextPage());
        nav.getChildren().addAll(prevBtn, pageLabel, nextBtn);

        VBox.setVgrow(canvas, Priority.ALWAYS);
        left.getChildren().addAll(canvas, nav);

        // RIGHT: controls (no PIN field; PIN is requested in a modal on Firmar)
        VBox controls = new VBox(10);
        controls.setPadding(new Insets(15));
        controls.setMinWidth(300);

        Button selectBtn = new Button("Seleccionar PDF...");
        selectBtn.setMaxWidth(Double.MAX_VALUE);
        selectBtn.setOnAction(e -> openFile());

        reasonCombo.setMaxWidth(Double.MAX_VALUE);
        roleField.setPromptText("Ej. Docente Universitario");
        roleField.setMaxWidth(Double.MAX_VALUE);

        signBtn.setMaxWidth(Double.MAX_VALUE);
        signBtn.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-font-weight: bold;");
        signBtn.setOnAction(e -> signDocument());

        saveBtn.setMaxWidth(Double.MAX_VALUE);
        saveBtn.setStyle("-fx-background-color: #198754; -fx-text-fill: white; -fx-font-weight: bold;");
        saveBtn.setOnAction(e -> saveDocument());

        controls.getChildren().addAll(
            new Label("1. Seleccione su PDF a firmar:"),
            selectBtn,
            new Separator(),
            new Label("Motivo:"),
            reasonCombo,
            new Label("Cargo:"),
            roleField,
            new Separator(),
            signBtn,
            saveBtn,
            new Separator(),
            statusLabel
        );

        split.getItems().addAll(left, controls);

        HBox footer = new HBox(new Label("OTI — Subunidad de Gobierno Electrónico — UNA Puno"));
        footer.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 8px;");
        footer.setAlignment(Pos.CENTER);

        VBox root = new VBox(header, split, footer);
        VBox.setVgrow(split, Priority.ALWAYS);
        return root;
    }

    // ---------- PDF loading & rendering ----------

    private void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            selectedPdf = file;
            signedPdf = null;
            signed = false;
            currentPage = 0;
            saveBtn.setDisable(true);
            signBtn.setDisable(false);
            statusLabel.setText("Cargando: " + file.getName());
            new Thread(() -> loadPdf(file)).start();
        }
    }

    private void loadPdf(File file) {
        try {
            if (currentDoc != null) currentDoc.close();
            currentDoc = Loader.loadPDF(file);
            totalPages = currentDoc.getNumberOfPages();
            Platform.runLater(() -> {
                pageLabel.setText("Pág 1/" + totalPages);
                renderPage();
                statusLabel.setText("PDF cargado. Arrastre el recuadro y presione Firmar.");
            });
        } catch (Exception e) {
            Platform.runLater(() -> statusLabel.setText("Error al cargar PDF: " + e.getMessage()));
        }
    }

    private void prevPage() {
        if (currentPage > 0) {
            currentPage--;
            pageLabel.setText("Pág " + (currentPage + 1) + "/" + totalPages);
            renderPage();
        }
    }

    private void nextPage() {
        if (currentPage < totalPages - 1) {
            currentPage++;
            pageLabel.setText("Pág " + (currentPage + 1) + "/" + totalPages);
            renderPage();
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

                // Box must visually match the real 220x70-point stamp on the PDF
                boxWcanvas = STAMP_W * renderScale * canvasScale;
                boxHcanvas = STAMP_H * renderScale * canvasScale;

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
        gc.setStroke(Color.DODGERBLUE);
        gc.setLineWidth(1.5);
        gc.setFill(Color.rgb(30, 144, 255, 0.15));
        gc.fillRect(boxX, boxY, boxWcanvas, boxHcanvas);
        gc.strokeRect(boxX, boxY, boxWcanvas, boxHcanvas);
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
        return new double[]{pdfX, pdfYbottom, STAMP_W, STAMP_H};
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

                String signerText = "Firmado digitalmente por:\n" + cn +
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
                    (float) rect[0], (float) rect[1], (float) rect[2], (float) rect[3]
                );

                signed = true;
                Platform.runLater(() -> {
                    statusLabel.setText("PDF firmado correctamente en la página " + (currentPage + 1) + ". Guarde el archivo.");
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
