package com.github.tempoz.pexercise;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

public class StringsToFileChannel {
  private FileChannel file;
  private ByteBuffer buffer;
  private Charset ascii_charset = Charset.forName("ASCII");

  public StringsToFileChannel(FileChannel file, int buffer_size) {
    this.file = file;
    this.buffer = ByteBuffer.allocateDirect(buffer_size);
  }

  public StringsToFileChannel(FileChannel file) {
    this(file, 4 * 1024);
  }

  public void put(String s) throws IOException {
    ByteBuffer encoded = ascii_charset.encode(s);
    if (encoded.limit() > buffer.remaining()) {
      final int limit = encoded.limit();
      encoded.limit(buffer.remaining());
      buffer.put(encoded);
      encoded.limit(limit);

      buffer.position(0);
      file.write(buffer);
      buffer.position(0);
    }
    buffer.put(encoded);
  }

  public void flush() throws IOException {
    final int position = buffer.position();
    buffer.position(0);
    buffer.limit(position);
    file.write(buffer);
    buffer.position(0);
    buffer.limit(buffer.capacity());
  }
}
