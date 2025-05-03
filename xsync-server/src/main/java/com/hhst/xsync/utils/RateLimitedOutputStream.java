package com.hhst.xsync.utils;

import com.google.common.util.concurrent.RateLimiter;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UnstableApiUsage")
public class RateLimitedOutputStream extends BufferedOutputStream {

  private final Boolean enabled;
  private final RateLimiter limiter;

  /**
   * Constructor.
   *
   * @param rate bytes per second.
   */
  public RateLimitedOutputStream(OutputStream os, double rate) {
    super(os);
    if (rate > 0) {
      enabled = true;
      limiter = RateLimiter.create(rate);
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
      limiter.acquire(b.length);
    }
    super.write(b);
  }

  @Override
  public synchronized void write(int b) throws IOException {
    if (enabled) {
      limiter.acquire(1);
    }
    super.write(b);
  }

  @Override
  public synchronized void write(@NotNull byte[] b, int off, int len) throws IOException {
    if (enabled) {
      limiter.acquire(len);
    }
    super.write(b, off, len);
  }
}
