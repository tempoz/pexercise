package com.github.tempoz.pexercise;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.BlockingQueue;

public class OutputResultRunner implements Runnable {
  FileChannel file;
  BlockingQueue<String> results;

  public OutputResultRunner(Path filepath, BlockingQueue<String> results) throws IOException {
    this.file = FileChannel.open(filepath, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
    this.results = results;
  }

  @Override
  public void run() {
    var out_channel = new StringsToFileChannel(file);
    while (true) {
      String result;
      try {
        result = results.take();
      } catch (InterruptedException e) {
        System.err.println("Interrupted when taking from results queue.");
        e.printStackTrace();
        continue;
      }

      // "" is the poison pill
      if (result.isEmpty()) {
        try {
          out_channel.flush();
        } catch (IOException e) {
          System.err.println("Failed to flush to file.");
          e.printStackTrace();
        }
        try {
          file.close();
        } catch (IOException e) {
          System.err.println("Failed to close file.");
          e.printStackTrace();
        }
        return;
      }

      try {
        out_channel.put(result);
      } catch (IOException e) {
        System.err.println("Failed to write " + result + "to file.");
        e.printStackTrace();
      }
    }
  }
}
