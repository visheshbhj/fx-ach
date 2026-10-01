package com.fx.ach;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class AchApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(AchApplication.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1280, 820);
        scene.getStylesheets().add(AchApplication.class.getResource("ach.css").toExternalForm());
        MainController controller = fxmlLoader.getController();
        controller.init(stage);
        stage.setScene(scene);
        stage.show();

        List<String> args = getParameters().getUnnamed();
        if (!args.isEmpty()) {
            controller.open(Path.of(args.get(0)));
        }
    }
}
