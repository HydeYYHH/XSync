package service.impl;

import com.google.gson.Gson;
import entity.Metadata;
import entity.Response;
import io.github.zabuzard.fastcdc4j.external.chunking.Chunk;
import io.github.zabuzard.fastcdc4j.internal.chunking.SimpleChunk;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.Setter;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import service.ApiService;
import service.RemoteService;

/**
 * Implementation of RemoteService for interacting with a remote storage API. Handles chunk uploads,
 * downloads, metadata operations, and authentication.
 */
public class XSyncRemoteService implements RemoteService {

  private static final Log log = LogFactory.getLog(XSyncRemoteService.class);
  private static final String BASE_URL = "http://127.0.0.1:8080/";
  private static final int MAX_BATCH_SIZE = 4 * 1024 * 1024; // 4MB

  private final Set<Chunk> chunkBatch = new HashSet<>();
  private final Gson gson = new Gson();
  @Setter private String token;
  private ApiService apiService;
  private int batchSize = 0;

  /** Initializes the API service with default Retrofit configuration. */
  public XSyncRemoteService() {
    apiService =
        new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService.class);
  }

  /**
   * Rebuilds the API service with authentication token. Adds interceptors for logging and
   * token-based authorization.
   */
  public void build() {
    OkHttpClient client =
        new OkHttpClient.Builder()
            .addInterceptor(
                new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.NONE))
            .addInterceptor(
                chain -> {
                  Request request = chain.request();
                  if (token != null && !request.url().encodedPath().endsWith("/login")) {
                    request =
                        request.newBuilder().addHeader("Authorization", "Bearer " + token).build();
                  }
                  return chain.proceed(request);
                })
            .build();
    apiService =
        new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService.class);
  }

  /**
   * Authenticates with the remote server using username and password.
   *
   * @param username the username
   * @param password the password
   * @return a Response indicating success or failure
   */
  @Override
  public Response login(String username, String password) {
    try {
      Response response = apiService.login(username, password).execute().body();
      return response != null ? response : new Response(500, "Login failed: no response", null);
    } catch (IOException e) {
      log.error("Login failed", e);
      return new Response(500, "Login failed: " + e.getMessage(), null);
    }
  }

  /**
   * Uploads a chunk to the batch for later submission. If batch size exceeds limit, triggers batch
   * upload.
   *
   * @param chunk the chunk to upload
   * @return a Response indicating success or failure
   */
  @Override
  public synchronized Response uploadChunk(Chunk chunk) {
    int chunkSize = 4 + chunk.getLength(); // 4 bytes for length prefix
    if (batchSize + chunkSize > MAX_BATCH_SIZE) {
      Response response = uploadBatch();
      if (!response.isSuccess()) {
        return response;
      }
    }
    chunkBatch.add(chunk);
    batchSize += chunkSize;
    return new Response(200, "Chunk added to batch", null);
  }

  /**
   * Completes the batch upload process if any chunks remain.
   *
   * @return a Response indicating success or failure
   */
  public synchronized Response finishUploadChunk() {
    if (chunkBatch.isEmpty()) {
      return new Response(200, "No chunks to upload", null);
    }
    return uploadBatch();
  }

  /**
   * Uploads all chunks in the current batch to the remote server.
   *
   * @return a Response indicating success or failure
   */
  private synchronized Response uploadBatch() {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      for (Chunk chunk : chunkBatch) {
        outputStream.write(ByteBuffer.allocate(4).putInt(chunk.getLength()).array());
        outputStream.write(chunk.getData());
      }
      byte[] data = outputStream.toByteArray();
      RequestBody body = RequestBody.create(data, MediaType.parse("application/octet-stream"));
      Response response =
          apiService
              .uploadBatch(
                  RequestBody.create(
                      DigestUtils.sha256Hex(data), MediaType.parse("text/plain; charset=UTF-8")),
                  RequestBody.create("SHA-256", MediaType.parse("text/plain; charset=UTF-8")),
                  MultipartBody.Part.createFormData("file", "chunks", body))
              .execute()
              .body();

      chunkBatch.clear();
      batchSize = 0;
      return response != null
          ? response
          : new Response(500, "Batch upload failed: no response", null);
    } catch (IOException e) {
      log.error("Batch upload failed", e);
      return new Response(500, "Batch upload failed: " + e.getMessage(), null);
    }
  }

  /**
   * Uploads metadata for a file to the remote server.
   *
   * @param metadata the metadata to upload
   * @return a Response indicating success or failure
   */
  @Override
  public synchronized Response uploadMetadata(Metadata metadata) {
    try {
      Response response =
          apiService
              .upsertMetadata(
                  URLEncoder.encode(metadata.getFilepath(), StandardCharsets.UTF_8), metadata)
              .execute()
              .body();
      return response != null
          ? response
          : new Response(500, "Metadata upload failed: no response", null);
    } catch (IOException e) {
      log.error("Metadata upload failed", e);
      return new Response(500, "Metadata upload failed: " + e.getMessage(), null);
    }
  }

  /**
   * Fetches metadata for a file from the remote server.
   *
   * @param filePath the encoded file path
   * @return an Optional containing the metadata, or empty if not found
   */
  @Override
  public synchronized Optional<Metadata> fetchMetadata(String filePath) {
    try {
      Response response = apiService.fetchMetadata(filePath).execute().body();
      if (response != null && response.getBody() != null) {
        return Optional.of(gson.fromJson(gson.toJson(response.getBody()), Metadata.class));
      }
      return Optional.empty();
    } catch (IOException e) {
      log.error("Metadata fetch failed for path: " + filePath, e);
      return Optional.empty();
    }
  }

  /**
   * Fetches chunks from the remote server based on their hashes.
   *
   * @param chunkHashes the list of chunk hashes to fetch
   * @return an Optional containing an Iterator of Chunks, or empty if fetching fails
   * @throws IllegalArgumentException if chunkHashes is null or empty
   */
  @Override
  public synchronized Optional<Iterator<Chunk>> fetchChunks(List<String> chunkHashes) {
    if (chunkHashes == null || chunkHashes.isEmpty()) {
      throw new IllegalArgumentException("Chunk hashes cannot be null or empty");
    }

    try {
      retrofit2.Response<ResponseBody> retrofitResponse =
          apiService.fetchBatch(chunkHashes).execute();
      if (!retrofitResponse.isSuccessful()) {
        log.error("Chunk fetch failed with status: " + retrofitResponse.code());
        return Optional.empty();
      }

      ResponseBody responseBody = retrofitResponse.body();
      if (responseBody == null) {
        log.error("Chunk fetch response body is null");
        return Optional.empty();
      }

      return Optional.of(
          new ChunkIterator(responseBody, responseBody.byteStream(), chunkHashes.size()));
    } catch (IOException e) {
      log.error("Failed to fetch chunks", e);
      return Optional.empty();
    }
  }

  /**
   * Iterator for streaming chunks from a remote response. Implements AutoCloseable to ensure
   * resource cleanup.
   */
  private static class ChunkIterator implements Iterator<Chunk>, AutoCloseable {
    private final ResponseBody responseBody;
    private final DataInputStream inputStream;
    private final int totalChunks;
    private int currentChunk = 0;
    private long offset = 0;
    private boolean hasMore = true;

    /**
     * Constructs a ChunkIterator for streaming chunk data.
     *
     * @param responseBody the Retrofit ResponseBody containing chunk data
     * @param inputStream the InputStream from the ResponseBody
     * @param totalChunks the expected number of chunks
     */
    public ChunkIterator(ResponseBody responseBody, InputStream inputStream, int totalChunks) {
      this.responseBody = responseBody;
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

        Chunk chunk = new SimpleChunk(chunkData, offset, DigestUtils.sha256(chunkData));
        offset += chunkLength;
        currentChunk++;
        return chunk;
      } catch (IOException e) {
        hasMore = false;
        log.error("Failed to read chunk", e);
        throw new RuntimeException("Chunk read error: " + e.getMessage(), e);
      }
    }

    /** Closes the underlying stream and response body. */
    @Override
    public void close() {
      try {
        inputStream.close();
      } catch (IOException e) {
        log.error("Failed to close chunk iterator stream", e);
      }
      responseBody.close();
    }
  }
}
