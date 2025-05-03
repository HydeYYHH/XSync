package service;

import entity.Metadata;
import entity.Response;
import java.util.List;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {

  // --- ChunkController Endpoints ---

  /**
   * Fetch a chunk by hash.
   *
   * @param hash The chunk hash.
   * @return ResponseBody containing the chunk data (streamed).
   */
  @GET("/chunk/fetch/{hash}")
  @Streaming
  Call<ResponseBody> fetch(@Path("hash") String hash);

  /**
   * Fetch multiple chunks by hashes (sent in request body).
   *
   * @param hashes List of chunk hashes.
   * @return ResponseBody containing the fetched chunks (streamed).
   */
  @POST("/chunk/fetch/batch")
  @Streaming
  Call<ResponseBody> fetchBatch(@Body List<String> hashes);

  /**
   * Upload a single chunk.
   *
   * @param hash The chunk hash.
   * @param hashAlgorithm The hashing algorithm used.
   * @param file The file part to upload.
   * @return Response indicating upload success.
   */
  @Multipart
  @POST("/chunk/upload")
  Call<Response> upload(
      @Part("hash") String hash,
      @Part("hash-algorithm") String hashAlgorithm,
      @Part MultipartBody.Part file);

  /**
   * Upload a compressed file containing multiple chunks.
   *
   * @param hash The hash of the compressed file.
   * @param hashAlgorithm The hashing algorithm used.
   * @param file The compressed file part.
   * @return Response indicating batch upload success.
   */
  @Multipart
  @POST("/chunk/upload/batch")
  Call<Response> uploadBatch(
      @Part("hash") RequestBody hash,
      @Part("hash-algorithm") RequestBody hashAlgorithm,
      @Part MultipartBody.Part file);

  // --- MetadataController Endpoints ---

  /**
   * Fetch metadata for a filepath.
   *
   * @param filepath The filepath.
   * @return Response containing the metadata.
   */
  @GET("/metadata/fetch")
  Call<Response> fetchMetadata(@Query("path") String filepath);

  /**
   * Update metadata for a filepath.
   *
   * @param filepath The filepath.
   * @param metadata The metadata to update.
   * @return Response indicating update success.
   */
  @PUT("/metadata/upsert")
  Call<Response> upsertMetadata(@Query("path") String filepath, @Body Metadata metadata);

  /** Login */
  @FormUrlEncoded
  @POST("/user/login")
  Call<Response> login(@Field("email") String email, @Field("password") String password);
}
