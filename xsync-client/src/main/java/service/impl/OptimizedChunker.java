package service.impl;

import entity.Chunk;
import io.github.zabuzard.fastcdc4j.external.chunking.IterativeStreamChunkerCore;
import io.github.zabuzard.fastcdc4j.external.chunking.MaskOption;
import io.github.zabuzard.fastcdc4j.internal.chunking.FastCdcChunkerCore;
import io.github.zabuzard.fastcdc4j.internal.chunking.HashTables;
import io.github.zabuzard.fastcdc4j.internal.chunking.MaskGenerator;
import io.github.zabuzard.fastcdc4j.internal.util.Validations;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import utils.Const;

public class OptimizedChunker {

  private Integer expectedChunkSize;

  public Iterable<Chunk> chunk(final InputStream stream, final long size) {
    Objects.requireNonNull(expectedChunkSize);
    Objects.requireNonNull(stream);
    Validations.requirePositiveNonZero(size, "Size");
    Validations.require(stream instanceof BufferedInputStream, "BufferedInputStream");
    return () -> new ChunkerIterator(stream, size, expectedChunkSize);
  }

  public OptimizedChunker setExpectedChunkSize(final int expectedChunkSize) {
    this.expectedChunkSize = expectedChunkSize;
    return this;
  }

  private static final class ChunkerIterator implements Iterator<Chunk> {

    private final IterativeStreamChunkerCore core;

    /** The amount of bytes available in the stream that are subject to be chunked. */
    private final long size;

    /** The data stream to chunk. */
    private final InputStream stream;

    /** The current offset in the data stream, marking the beginning of the next chunk. */
    private long currentOffset;

    /**
     * @param stream The data stream to chunk, not null
     * @param size The amount of bytes available in the stream that are subject to be chunked, the
     *     stream must offer at least that many bytes, positive and not zero
     */
    private ChunkerIterator(
        final InputStream stream, final long size, final int expectedChunkSize) {
      this.stream = Objects.requireNonNull(stream);
      this.size = Validations.requirePositiveNonZero(size, "Size");
      final MaskGenerator maskGenerator =
          new MaskGenerator(
              MaskOption.FAST_CDC,
              Const.DEFAULT_NORMALIZATION_LEVEL,
              expectedChunkSize,
              Const.DEFAULT_MASK_GENERATION_SEED);
      final long maskSmallToUse = maskGenerator.generateSmallMask();
      final long maskLargeToUse = maskGenerator.generateLargeMask();

      core =
          new FastCdcChunkerCore(
              expectedChunkSize,
              (int) (expectedChunkSize * Const.DEFAULT_MIN_SIZE_FACTOR),
              (int) (expectedChunkSize * Const.DEFAULT_MAX_SIZE_FACTOR),
              HashTables.getRtpal(),
              maskSmallToUse,
              maskLargeToUse);
    }

    @Override
    public boolean hasNext() {
      if (currentOffset < size) {
        return true;
      } else {
        try {
          stream.close();
        } catch (IOException e) {
          throw new UncheckedIOException("Error closing stream", e);
        }
        return false;
      }
    }

    @Override
    public Chunk next() {
      if (!hasNext()) {
        throw new NoSuchElementException(
            "The data stream has ended, can not generate another chunk");
      }

      final byte[] data = core.readNextChunk(stream, size, currentOffset);

      final Chunk chunk = new Chunk(data, data.length);

      currentOffset += data.length;
      return chunk;
    }
  }
}
