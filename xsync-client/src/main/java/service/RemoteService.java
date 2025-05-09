package service;

import entity.Chunk;
import entity.Metadata;
import entity.Response;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

/**
 * RemoteService defines operations to communicate with the remote server for uploading and fetching
 * file-related data such as chunks and metadata.
 */
public interface RemoteService {

  void setToken(String token);

  Response login(String username, String password);

  /**
   * Upload chunk and metadata to the server.
   *
   * @return server response indicating success or failure
   */
  Response upload(InputStream stream, Metadata metadata, String hash);

  /**
   * Fetch a list of chunks from the server using their hash values.
   *
   * @param chunkHashes list of chunk hashes
   * @return list of Chunk objects corresponding to the given hashes
   */
  Iterator<Chunk> fetchChunks(List<String> chunkHashes);

  /**
   * Fetch the metadata of a file from the server.
   *
   * @param path the file reference path
   * @return metadata object retrieved from the server
   */
  Metadata fetchMetadata(String path);

  /**
   * Delete the file from server.
   * @param path the filepath of the file.
   * @return server response indicating success or failure
   */
  Response delete(String path);
}
