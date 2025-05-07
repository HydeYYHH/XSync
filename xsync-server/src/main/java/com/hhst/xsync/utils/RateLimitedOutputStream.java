package com.hhst.xsync.utils;

import io.github.bucket4j.Bucket;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import org.jetbrains.annotations.NotNull;

public class RateLimitedOutputStream extends BufferedOutputStream {

  private final Boolean enabled;
  private final Bucket limiter;

  /**
   * Constructor.
   *
   * @param rate bytes per second.
   */
  public RateLimitedOutputStream(OutputStream os, long rate) {
    super(os);
    if (rate > 0) {
      enabled = true;
      limiter =
          Bucket.builder()
              .addLimit(limit -> limit.capacity(rate).refillIntervally(rate, Duration.ofSeconds(1)))
              .build();
    } else {
      enabled = false;
      limiter = null;
    }
  }

  /**
   * Write bytes data with speed limit.
   *
   * @param b the data.
   */
  @Override
  public void write(@NotNull byte[] b) throws IOException {
    if (enabled) {
      try {
        limiter.asBlocking().consume(b.length);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Rate limiter was interrupted", e);
      }
    }
    super.write(b);
  }

  @Override
  public synchronized void write(int b) throws IOException {
    if (enabled) {
      try {
        limiter.asBlocking().consume(1);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Rate limiter was interrupted", e);
      }
    }
    super.write(b);
  }

  @Override
  public synchronized void write(@NotNull byte[] b, int off, int len) throws IOException {
    if (enabled) {
      try {
        limiter.asBlocking().consume(len);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Rate limiter was interrupted", e);
      }
    }
    super.write(b, off, len);
  }
}
