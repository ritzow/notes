//for stored procedure: https://sqlperformance.com/2020/09/locking/upsert-anti-pattern

package ritzow.notes.server;

import java.io.IOException;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.ritzow.notes.share.NoteProto;
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

		ExecutorService pool = Executors.newCachedThreadPool();

		for(var key : select.selectedKeys()) {
			if(key.channel() instanceof ServerSocketChannel serv) {
				SocketChannel connection = serv.accept();
				pool.execute(() -> {
					try {
						System.out.println("Opened connection to " + connection.getRemoteAddress());
						process(db, connection);
					} catch(ClosedByInterruptException e) {
						System.out.println(connection + " closed by interrupt");
					} catch(IOException | SQLException e) {
						e.printStackTrace();
					}
				});
			} else {
				throw new RuntimeException("Unknown channel " + key.channel());
			}
		}

		select.selectedKeys().clear();
	}

	private static void saveNote(Connection db, String username, String note) throws SQLException, IOException {
		//https://devblogs.microsoft.com/azure-sql/the-insert-if-not-exists-challenge-a-solution/
		try(CallableStatement call = db.prepareCall("call update_user_note(?, ?, ?)")) {
			call.setString(1, username);
			/* TODO truncate? */
			Clob handle = db.createClob();
			try(Writer writer = handle.setCharacterStream(1)) {
				writer.write(note);
			}
			call.setClob(2, handle);
			call.registerOutParameter(3, Types.INTEGER);
			/* TODO this is a blocking call */
			call.execute();
			int id = call.getInt(3);
			System.out.println("User ID " + id + " connected and updated note to " + note.codePoints().count() + " chars.");
		}
	}
	
	private static String getNote(Connection db, String username) throws SQLException, IOException {
		try(PreparedStatement call = db.prepareStatement(
			"SELECT notes.notedata FROM notes INNER JOIN userdata " +
			"ON userdata.userid = notes.userid WHERE username = ?")) {
			call.setString(1, username);
			call.execute();
			ResultSet result = call.getResultSet();
			if(result != null && result.next()) {
				Clob data = result.getClob(1);
				if(data.length() > Integer.MAX_VALUE) {
					throw new RuntimeException("Large clob");
				}
				return data.getSubString(1, (int)data.length());
			} else {
				return null;
			}
		}
	}

	private static final int
		MAX_USERNAME_LENGTH = 1_0000,
		MAX_NOTE_LENGTH = 4_128;

	private static void process(Connection db, SocketChannel conn) throws IOException, SQLException {
		ByteBuffer dst = ByteBuffer.allocate(Integer.BYTES);
		
		readBytes(conn, dst, Byte.BYTES);
		
		switch(dst.flip().get()) {
			case NoteProto.MSG_QUERY -> {
				readBytes(conn, dst.clear(), Integer.BYTES);
				int usernameLength = dst.flip().getInt();
				String user = readUsername(conn, usernameLength);
				String noteText = getNote(db, user);
				if(noteText != null) {
					System.out.println("Retrieved note text: \"" + noteText + "\"");
				} else {
					System.out.println("No note text");
					noteText = "";
				}
				ByteBuffer note = StandardCharsets.UTF_8.encode(noteText);
				dst.clear().putInt(note.remaining()).flip();
				conn.write(new ByteBuffer[]{dst, note});
			}
			
			case NoteProto.MSG_UPDATE -> {
				readBytes(conn, dst.clear(), Integer.BYTES);
				int usernameLength = dst.getInt(0);
				
				if(usernameLength > MAX_USERNAME_LENGTH) {
					throw new RuntimeException("username length " + usernameLength + " longer than " + MAX_USERNAME_LENGTH);
				}
				
				dst = ByteBuffer.allocateDirect(usernameLength);
				readBytes(conn, dst, usernameLength);
				String username = StandardCharsets.UTF_8.decode(dst.flip()).toString();
				readBytes(conn, dst.rewind(), 4);
				int noteLength = dst.getInt(0);
				
				if(noteLength > MAX_NOTE_LENGTH) {
					throw new RuntimeException("note length " + noteLength + " longer than " + MAX_NOTE_LENGTH);
				}
				
				dst = dst.capacity() >= noteLength ? dst.clear() : ByteBuffer.allocateDirect(noteLength);
				readBytes(conn, dst, noteLength);
				saveNote(db, username, StandardCharsets.UTF_8.decode(dst.flip()).toString());
			}
			
			default -> {
				System.out.println("Received unknown message type from client");
			}
		}
	}
	
	private static String readUsername(ReadableByteChannel conn, int usernameLength) throws IOException {
		if(usernameLength > MAX_USERNAME_LENGTH) {
			throw new RuntimeException("username length " + usernameLength + " longer than " + MAX_USERNAME_LENGTH);
		}
		
		ByteBuffer buf = ByteBuffer.allocate(usernameLength);
		readBytes(conn, buf, usernameLength);
		return StandardCharsets.UTF_8.decode(buf.flip()).toString();
	}

	private static void readBytes(ReadableByteChannel in, ByteBuffer dst, int count) throws IOException {
		dst.limit(count);
		do {
			in.read(dst);
		} while(dst.hasRemaining());
	}
}
