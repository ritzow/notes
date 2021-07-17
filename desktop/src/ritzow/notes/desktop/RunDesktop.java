package ritzow.notes.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class RunDesktop {
	public static void main(String[] args) {
		Platform.startup(RunDesktop::start);
	}

	public static void start() {
		Stage stage = new Stage(StageStyle.TRANSPARENT);
		stage.setTitle("Online Notes Service");
		try(var in = Files.newInputStream(Path.of("ui").resolve("main.fxml"))) {
			stage.setScene(new Scene(new FXMLLoader(StandardCharsets.UTF_8).load(in)));
			stage.getScene().setFill(Color.TRANSPARENT);
			/* Transparent edges: */
			/* https://stackoverflow.com/questions/45258138/round-corners-in-java-fx-pane */
			/* https://apilevel.wordpress.com/2012/10/11/java-fx-stage-with-rounded-corners-with-background-image/ */
			Node toolbar = stage.getScene().lookup("#toolbar");
			Node closeButton = stage.getScene().lookup("#closeButton");
			Node uploadButton = stage.getScene().lookup("#uploadButton");
			Node notesContent = stage.getScene().lookup("#notesContent");
			toolbar.setOnMousePressed(pressEvent -> {
				toolbar.setOnMouseDragged(dragEvent -> {
					stage.setX(dragEvent.getScreenX() - pressEvent.getSceneX());
					stage.setY(dragEvent.getScreenY() - pressEvent.getSceneY());
				});
			});
			if(closeButton instanceof Button button) {
				button.setOnAction(event -> {
					stage.close();
				});
			}
			stage.show();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
}
