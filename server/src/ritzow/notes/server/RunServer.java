package ritzow.notes.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
//import org.hsqldb.server.*;

public class RunServer {
	public static void main(String[] args) throws SQLException, IOException {
//		Server server = new Server();
//		server.setDatabaseName(0, "notesdb");
//		server.setDatabasePath(0, "file:notes.db");
//		server.setAddress("::1");
//		server.setPort(5433);
//		server.start();

		System.out.println("Program started");

		Connection con = DriverManager.getConnection("jdbc:hsqldb:file:db/notes.db;shutdown=true", "SA", "");

		System.out.println("Database started");

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try(var st = con.prepareStatement("SHUTDOWN")) {
				System.out.println("Shutting down");
				st.executeUpdate();
			} catch(SQLException throwables) {
				throwables.printStackTrace();
			}
		}));

		var listen4 = ServerSocketChannel
			.open(StandardProtocolFamily.INET)
			.bind(new InetSocketAddress("127.0.0.1", 5432))
			.configureBlocking(false);
		var listen6 = ServerSocketChannel
			.open(StandardProtocolFamily.INET6)
			.bind(new InetSocketAddress("::1", 5432))
			.configureBlocking(false);

		try(Selector select = Selector.open()) {
			listen4.register(select, SelectionKey.OP_ACCEPT);
			listen6.register(select, SelectionKey.OP_ACCEPT);

			while(true) {
				select.select();

				for(var key : select.selectedKeys()) {
					if(key.channel() instanceof ServerSocketChannel serv) {
						SocketChannel connection = serv.accept();
						connection.configureBlocking(false);
						connection.register(select, SelectionKey.OP_READ, new ConnectionState());
					} else if(key.channel() instanceof SocketChannel connection) {
						if(key.isReadable()) {
							ConnectionState state = (ConnectionState)key.attachment();
							ProcessState newState = process(connection, state);
							System.out.println(state.state);
							switch(newState) {
								case MALFORMED -> {
									connection.close();
									key.cancel();
									System.err.println("Malformed data from " + connection + " during " + state.state);
								}
								case DONE -> {
									connection.close();
									key.cancel();
									System.out.println("Done with " + connection);
								}
								default -> state.state = newState;
							}
						}
					} else {
						throw new RuntimeException("Unknown channel " + key.channel());
					}
				}

				select.selectedKeys().clear();
			}
		}
	}

	private static class ConnectionState {
		ProcessState state;
		private final ByteBuffer readBuffer;
		private byte[] username;
		private Integer noteLength;
		/* TODO need note storage buffer */

		private ConnectionState() {
			this.readBuffer = ByteBuffer.allocateDirect(8192);
			this.state = ProcessState.READ_USERNAME_LENGTH;
		}
	}

	private enum ProcessState {
		READ_USERNAME_LENGTH,
		READ_USERNAME_TEXT,
		READ_NOTE_LENGTH,
		READ_NOTE_TEXT,
		DONE,
		MALFORMED
	}

	private static final int
		MAX_USERNAME_LENGTH = 1_0000,
		MAX_NOTE_LENGTH = 4_128;

	private static ProcessState process(SocketChannel conn, ConnectionState state) throws IOException {
		switch(state.state) {
			case READ_USERNAME_LENGTH -> {
				switch(readyBytes(conn, state.readBuffer, Integer.BYTES)) {
					case READY -> {
						int usernameLength = state.readBuffer.getInt(0);
						if(usernameLength > MAX_USERNAME_LENGTH) {
							return ProcessState.MALFORMED;
						}
						state.username = new byte[usernameLength];
						state.readBuffer.clear();
						return ProcessState.READ_USERNAME_TEXT;
					}
					case WAIT -> {
						return ProcessState.READ_USERNAME_LENGTH;
					}
					case CLOSE -> {
						return ProcessState.MALFORMED;
					}
				}
			}
			case READ_USERNAME_TEXT -> {
				switch(readyBytes(conn, state.readBuffer, state.username.length)) {
					case READY -> {
						state.readBuffer.get(0, state.username).clear();
						return ProcessState.READ_NOTE_LENGTH;
					}
					case WAIT -> {
						return ProcessState.READ_USERNAME_TEXT;
					}
					case CLOSE -> {
						return ProcessState.MALFORMED;
					}
				}
			}
			case READ_NOTE_LENGTH -> {
				switch(readyBytes(conn, state.readBuffer, Integer.BYTES)) {
					case READY -> {
						int noteLength = state.readBuffer.getInt(0);
						if(noteLength > MAX_NOTE_LENGTH) {
							return ProcessState.MALFORMED;
						}
						state.noteLength = noteLength;
						state.readBuffer.clear();
						return ProcessState.READ_NOTE_TEXT;
					}
					case WAIT -> {
						return ProcessState.READ_NOTE_LENGTH;
					}
					case CLOSE -> {
						System.out.println("close");
						return ProcessState.MALFORMED;
					}
				}
			}
			case READ_NOTE_TEXT -> {
				if(state.noteLength == 0) {
					return ProcessState.DONE;
				}

				if(conn.read(state.readBuffer) == -1) {
					return ProcessState.MALFORMED;
				}

				System.out.println("From " + conn.getRemoteAddress() + ":\"" +
					StandardCharsets.UTF_8.decode(state.readBuffer.flip()) + "\"");
				/* TODO read all data, won't always return done in one go, or at all! (might close early) */
				return ProcessState.DONE;
			}
			case DONE, MALFORMED -> throw new IllegalStateException();
		}
		return null;
	}

	private enum ReadResult {
		READY,
		WAIT,
		CLOSE
	}

	private static ReadResult readyBytes(SocketChannel socket, ByteBuffer buf, int count) throws IOException {
		buf.limit(count);
		if(socket.read(buf) == -1) {
			return ReadResult.CLOSE;
		}
		return buf.position() >= (count - 1) ? ReadResult.READY : ReadResult.WAIT;
	}
}
