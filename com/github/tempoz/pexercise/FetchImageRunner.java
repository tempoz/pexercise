package com.github.tempoz.pexercise;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.BlockingQueue;

import javax.imageio.ImageIO;

public class FetchImageRunner implements Runnable {
  private BlockingQueue<String> image_urls;
  private BlockingQueue<Pair<String, BufferedImage>> images;

  public FetchImageRunner(BlockingQueue<String> image_urls, BlockingQueue<Pair<String, BufferedImage>> images) {
    this.image_urls = image_urls;
    this.images = images;
  }

  private static URLConnection connectionFromURL(String url) throws IOException {
    URLConnection connection = new URL(url).openConnection();
    // Handle http -> https redirects
    if (connection.getURL().getProtocol().equals("http") && connection instanceof HttpURLConnection) {
      var http_connection = (HttpURLConnection)connection;
      switch (http_connection.getResponseCode()) {
        case HttpURLConnection.HTTP_MOVED_TEMP:
        case HttpURLConnection.HTTP_MOVED_PERM:
        final String location = http_connection.getHeaderField("Location");
        URL redirect_url = null;
        try {
          redirect_url = new URL(location);
        } catch (MalformedURLException e) {
          // Not a http -> https redirect
        }
        if (redirect_url != null && redirect_url.getProtocol().equals("https")) {
          connection = redirect_url.openConnection();
        }
      }
    }

    return connection;
  }

  @Override
  public void run() {
    String image_url;
    while (true) {
      try {
        image_url = image_urls.take();
      } catch (InterruptedException e) {
        System.err.println("Interrupted when taking image_url from queue.");
        e.printStackTrace();
        continue;
      }

      // "" is the poison pill
      if (image_url.isEmpty()) {
        while (true) {
          try {
            image_urls.put(image_url);
            break;
          } catch (InterruptedException e) {
            System.err.println("Interrupted when re-inserting the poison pill. Retrying...");
            e.printStackTrace();
          }
        }
        return;
      }
      Pair<String, BufferedImage> labeled_image;
      try {
        BufferedImage image;
        for (int retries = 0; ; ++retries) {
            var connection = connectionFromURL(image_url);
          try {
            image = ImageIO.read(connection.getInputStream());
          } catch (NullPointerException e) {
            if (retries < 5) {
              System.err.println("Null pointer Exception when reading " + image_url + " . Retrying...");
              connection.getInputStream().close();
              continue;
            } else {
              System.err.println("Null pointer Exception when reading " + image_url + " . Failing...");
              image = null;
            }
          }
          connection.getInputStream().close();
          break;
        }

        if (image == null) {
          System.err.println("Reading " + image_url + " returned a null image.");
          continue;
        }

        labeled_image = new Pair<String, BufferedImage>(
          image_url,
          image
        );
      } catch (MalformedURLException e) {
        System.err.println(image_url + " is not a valid URL.");
        e.printStackTrace();
        continue;
      } catch (FileNotFoundException e) {
        System.err.println("URL " + image_url + " does not exist.");
        continue;
      } catch (IOException e) {
        System.err.println("Encountered error fetching " + image_url);
        e.printStackTrace();
        continue;
      }

      try {
        images.put(labeled_image);
      } catch (InterruptedException e) {
        System.err.println("Interrupted when putting image retrieved from " +
            image_url);
        e.printStackTrace();
      }
    }
  }
}
