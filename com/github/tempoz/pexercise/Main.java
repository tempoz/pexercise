package com.github.tempoz.pexercise;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.FileSystems;
// import java.time.Duration;
// import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class Main {
  public static void main(String[] args) throws IOException, InterruptedException {

    // Instant start = Instant.now();

    // Start threads and initialize queues

    BlockingQueue<String> image_urls = new ArrayBlockingQueue<String>(64);
    Thread read_image_thread = new Thread(
        new ReadImageURLRunner(FileSystems.getDefault().getPath("input.txt"), image_urls));
    read_image_thread.start();

    BlockingQueue<Pair<String, BufferedImage>> images = new ArrayBlockingQueue<Pair<String, BufferedImage>>(16);
    Thread[] fetch_image_threads = new Thread[8];
    for (int i = 0; i < fetch_image_threads.length; i++) {
      fetch_image_threads[i] = new Thread(new FetchImageRunner(image_urls, images));
      fetch_image_threads[i].start();
    }

    BlockingQueue<String> results = new ArrayBlockingQueue<String>(64);
    Thread[] process_image_threads = new Thread[4];
    for (int i = 0; i < process_image_threads.length; i++) {
      process_image_threads[i] = new Thread(new ProcessImageRunner(images, results));
      process_image_threads[i].start();
    }

    Thread output_result_thread = new Thread(
      new OutputResultRunner(FileSystems.getDefault().getPath("output.csv"), results));
    output_result_thread.start();

    // Join threads and poison queues

    read_image_thread.join();
    image_urls.put("");

    for (Thread fetch_image_thread : fetch_image_threads) {
      fetch_image_thread.join();
    }
    images.put(new Pair<String, BufferedImage>("", null));

    for (Thread process_image_thread : process_image_threads) {
      process_image_thread.join();
    }
    results.put("");

    output_result_thread.join();

    // Instant end = Instant.now();
    // System.out.println("Total Execution time: " + Duration.between(start, end).toString());

  }
}
