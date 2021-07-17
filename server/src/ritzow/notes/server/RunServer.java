package ritzow.notes.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
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

		try(var st = con.prepareStatement("SHUTDOWN")) {
			st.executeUpdate();
		}

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

			ByteBuffer buf = ByteBuffer.allocateDirect(65535);

			while(true) {
				select.select();

				for(var key : select.selectedKeys()) {
					if(key.channel() instanceof ServerSocketChannel serv) {
						SocketChannel connection = serv.accept();
						connection.register(select, SelectionKey.OP_READ);
					} else if(key.channel() instanceof SocketChannel conn) {
						buf.clear();
						conn.read(buf);
						buf.flip();
						System.out.println(buf.asCharBuffer());
					} else {
						throw new RuntimeException("Unknown channel " + key.channel());
					}
				}

				select.selectedKeys().clear();
			}

			//System.out.println("Database shutdown started");
		}
	}
}
