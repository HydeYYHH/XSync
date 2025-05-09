package com.hhst.xsync.utils;

import io.github.bucket4j.Bucket;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import org.jetbrains.annotations.NotNull;

public class RateLimiter {

  private final Boolean enabled;
  private final Bucket limiter;

  public RateLimiter(final long rate) {
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

  public static RateLimiter newInstance(final long rate) {
    return new RateLimiter(rate);
  }

  public RateLimitedOutputStream stream(OutputStream stream) {
    return new RateLimitedOutputStream(stream, limiter, enabled);
  }

  public void limiting(long c) throws IOException {
    if (enabled) {
      try {
        limiter.asBlocking().consume(c);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Rate limiter was interrupted", e);
      }
    }
  }

  public static class RateLimitedOutputStream extends BufferedOutputStream {

    private final Boolean enabled;
    private final Bucket limiter;

    public RateLimitedOutputStream(OutputStream os, Bucket limiter, Boolean enabled) {
      super(os);
      this.limiter = limiter;
      this.enabled = enabled;
    }

    private void limiting(long c) throws IOException {
      if (enabled) {
        try {
          limiter.asBlocking().consume(c);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException("Rate limiter was interrupted", e);
        }
      }
    }

    /**
     * Write bytes data with speed limit.
     *
     * @param b the data.
     */
    @Override
    public void write(@NotNull byte[] b) throws IOException {
      limiting(b.length);
      super.write(b);
    }

    @Override
    public synchronized void write(int b) throws IOException {
      limiting(1);
      super.write(b);
    }

    @Override
    public synchronized void write(@NotNull byte[] b, int off, int len) throws IOException {
      limiting(len);
      super.write(b, off, len);
    }
  }
}
