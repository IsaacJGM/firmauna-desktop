package unap.oti.firmauna;

import javafx.application.Application;
import javafx.stage.Stage;
import unap.oti.firmauna.ui.MainWindow;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        MainWindow window = new MainWindow(primaryStage);
        window.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}