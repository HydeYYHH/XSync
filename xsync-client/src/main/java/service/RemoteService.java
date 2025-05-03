package service;

import entity.Metadata;
import entity.Response;
import io.github.zabuzard.fastcdc4j.external.chunking.Chunk;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * RemoteService defines operations to communicate with the remote server for uploading and fetching
 * file-related data such as chunks and metadata.
 */
public interface RemoteService {

  void setToken(String token);

  Response login(String username, String password);

  /**
   * Upload a chunk to the server.
   *
   * @param chunk the chunk to be uploaded
   * @return server response indicating success or failure
   */
  Response uploadChunk(Chunk chunk);

  /**
   * Fetch the metadata of a file from the server.
   *
   * @param fileReference the file reference
   * @return metadata object retrieved from the server
   */
  Optional<Metadata> fetchMetadata(String fileReference);

  /**
   * Fetch a list of chunks from the server using their hash values.
   *
   * @param chunkHashes list of chunk hashes
   * @return list of Chunk objects corresponding to the given hashes
   */
  Optional<Iterator<Chunk>> fetchChunks(List<String> chunkHashes);

  /**
   * Upload metadata to the server.
   *
   * @param metadata metadata object representing file information
   * @return server response indicating success or failure
   */
  Response uploadMetadata(Metadata metadata);
}
