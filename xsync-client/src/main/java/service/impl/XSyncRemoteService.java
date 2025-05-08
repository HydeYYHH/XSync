package service.impl;

import com.google.gson.Gson;
import entity.Chunk;
import entity.Metadata;
import entity.Response;
import io.github.zabuzard.fastcdc4j.internal.util.Validations;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import kong.unirest.core.ContentType;
import kong.unirest.core.RawResponse;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import service.RemoteService;
import utils.Const;

public class XSyncRemoteService implements RemoteService {

  private static final Log log = LogFactory.getLog(XSyncRemoteService.class);
  private final Gson gson = new Gson();

  public XSyncRemoteService() {
    Unirest.config().defaultBaseUrl(Const.defaultUrl).connectTimeout(5000);
  }

  @Override
  public void setToken(String token) {
    Validations.require(StringUtils.isNotEmpty(token), "token cannot be empty");
    Unirest.config().setDefaultHeader("Authorization", "Bearer " + token);
  }

  @Override
  public Response login(String username, String password) {
    Validations.require(StringUtils.isNotEmpty(username), "username cannot be empty");
    Validations.require(StringUtils.isNotEmpty(password), "password cannot be empty");
    return Unirest.post("/user/login")
        .field("email", username)
        .field("password", password)
        .asObject(Response.class)
        .ifFailure(
            error -> {
              throw new UnirestException("Login Failed");
            })
        .getBody();
  }

  @Override
  public Response upload(InputStream stream, Metadata metadata, String hash) {
    Objects.requireNonNull(stream, "stream cannot be null");
    Objects.requireNonNull(metadata, "metadata cannot be null");
    Validations.require(StringUtils.isNotEmpty(hash), "hash cannot be empty");
    return Unirest.post("/chunk/upload/batch")
        .field("hash", hash, ContentType.TEXT_PLAIN.getMimeType())
        .field("hash-algorithm", Const.hashAlgorithm, ContentType.TEXT_PLAIN.getMimeType())
        .field("metadata", gson.toJson(metadata), ContentType.APPLICATION_JSON.getMimeType())
        .field("file", stream, ContentType.APPLICATION_OCTET_STREAM, "chunks")
        .asObject(Response.class)
        .ifFailure(
            error -> {
              throw new UnirestException("Upload Chunks Failed");
            })
        .getBody();
  }

  @Override
  public Iterator<Chunk> fetchChunks(List<String> chunkHashes) {
    Validations.require(CollectionUtils.isNotEmpty(chunkHashes), "chunkHashes cannot be empty");
    var stream =
        Unirest.post("/chunk/fetch/batch")
            .header("Content-Type", ContentType.APPLICATION_JSON.getMimeType())
            .body(gson.toJson(chunkHashes))
            .asObject(RawResponse::getContent)
            .ifFailure(
                error -> {
                  throw new UnirestException("Fetch Chunks Failed");
                })
            .getBody();
    return new ChunkIterator(stream, chunkHashes.size());
  }

  @Override
  public Metadata fetchMetadata(String path) {
    Validations.require(StringUtils.isNotEmpty(path), "path cannot be empty");
    var rep =
        Unirest.get("/metadata/fetch")
            .queryString("path", path)
            .asJson()
            .ifFailure(
                error -> {
                  throw new UnirestException("Fetch Metadata Failed");
                })
            .getBody();
    String jsonStr =
        Optional.ofNullable(rep.getObject().get("body")).map(Object::toString).orElse(null);
    return gson.fromJson(jsonStr, Metadata.class);
  }

  /**
   * Iterator for streaming chunks from a remote response. Implements AutoCloseable to ensure
   * resource cleanup.
   */
  private static class ChunkIterator implements Iterator<Chunk>, AutoCloseable {
    private final DataInputStream inputStream;
    private final int totalChunks;
    private int currentChunk = 0;
    private boolean hasMore = true;

    /**
     * Constructs a ChunkIterator for streaming chunk data.
     *
     * @param inputStream the InputStream from the ResponseBody
     * @param totalChunks the expected number of chunks
     */
    public ChunkIterator(InputStream inputStream, int totalChunks) {
      Objects.requireNonNull(inputStream, "inputStream cannot be null");
      Validations.requirePositive(totalChunks, "totalChunks");
      this.inputStream = new DataInputStream(inputStream);
      this.totalChunks = totalChunks;
    }

    /**
     * Checks if more chunks are available.
     *
     * @return true if more chunks are available, false otherwise
     */
    @Override
    public boolean hasNext() {
      return hasMore && currentChunk < totalChunks;
    }

    /**
     * Reads the next chunk from the stream.
     *
     * @return the next Chunk object
     * @throws NoSuchElementException if no more chunks are available
     */
    @Override
    public Chunk next() {
      if (!hasNext()) {
        throw new NoSuchElementException("No more chunks available");
      }

      try {
        int chunkLength = inputStream.readInt();
        if (chunkLength <= 0) {
          throw new IllegalStateException("Invalid chunk length: " + chunkLength);
        }

        byte[] chunkData = new byte[chunkLength];
        inputStream.readFully(chunkData);

        Chunk chunk = new Chunk(chunkData, chunkLength);
        currentChunk++;
        return chunk;
      } catch (IOException e) {
        hasMore = false;
        log.error("Failed to read chunk", e);
        throw new RuntimeException("Chunk read error: " + e.getMessage(), e);
      }
    }

    /** Closes the underlying stream. */
    @Override
    public void close() {
      try {
        inputStream.close();
      } catch (IOException e) {
        log.error("Failed to close chunk iterator stream", e);
      }
    }
  }
}
