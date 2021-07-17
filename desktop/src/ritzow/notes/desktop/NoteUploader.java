package ritzow.notes.desktop;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class NoteUploader {
	public static void upload(InetSocketAddress addr, String user, String note) throws IOException {
		System.out.println(addr + ": \"" + note + "\"");
		SocketChannel conn = SocketChannel.open(addr);
		conn.write(StandardCharsets.UTF_8.encode(note));
		conn.close();
	}
}
