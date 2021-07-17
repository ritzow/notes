package ritzow.notes.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class RunDesktop {
	public static void main(String[] args) {
		Platform.startup(RunDesktop::start);
	}

	public static void start() {
		Stage stage = new Stage();
		System.out.println(Path.of(".").toAbsolutePath());
		try(var in = Files.newInputStream(Path.of("ui").resolve("main.fxml"))) {
			stage.setScene(new Scene(new FXMLLoader(StandardCharsets.UTF_8).load(in)));
			stage.show();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
}
