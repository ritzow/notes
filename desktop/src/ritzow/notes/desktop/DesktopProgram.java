package ritzow.notes.desktop;

import com.google.common.net.InetAddresses;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ForkJoinPool;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.fxmisc.richtext.InlineCssTextArea;

public class DesktopProgram {
	private final Node toolbar;
	private final Button closeButton;
	private final Button uploadButton;
	private final InlineCssTextArea notesContent;
	private final InlineCssTextArea ipField;
	private final InlineCssTextArea usernameField;
	private final Stage stage;

	public DesktopProgram() throws IOException {
		stage = new Stage(StageStyle.TRANSPARENT);
		stage.setTitle("Online Notes Service");
		Parent root = load(programDir().resolve("ui").resolve("main.fxml"));
		Scene scene = new Scene(root);
		//JMetro metro = new JMetro(root, Style.DARK);
		//metro.setScene(scene);
		scene.setFill(Color.TRANSPARENT);
		toolbar = scene.lookup("#toolbar");
		closeButton = (Button)scene.lookup("#closeButton");
		uploadButton = (Button)scene.lookup("#uploadButton");
		notesContent = (InlineCssTextArea)scene.lookup("#notesContent");
		ipField = (InlineCssTextArea)scene.lookup("#ipField");
		usernameField = (InlineCssTextArea)scene.lookup("#usernameField");
		toolbar.setOnMousePressed(pressEvent -> {
			toolbar.setOnMouseDragged(dragEvent -> {
				stage.setX(dragEvent.getScreenX() - pressEvent.getSceneX());
				stage.setY(dragEvent.getScreenY() - pressEvent.getSceneY());
			});
		});
		closeButton.setOnAction(event -> stage.close());
		uploadButton.setOnAction(this::onUploadButton);
		stage.setScene(scene);
	}

	public void show() {
		stage.show();
	}

	private static Path programDir() {
		return Path.of(".");
	}

	private void onUploadButton(ActionEvent event) {
		String ip = ipField.getText();
		try {
			InetAddress address = InetAddresses.forString(ip);
			upload(new InetSocketAddress(address, 5432), usernameField.getText(), notesContent.getText());
		} catch(IllegalArgumentException e) {
			System.err.println(ip + " is not a valid IP address.");
		}
	}

	public static <T> T load(Path file) throws IOException {
		try(var in = Files.newInputStream(file)) {
			return new FXMLLoader(StandardCharsets.UTF_8).load(in);
		}
	}

	public static void upload(InetSocketAddress addr, String user, String note) {
		var noteBuf = StandardCharsets.UTF_8.encode(note);
		ForkJoinPool.commonPool().execute(() -> {
			try {
				SocketChannel socket = SocketChannel.open(addr);
				var lenBuf = ByteBuffer.allocateDirect(Integer.BYTES);
				var userBuf = StandardCharsets.UTF_8.encode(user);
				socket.write(lenBuf.putInt(userBuf.remaining()).flip());
				socket.write(userBuf);
				lenBuf.clear().putInt(noteBuf.remaining()).flip();
				socket.write(lenBuf);
				socket.write(noteBuf);
				/* Wait for the server to close its socket */
				socket.read(ByteBuffer.allocate(1));
			} catch(IOException e) {
				e.printStackTrace();
			}
		});
	}
}
