package com.github.tempoz.pexercise;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.BlockingQueue;

public class ReadImageURLRunner implements Runnable {
  FileChannel file;
  BlockingQueue<String> image_urls;

  public ReadImageURLRunner(Path image_url_filepath, BlockingQueue<String> image_urls) throws IOException {
    this.file = FileChannel.open(image_url_filepath, StandardOpenOption.READ);
    this.image_urls = image_urls;
  }

  private void addToImageURLs(CharSequence image_url) {
    try {
      image_urls.put(image_url.toString());
    } catch (InterruptedException e) {
      System.err.println("Interrupted putting " +
                          image_url +
                          " into the image_urls queue.");
      e.printStackTrace();
    }
  }

  @Override
  public void run() {
    for (CharSequence line : new LinesFromFileChannel(file)) {
      if(line.length() != 0) {
        addToImageURLs(line);
      }
    }
    try {
      file.close();
    } catch (IOException e) {
      System.err.println("Failed to close input file.");
      e.printStackTrace();
    }
  }
}
