/* Transparent edges: */
/* https://stackoverflow.com/questions/45258138/round-corners-in-java-fx-pane */
/* https://apilevel.wordpress.com/2012/10/11/java-fx-stage-with-rounded-corners-with-background-image/ */

/* Close icon https://commons.wikimedia.org/wiki/File:Red_X.svg */

/* dropshadow from https://stackoverflow.com/questions/31679175/how-can-i-add-shadow-for-undecorated-stage-with-javafx */

package ritzow.notes.desktop;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.fxmisc.richtext.InlineCssTextField;

public class RunDesktop {
	public static void main(String[] args) {
		Platform.startup(RunDesktop::start);
	}

	private static Path programDir() {
		return Path.of(".");
	}

	public static void start() {
		Stage stage = new Stage(StageStyle.TRANSPARENT);
		stage.setTitle("Online Notes Service");
		Scene scene;
		try {
			scene = new Scene(load(programDir().resolve("ui").resolve("main.fxml")));
			//if(scene.getRoot().lookup("#textPlaceholder") instanceof Pane pane) {
			//	pane.getChildren().add(load(programDir().resolve("ui").resolve("text.fxml")));
			//}
		} catch(IOException e) {
			e.printStackTrace();
			return;
		}
		scene.setFill(Color.TRANSPARENT);
		Node toolbar = scene.lookup("#toolbar");
		Button closeButton = (Button)scene.lookup("#closeButton");
		Button uploadButton = (Button)scene.lookup("#uploadButton");
		TextInputControl notesContent = (TextInputControl)scene.lookup("#notesContent");
		InlineCssTextField ipField = (InlineCssTextField)scene.lookup("#ipField");
		toolbar.setOnMousePressed(pressEvent -> {
			toolbar.setOnMouseDragged(dragEvent -> {
				stage.setX(dragEvent.getScreenX() - pressEvent.getSceneX());
				stage.setY(dragEvent.getScreenY() - pressEvent.getSceneY());
			});
		});
		closeButton.setOnAction(event -> stage.close());
		uploadButton.setOnAction(event -> {
			try {
				String ip = ipField.getText().isBlank() ? ipField.getPromptText().getText() : ipField.getText();
				NoteUploader.upload(new InetSocketAddress(ip, 5432), "blobjim", notesContent.getText());
			} catch(IOException e) {
				e.printStackTrace();
			}
		});
		stage.setScene(scene);
		stage.show();
	}

	public static <T> T load(Path file) throws IOException {
		try(var in = Files.newInputStream(file)) {
			return new FXMLLoader(StandardCharsets.UTF_8).load(in);
		}
	}
}
