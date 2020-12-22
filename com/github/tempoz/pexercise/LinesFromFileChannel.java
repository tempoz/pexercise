package com.github.tempoz.pexercise;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Iterator;

public class LinesFromFileChannel implements Iterable<CharSequence> {
  private FileChannel file;
  private final int buffer_size;
  private Charset ascii_charset = Charset.forName("ASCII");

  public LinesFromFileChannel(FileChannel file, int buffer_size) {
    this.file = file;
    this.buffer_size = buffer_size;
  }

  public LinesFromFileChannel(FileChannel file) {
    this(file, 4 * 1024);
  }

  private static CharSequence strippedLine(CharSequence line) {
    return strippedLine(line, 0);
  }

  private static CharSequence strippedLine(CharSequence line, int line_start) {
    return strippedLine(line, line_start, line.length());
  }

  private static CharSequence strippedLine(CharSequence line, int line_start, int line_end) {

    final int end = line_end > line_start && line.charAt(line_end - 1) == '\r'
      ? line_end - 1
      : line_end;
    return line.subSequence(line_start, end);
  }

  private CharSequence decodedSubSequence(ByteBuffer buffer, int start, int end) {
    ByteBuffer slice = buffer.slice();
    slice.position(start);
    slice.limit(end);
    return ascii_charset.decode(slice);
  }

  private class LinesIterator implements Iterator<CharSequence> {
    private ByteBuffer buffer;
    private int bytes_read;
    private int pos;

    public LinesIterator() {
      this.buffer = ByteBuffer.allocateDirect(buffer_size);
      this.bytes_read = 0;
      this.pos = 0;
    }

    private void readNextBlock() {
      pos = 0;
      do {
        try {
          buffer.position(0);
          bytes_read = file.read(buffer);
          buffer.position(0);
        } catch (IOException e) {
          System.err.println("Encountered exception reading from file.");
          e.printStackTrace();
          bytes_read = -1;
        }
      } while (bytes_read == 0);
    }

    private void advancePosToNewLine() {
      while (pos < bytes_read && buffer.get(pos) != (byte)'\n') {
        ++pos;
      }
    }

    @Override
    public boolean hasNext() {
      if (pos < bytes_read) {
        return true;
      }
      readNextBlock();
      if (bytes_read == -1) {
        return false;
      }
      return true;
    }

    @Override
    public CharSequence next() {
      CharSequence next_line;
      int start = pos;
      advancePosToNewLine();
      if (pos == bytes_read) {
        StringBuilder fragment = new StringBuilder();
        while (pos == bytes_read) {
          fragment.append(decodedSubSequence(buffer, start, bytes_read));
          readNextBlock();
          start = 0;
          advancePosToNewLine();
        }
        fragment.append(decodedSubSequence(buffer, 0, pos));
        next_line = strippedLine(fragment);
      } else {
        next_line = strippedLine(decodedSubSequence(buffer, start, pos));
      }
      ++pos;
      return next_line;
    }

  }

  @Override
  public Iterator<CharSequence> iterator() {
    return new LinesIterator();
  }
  
}
