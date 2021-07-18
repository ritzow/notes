package ritzow.notes.desktop;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class NoteUploader {
	public static void upload(InetSocketAddress addr, String user, String note) throws IOException {
		SocketChannel conn = SocketChannel.open(addr);
		var lenBuf = ByteBuffer.allocateDirect(Integer.BYTES);
		var userBuf = StandardCharsets.UTF_8.encode(user);
		System.out.println(userBuf.remaining());
		lenBuf.putInt(userBuf.remaining()).flip();
		conn.write(lenBuf);
		conn.write(userBuf);
		var noteBuf = StandardCharsets.UTF_8.encode(note);
		if(noteBuf.remaining() > 0) {
			lenBuf.clear().putInt(noteBuf.remaining()).flip();
			conn.write(lenBuf);
			conn.write(noteBuf);
		}
		/* Wait for the server to close its socket */
		conn.read(ByteBuffer.allocate(1));
		conn.close();
	}
}
