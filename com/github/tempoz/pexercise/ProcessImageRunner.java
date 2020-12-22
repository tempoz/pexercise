package com.github.tempoz.pexercise;

import java.awt.image.BufferedImage;
// import java.util.HashMap;
import java.util.concurrent.BlockingQueue;

public class ProcessImageRunner implements Runnable {
  private BlockingQueue<Pair<String, BufferedImage>> images;
  private BlockingQueue<String> results;

  // Better to not have to re-allocate these between
  // calls to processImage, so declaring / instantiating
  // them here.

  // Array of of green-blue byte pairs bucketed by red byte
  // Use ints because java doesn't have unsigned types,
  // and fixing sign-padding after casting is a mess.
  private IntList[] red_buckets = new IntList[256];

  // Array of blue bytes bucketed by green byte
  // Use shorts because java doesn't have unsigned types,
  // and fixing sign-padding after casting is a mess.
  private ShortList[] green_buckets = new ShortList[256];

  // Array of the count of pixels encountered bucketed by blue byte
  // Use shorts because java doesn't have unsigned types,
  // and fixing sign-padding after casting is a mess.
  private short[] blue_counts = new short[256];

  private short[] next_red = new short[256];
  private short[] next_green = new short[256];
  private short[] next_blue = new short[256];

  public ProcessImageRunner(BlockingQueue<Pair<String, BufferedImage>> images, BlockingQueue<String> results) {
    this.images = images;
    this.results = results;
  }

  /*
  // HashMap implementation. Much, much slower.
  public int[] processImageHashMap(String image_url, BufferedImage image) {
    var color_counts = new HashMap<Integer, Integer>();

    for (var pixel : new PixelsFromImage(image)) {
      color_counts.compute(pixel & 0xffffff, (color, count) -> count == null ? 1 : count + 1);
    }

    int[] max_colors = new int[4];
    int[] max_counts = new int[4];
    for (var entry : color_counts.entrySet()) {
      max_colors[3] = entry.getKey();
      max_counts[3] = entry.getValue();
      for (int i = 3; i > 0 && max_counts[i] > max_counts[i - 1]; --i) {
        int color = max_colors[i];
        max_colors[i] = max_colors[i - 1];
        max_colors[i - 1] = color;

        int count = max_counts[i];
        max_counts[i] = max_counts[i - 1];
        max_counts[i - 1] = count;
      }

    }

    correctIfZeroCounts(max_colors, max_counts); 

    return max_colors;

  }
  */

  public int[] processImage(String image_url, BufferedImage image) {
    int[] max_colors = new int[3];
    int[] max_counts = new int[3];

    // We're going to kinda-sort-of radix sort the pixels.
    // Since we don't actually need all the values at the end,
    // and we don't care about the order, just the grouping,
    // we can take some shortcuts.
    var pixel_iter = new PixelsFromImage(image).iterator();

    short last_red = -1;
    while (pixel_iter.hasNext()) {
      int pixel = pixel_iter.nextInt();
      short red = (short) (pixel >>> 16 & 0xff);
      var green_blue_node = new IntList(pixel & 0xffff);
      if (red_buckets[red] == null) {
        next_red[red] = last_red;
        last_red = red;
        red_buckets[red] = green_blue_node;
      } else {
        red_buckets[red].next = green_blue_node;
      }
    }

    for (short red = last_red; red != -1; red = next_red[red]) {
      short last_green = -1;
      for(var green_blue_list = red_buckets[red]; green_blue_list != null; green_blue_list = green_blue_list.next) {
        short green = (short) (green_blue_list.value >>> 8 & 0xff);
        var blue_node = new ShortList((short) (green_blue_list.value & 0xff));
        if (green_buckets[green] == null) {
          next_green[green] = last_green;
          last_green = green;
          green_buckets[green] = blue_node;
        } else {
          green_buckets[green].next = blue_node;
        }

      }

      for (short green = last_green; green != -1; green = next_green[green]) {
        short last_blue = -1;
        for (var blue_list = green_buckets[green]; blue_list != null; blue_list = blue_list.next) {
          final short blue = blue_list.value;
          if (blue_counts[blue] == 0) {
            next_blue[blue] = last_blue;
            last_blue = blue;
          }
          ++blue_counts[blue];
        }

        for(short blue = last_blue; blue != -1; blue = next_blue[blue]) {
          final int blue_count = blue_counts[blue];
          // Calculate index arithmetically to avoid branch misprediction
          final int index = 3 - (
            ((max_counts[2] - blue_count) >>> 23 & 1) <<
            ((max_counts[1] - blue_count) >>> 23 & 1) |
            ((max_counts[0] - blue_count) >>> 23 & 1));
          switch (index) {
            case 0:
              max_counts[index + 2] = max_counts[index + 1];
              max_colors[index + 2] = max_colors[index + 1];
            case 1:
              max_counts[index + 1] = max_counts[index];
              max_colors[index + 1] = max_colors[index];
            case 2:
              max_counts[index] = blue_count;
              max_colors[index] = (red << 8 | green) << 8 | blue;
            default:
              blue_counts[blue] = 0;
          }
        }
        green_buckets[green] = null;
      }
      red_buckets[red] = null;
    }

   correctIfZeroCounts(max_colors, max_counts); 

    return max_colors;
  }

  public static void correctIfZeroCounts(int[] max_colors, int[] max_counts) {
    // If no colors are encountered (image size is zero),
    // output [0x0, 0xbad, 0x0]. It is impossible for valid
    // output to contain colors in an A, B, A pattern, so
    // this is easily distinguished as an error case.
    if (max_counts[0] == 0) {
      max_colors[1] = 0xBAD;
      return;
    }
    
    // If fewer than 3 colors were encountered, repeat the
    // first color to fill to 3 colors. It is impossible for an
    // image with more than three colors to 
    if (max_counts[2] == 0) {
      max_colors[2] = max_counts[1] == 0 ? max_colors[0] : max_colors[1];
      max_colors[1] = max_colors[0];
    }
  }

  @Override
  public void run() {
    while (true) {
      Pair<String, BufferedImage> labeled_image;
      try {
        labeled_image = images.take();
      } catch (InterruptedException e) {
        System.err.println("Interrupted taking image from the image queue.");
        e.printStackTrace();
        continue;
      }

      // "" key is the poison pill
      if (labeled_image.key.isEmpty()) {
        while (true) {
          try {
            images.put(labeled_image);
            break;
          } catch (InterruptedException e) {
            System.err.println("Interrupted when re-inserting the poison pill. Retrying...");
            e.printStackTrace();
          }
        }
        return;
      }

      if (labeled_image.value == null)
      {
        System.err.println("Image " + labeled_image.key + " is a null image.");
        continue;
      }

      int[] max_colors = processImage(labeled_image.key, labeled_image.value);
      // int[] max_colors = processImageHashMap(labeled_image.key, labeled_image.value);

      final String result = String.format("%s,#%06X,#%06X,#%06X%s", labeled_image.key, max_colors[0], max_colors[1],
          max_colors[2], System.lineSeparator());

      try {
        results.put(result);
      } catch (InterruptedException e) {
        System.err.println("Interrupted putting " + result + " into the result queue.");
        e.printStackTrace();
      }
    }
  }
}
