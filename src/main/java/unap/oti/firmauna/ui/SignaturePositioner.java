package unap.oti.firmauna.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Modal window that renders the first page of a PDF and lets the user
 * drag a semi-transparent rectangle to position the visual signature.
 */
public class SignaturePositioner {

    // Position state
    private double boxX = 40, boxY = 40;
    private double dragStartX, dragStartY;
    private boolean dragging;

    private final PDDocument document;
    private final Canvas canvas;
    private final GraphicsContext gc;

    public SignaturePositioner(PDDocument document) {
        this.document = document;
        this.canvas = new Canvas(420, 520);
        this.gc = canvas.getGraphicsContext2D();
    }

    public void show(Stage owner, PositionCallback callback) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle("Posicione su firma");

        renderPage();

        // Drag handlers
        canvas.setOnMousePressed(e -> {
            double mx = e.getX();
            double my = e.getY();
            if (mx >= boxX && mx <= boxX + 220 &&
                my >= boxY && my <= boxY + 70) {
                dragging = true;
                dragStartX = mx - boxX;
                dragStartY = my - boxY;
            } else {
                dragging = false;
                boxX = Math.max(0, Math.min(mx - 110, canvas.getWidth() - 220));
                boxY = Math.max(0, Math.min(my - 35, canvas.getHeight() - 70));
                redraw();
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (dragging) {
                boxX = Math.max(0, Math.min(e.getX() - dragStartX, canvas.getWidth() - 220));
                boxY = Math.max(0, Math.min(e.getY() - dragStartY, canvas.getHeight() - 70));
                redraw();
            }
        });

        Button okBtn = new Button("Continuar");
        okBtn.setStyle("-fx-background-color: #198754; -fx-text-fill: white;");
        okBtn.setOnAction(e -> {
            dialog.close();
            callback.onPosition(boxX * 1.4, boxY * 1.6, 220, 70);
        });

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white;");
        cancelBtn.setOnAction(e -> {
            dialog.close();
            callback.onPosition(50, 700, 220, 70);
        });

        HBox buttons = new HBox(10, okBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER);

        VBox root = new VBox(10, canvas, buttons);
        root.setPadding(new Insets(10));

        dialog.setScene(new Scene(root, 440, 590));
        dialog.show();
    }

    private void renderPage() {
        try {
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage page = renderer.renderImage(0, 1.2f);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(page, "png", baos);
            Image fxImage = new Image(new ByteArrayInputStream(baos.toByteArray()));

            double sw = canvas.getWidth() / fxImage.getWidth();
            double sh = canvas.getHeight() / fxImage.getHeight();
            double s = Math.min(sw, sh);
            double w = fxImage.getWidth() * s;
            double h = fxImage.getHeight() * s;
            double ox = (canvas.getWidth() - w) / 2;
            double oy = (canvas.getHeight() - h) / 2;

            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            gc.drawImage(fxImage, ox, oy, w, h);
            drawBox();
        } catch (Exception e) {
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            gc.setStroke(Color.RED);
            gc.strokeText("Error: " + e.getMessage(), 20, 50);
        }
    }

    private void redraw() {
        renderPage();
        drawBox();
    }

    private void drawBox() {
        gc.setStroke(Color.DODGERBLUE);
        gc.setLineWidth(1.5);
        gc.setFill(Color.rgb(30, 144, 255, 0.15));
        gc.fillRect(boxX, boxY, 220, 70);
        gc.strokeRect(boxX, boxY, 220, 70);
    }

    @FunctionalInterface
    public interface PositionCallback {
        void onPosition(double x, double y, double w, double h);
    }
}