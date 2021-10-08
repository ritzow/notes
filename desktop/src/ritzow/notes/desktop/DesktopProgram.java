package ritzow.notes.desktop;

import com.google.common.net.InetAddresses;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ForkJoinPool;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import net.ritzow.notes.share.NoteProto;
import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.model.PlainTextChange;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.SegmentOps;

public class DesktopProgram {
	private final Node toolbar;
	private final InlineCssTextArea notesContent;
	private final InlineCssTextArea ipField;
	private final InlineCssTextArea usernameField;
	private final Stage stage;

	public DesktopProgram() throws IOException {
		stage = new Stage(StageStyle.TRANSPARENT);
		stage.setTitle("Online Notes Service");
		try(var in = DesktopProgram.class.getClassLoader().getResourceAsStream("icon.png")) {
			stage.getIcons().add(new Image(in));
		}
		FXMLLoader loader = new FXMLLoader();
		loader.setCharset(StandardCharsets.UTF_8);
		loader.setLocation(DesktopProgram.class.getClassLoader().getResource("main.fxml"));
		Parent root = loader.load();

		root.setOnDragDetected(event -> {
			//System.out.println(event.getPickResult());
			root.startFullDrag();
		});

		Scene scene = new Scene(root);
		scene.setFill(Color.TRANSPARENT);
		toolbar = scene.lookup("#toolbar");
		Button closeButton = (Button)scene.lookup("#closeButton");
		Button uploadButton = (Button)scene.lookup("#uploadButton");
		notesContent = (InlineCssTextArea)scene.lookup("#notesContent");
		ipField = (InlineCssTextArea)scene.lookup("#ipField");
		usernameField = (InlineCssTextArea)scene.lookup("#usernameField");

		toolbar.setOnMousePressed(pressEvent -> {
			toolbar.setOnMouseDragged(dragEvent -> {
				stage.setX(dragEvent.getScreenX() - pressEvent.getSceneX());
				stage.setY(dragEvent.getScreenY() - pressEvent.getSceneY());
			});
		});
		
		ipField.plainTextChanges().addObserver(this::onIpChange);
		usernameField.plainTextChanges().addObserver(this::onUsernameChange);

		closeButton.setOnAction(event -> stage.close());
		uploadButton.setOnAction(this::onUploadButton);
		stage.setScene(scene);
	}

	public void show() {
		stage.show();
	}

	private void onIpChange(PlainTextChange change) {
		queryUpdateNoteLocal();
	}
	
	private volatile Instant lastUsernameChange;

	private void onUsernameChange(PlainTextChange change) {
		lastUsernameChange = Instant.now();
		ForkJoinPool.commonPool().submit(() -> {
			try {
				Thread.sleep(Duration.ofSeconds(1).toMillis());
				if(lastUsernameChange.plusSeconds(1).isBefore(Instant.now())) {
					queryUpdateNoteLocal();
				}
			} catch(InterruptedException e) {
				e.printStackTrace();
			}
		});
	}

	private void queryUpdateNoteLocal() {
		if((InetAddresses.isInetAddress(ipField.getText()) && !usernameField.getText().isBlank())) {
			ForkJoinPool.commonPool().execute(() -> {
				try {
					/* query note for user from IP and update local */
					SocketChannel socket = SocketChannel.open(
						new InetSocketAddress(InetAddresses.forString(ipField.getText()), 5432));
					byte[] username = usernameField.getText().getBytes(StandardCharsets.UTF_8);
					ByteBuffer buf = ByteBuffer.allocateDirect(Byte.BYTES + Integer.BYTES + username.length);
					buf.put(NoteProto.MSG_QUERY).putInt(username.length).put(username).flip();
					socket.write(buf);
					/* Read in Note length and data */
					read(socket, buf.clear().limit(Integer.BYTES));
					int noteLength = buf.flip().getInt();
					buf = ByteBuffer.allocate(noteLength);
					read(socket, buf);
					socket.shutdownInput();
					socket.shutdownOutput();
					socket.close();
					buf.flip();
					/* TODO this probably isn't correct */
					String note = StandardCharsets.UTF_8.decode(buf).toString();
					System.out.println("Downloaded Note: " + note.codePoints().count() + " chars.");
					Platform.runLater(() -> {
						var doc = ReadOnlyStyledDocument.fromString(note, "", "", SegmentOps.styledTextOps());
						notesContent.getContent().replace(0, notesContent.getContent().length(), doc);
					});
				} catch(IOException e) {
					e.printStackTrace();
				}
			});
		}
	}

	private static void read(SocketChannel socket, ByteBuffer buf) throws IOException {
		do {
			socket.read(buf);
		} while(buf.hasRemaining());
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

	public static void upload(InetSocketAddress addr, String user, String note) {
		ForkJoinPool.commonPool().execute(() -> {
			try {
				SocketChannel socket = SocketChannel.open(addr);
				var lenBuf = ByteBuffer.allocateDirect(Byte.BYTES + Integer.BYTES);
				var userBuf = StandardCharsets.UTF_8.encode(user);
				/* TODO write message type */
				socket.write(lenBuf.put(NoteProto.MSG_UPDATE).putInt(userBuf.remaining()).flip());
				socket.write(userBuf);
				ByteBuffer noteBuf = StandardCharsets.UTF_8.encode(note);
				lenBuf.clear().putInt(noteBuf.remaining()).flip();
				socket.write(lenBuf);
				socket.write(noteBuf);
				/* Wait for the server to close its socket */
				//socket.read(ByteBuffer.allocate(1));
				socket.shutdownInput();
				socket.shutdownOutput();
				socket.close();
			} catch(IOException e) {
				e.printStackTrace();
			}
		});
	}
}
