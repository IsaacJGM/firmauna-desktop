package unap.oti.firmauna.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import unap.oti.firmauna.pkcs11.TokenProvider;
import unap.oti.firmauna.signer.PDFSigner;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainWindow {

    private final Stage stage;
    private final ImageView pdfPreview = new ImageView();
    private final Label statusLabel = new Label("Seleccione un PDF para firmar.");
    private final ComboBox<String> reasonCombo = new ComboBox<>();
    private final TextField roleField = new TextField();
    private final CheckBox positionCheck = new CheckBox("Elegir posición de firma");
    private final Button signBtn = new Button("Firmar mi PDF");
    private final Button saveBtn = new Button("Guardar PDF firmado");

    private File selectedPdf;
    private File signedPdf;
    private PDDocument currentDoc;
    private X509Certificate cert;
    private PrivateKey privateKey;
    private java.security.cert.Certificate[] certChain;
    private double sigX, sigY, sigW = 160, sigH = 55;

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
        positionCheck.setSelected(true);
        saveBtn.setDisable(true);
    }

    public void show() {
        stage.setScene(new Scene(buildRoot(), 1100, 720));
        stage.show();
    }

    private javafx.scene.layout.VBox buildRoot() {
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #306080; -fx-padding: 12 16;");
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("firmaUNA 2.0");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");
        Label subtitleLbl = new Label("Firma Digital · Token RENIEC · UNA Puno");
        subtitleLbl.setStyle("-fx-text-fill: #b0c4de; -fx-font-size: 11px;");
        header.getChildren().addAll(new VBox(2, title, subtitleLbl));

        javafx.scene.control.SplitPane split = new javafx.scene.control.SplitPane();
        split.setDividerPositions(0.62);

        pdfPreview.setFitWidth(650);
        pdfPreview.setPreserveRatio(true);
        StackPane pdfPane = new StackPane(pdfPreview);
        pdfPane.setStyle("-fx-background-color: #e8e8e8; -fx-border-color: #ccc;");
        pdfPane.setPadding(new Insets(8));
        pdfPane.setAlignment(Pos.CENTER);

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
            new javafx.scene.control.Separator(),
            new Label("Motivo:"),
            reasonCombo,
            new Label("Cargo:"),
            roleField,
            positionCheck,
            new javafx.scene.control.Separator(),
            signBtn,
            saveBtn,
            new javafx.scene.control.Separator(),
            statusLabel
        );

        split.getItems().addAll(pdfPane, controls);

        HBox footer = new HBox(new Label("OTI — Subunidad de Gobierno Electrónico — UNA Puno"));
        footer.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 8px;");
        footer.setAlignment(Pos.CENTER);

        VBox root = new VBox(header, split, footer);
        VBox.setVgrow(split, Priority.ALWAYS);
        return root;
    }

    private void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            selectedPdf = file;
            signedPdf = null;
            saveBtn.setDisable(true);
            statusLabel.setText("Cargando: " + file.getName());
            new Thread(() -> renderPdf(file)).start();
        }
    }

    private void renderPdf(File file) {
        try {
            if (currentDoc != null) currentDoc.close();
            currentDoc = Loader.loadPDF(file);
            PDFRenderer renderer = new PDFRenderer(currentDoc);
            BufferedImage img = renderer.renderImage(0, 1.5f);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            Image fxImage = new Image(new ByteArrayInputStream(baos.toByteArray()));

            Platform.runLater(() -> {
                pdfPreview.setImage(fxImage);
                statusLabel.setText("PDF cargado. Conecte su token USB (Bit4id).");
            });
        } catch (Exception e) {
            Platform.runLater(() -> statusLabel.setText("Error al cargar PDF: " + e.getMessage()));
        }
    }

    private void signDocument() {
        if (selectedPdf == null) {
            showAlert("Primero seleccione un archivo PDF.");
            return;
        }
        TextInputDialog pinDlg = new TextInputDialog();
        pinDlg.setTitle("PIN del Token");
        pinDlg.setHeaderText("Ingrese el PIN de su token Bit4id");
        pinDlg.setContentText("PIN:");
        pinDlg.getDialogPane().setMinWidth(350);
        pinDlg.showAndWait().ifPresent(pin -> {
            if (!pin.trim().isBlank()) doLoginAndSign(pin.trim());
        });
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
                    enableButtons();
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
                    enableButtons();
                });
                worker.interrupt();
            }
        });
        timeoutGuard.setDaemon(true);
        timeoutGuard.start();
    }

    private void enableButtons() {
        signBtn.setDisable(false);
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

        if (positionCheck.isSelected()) {
            Platform.runLater(this::showSignaturePositioner);
        } else {
            performSigning(0, 50, 700, 160, 55);
        }
    }

    private void showSignaturePositioner() {
        SignaturePositioner positioner = new SignaturePositioner(currentDoc);
        positioner.show(stage, (x, y, w, h) -> performSigning(0, x, y, w, h));
    }

    private void performSigning(int page, double x, double y, double w, double h) {
        Thread signing = new Thread(() -> {
            try {
                String reason = reasonCombo.getSelectionModel().getSelectedItem();
                String role = roleField.getText().trim();
                String cn = extractCN(cert.getSubjectX500Principal().getName());

                String signerText = "Firmado digitalmente por:\n" + cn +
                    (role.isEmpty() ? "" : "\n" + role) +
                    "\nFecha: " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()) +
                    "\nfirmaUNA v2.0";

                File output = File.createTempFile("firmado-", "-FD.pdf");
                signedPdf = output;

                PDFSigner.signPDF(
                    selectedPdf, signedPdf,
                    cert, privateKey, certChain,
                    reason, "Puno, Per\u00fa", cn,
                    signerText, page,
                    (float)x, (float)y, (float)w, (float)h
                );

                Platform.runLater(() -> {
                    statusLabel.setText("PDF firmado correctamente. Guarde el archivo.");
                    saveBtn.setDisable(false);
                    signBtn.setDisable(false);
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
            } catch (IOException e) {
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