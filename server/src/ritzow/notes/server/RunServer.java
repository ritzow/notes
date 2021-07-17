package ritzow.notes.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
//import org.hsqldb.server.*;

public class RunServer {
	public static void main(String[] args) throws SQLException {
//		Server server = new Server();
//		server.setDatabaseName(0, "notesdb");
//		server.setDatabasePath(0, "file:notes.db");
//		server.setAddress("::1");
//		server.setPort(5433);
//		server.start();

		System.out.println("Program started");

		Connection con = DriverManager.getConnection("jdbc:hsqldb:file:db/notes.db;shutdown=true", "SA", "");

		System.out.println("Database started");

		con.prepareStatement("SHUTDOWN");

		System.out.println("Database shutdown started");
	}
}
