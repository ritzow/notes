//for stored procedure: https://sqlperformance.com/2020/09/locking/upsert-anti-pattern

package ritzow.notes.server;

import java.io.IOException;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import org.hsqldb.server.Server;

public class RunServer {
	public static void main(String[] args) {
		System.out.println("Server started.");

		try(Selector select = Selector.open()) {
			ServerSocketChannel
				.open(StandardProtocolFamily.INET)
				.bind(new InetSocketAddress("127.0.0.1", 5432))
				.configureBlocking(false)
				.register(select, SelectionKey.OP_ACCEPT);
			ServerSocketChannel
				.open(StandardProtocolFamily.INET6)
				.bind(new InetSocketAddress("::1", 5432))
				.configureBlocking(false)
				.register(select, SelectionKey.OP_ACCEPT);
			Connection db = openDatabase();

			try {
				do {
					listen(select, db);
				} while(true);
			} catch(SQLException e) {
				try(var st = db.prepareStatement("SHUTDOWN")) {
					System.out.println("Shutting down");
					st.executeUpdate();
				} catch(SQLException throwables) {
					throwables.printStackTrace();
				}
			}
		} catch(IOException | SQLException e) {
			e.printStackTrace();
		}
	}

	private static Connection openDatabase() throws SQLException {
		Server server = new Server();
		server.setErrWriter(null);
		server.setLogWriter(null);
		server.setSilent(true);
		server.setTrace(false);
		server.setTls(false);
		server.setDatabaseName(0, "notes");
		server.setDatabasePath(0, "file:testdb/db");
		server.setAddress("::1");
		server.start();
		//DriverManager.getConnection("jdbc:hsqldb:file:server/db;shutdown=true");
		return DriverManager.getConnection("jdbc:hsqldb:hsql://[::1]/notes");
	}

	private static void listen(Selector select, Connection db) throws IOException, SQLException {
		select.select();

		for(var key : select.selectedKeys()) {
			if(key.channel() instanceof ServerSocketChannel serv) {
				SocketChannel connection = serv.accept();
				connection.configureBlocking(false);
				connection.register(select, SelectionKey.OP_READ, new ConnectionState());
			} else if(key.channel() instanceof SocketChannel connection) {
				if(key.isReadable()) {
					ConnectionState state = (ConnectionState)key.attachment();
					try {
						ProcessState newState = process(db, connection, state);
						switch(newState) {
							case MALFORMED -> {
								connection.close();
								key.cancel();
								System.err.println("Malformed data from " + connection + " during " + state.state);
							}
							case DONE -> {
								System.out.println("Done with " + connection);
								connection.close();
								key.cancel();
							}
							default -> state.state = newState;
						}
					} catch(IOException e) {
						key.cancel();
						System.err.println(e.getMessage());
					}
				}
			} else {
				throw new RuntimeException("Unknown channel " + key.channel());
			}
		}

		select.selectedKeys().clear();
	}

	private static void saveNote(Connection db, byte[] username, CharBuffer note) throws SQLException, IOException {
		//https://devblogs.microsoft.com/azure-sql/the-insert-if-not-exists-challenge-a-solution/
		try(CallableStatement call = db.prepareCall("call update_user_note(?, ?, ?)")) {
			call.setString(1, new String(username, StandardCharsets.UTF_8));
			/* TODO truncate? */
			Clob handle = db.createClob();
			try(Writer writer = handle.setCharacterStream(1)) {
				writer.write(note.toString());
			}
			call.setClob(2, handle);
			call.registerOutParameter(3, Types.INTEGER);
			/* TODO this is a blocking call */
			call.execute();
			int id = call.getInt(3);
			System.out.println("User ID " + id + " connected.");
		}
	}

	private static class ConnectionState {
		ProcessState state;
		private final ByteBuffer readBuffer;
		private byte[] username;
		private ByteBuffer noteBuffer;

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

	private static ProcessState process(Connection db, SocketChannel conn, ConnectionState state)
		throws IOException, SQLException {
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
						return state.state;
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
						return state.state;
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
						state.noteBuffer = ByteBuffer.allocate(noteLength);
						state.noteBuffer.put(state.readBuffer.flip().position(Integer.BYTES));
						/* read buffer isn't used after this, don't clear it. */
						return ProcessState.READ_NOTE_TEXT;
					}
					case WAIT -> {
						return state.state;
					}
					case CLOSE -> {
						return ProcessState.MALFORMED;
					}
				}
			}
			case READ_NOTE_TEXT -> {
				if(conn.read(state.noteBuffer) == -1) {
					return ProcessState.MALFORMED;
				}

				if(!state.noteBuffer.hasRemaining()) {
					CharBuffer note = StandardCharsets.UTF_8.decode(state.noteBuffer.flip());
					saveNote(db, state.username, note);
					return ProcessState.DONE;
				}
				return state.state;
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
