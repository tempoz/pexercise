package com.github.tempoz.pexercise;

import java.awt.image.BufferedImage;
import java.util.PrimitiveIterator;

public class PixelsFromImage implements Iterable<Integer> {
  private BufferedImage image;

  public PixelsFromImage(BufferedImage image) {
    this.image = image;
  }

  private class PixelIterator implements PrimitiveIterator.OfInt {
    private int pos;
    private final int length;

    public PixelIterator() {
      pos = 0;
      length = image.getWidth() * image.getHeight();
    }

    @Override
    public boolean hasNext() {
      return pos < length;
    }

    @Override
    public int nextInt() {
      final int color = image.getRGB(pos % image.getWidth(), pos / image.getWidth());
      ++pos;
      return color;
    }

  }

  @Override
  public PrimitiveIterator.OfInt iterator() {
    return new PixelIterator();
  }
}
