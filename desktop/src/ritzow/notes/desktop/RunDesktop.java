/* Transparent edges: */
/* https://stackoverflow.com/questions/45258138/round-corners-in-java-fx-pane */
/* https://apilevel.wordpress.com/2012/10/11/java-fx-stage-with-rounded-corners-with-background-image/ */

/* Close icon https://commons.wikimedia.org/wiki/File:Red_X.svg */

/* dropshadow from https://stackoverflow.com/questions/31679175/how-can-i-add-shadow-for-undecorated-stage-with-javafx */

package ritzow.notes.desktop;

import java.io.IOException;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

public class RunDesktop {
	public static void main(String[] args) {
		Platform.startup(() -> {
			try {
				new DesktopProgram().show();
			} catch(IOException e) {
				new Alert(AlertType.ERROR, e.getMessage()).showAndWait();
			}
		});
	}
}
