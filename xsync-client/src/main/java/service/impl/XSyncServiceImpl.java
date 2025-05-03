package service.impl;

import com.google.common.collect.Lists;
import entity.Metadata;
import entity.Response;
import io.github.zabuzard.fastcdc4j.external.chunking.Chunk;
import io.github.zabuzard.fastcdc4j.external.chunking.Chunker;
import io.github.zabuzard.fastcdc4j.external.chunking.ChunkerBuilder;
import io.github.zabuzard.fastcdc4j.external.chunking.ChunkerOption;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import service.SyncService;

/**
 * Implementation of SyncService for synchronizing files between local directories and remote
 * storage. Handles authentication, metadata management, and incremental synchronization using
 * chunking.
 */
public class XSyncServiceImpl implements SyncService {

  private static final Log log = LogFactory.getLog(XSyncServiceImpl.class);
  private final XSyncRemoteService remoteService;
  private int expectedChunkSize = 8 * 1024; // 8KB default
  private File rootDir = new File(".");
  private File cacheDir = new File("./cache");

  /** Initializes the remote service. */
  public XSyncServiceImpl() {
    remoteService = new XSyncRemoteService();
  }

  @Override
  public SyncService setExpectedChunkSize(int chunkSize) {
    expectedChunkSize = chunkSize;
    return this;
  }

  /**
   * Sets the cache directory and initializes directories.
   *
   * @param cacheDir the cache directory
   * @return this instance for method chaining
   */
  @Override
  public SyncService setCacheDir(File cacheDir) {
    this.cacheDir = cacheDir;
    initDirs();
    return this;
  }

  /**
   * Sets the root directory and initializes directories.
   *
   * @param rootDir the root directory
   * @return this instance for method chaining
   */
  @Override
  public SyncService setRootDir(File rootDir) {
    this.rootDir = rootDir;
    initDirs();
    return this;
  }

  /**
   * Authenticates with username and password, caching the token on success.
   *
   * @param username the username
   * @param password the password
   * @return this instance for method chaining
   * @throws IOException if authentication fails
   */
  @Override
  public SyncService authenticate(String username, String password) throws IOException {
    Response response = remoteService.login(username, password);
    if (response != null && response.isSuccess()) {
      String token = response.getBody().toString();
      FileUtils.writeStringToFile(new File(cacheDir, "credential"), token, StandardCharsets.UTF_8);
      remoteService.setToken(token);
      remoteService.build();
    } else {
      throw new SyncException("Authentication failed", null);
    }
    return this;
  }

  /**
   * Authenticates using cached credentials.
   *
   * @return this instance for method chaining
   * @throws IOException if cached credentials are invalid
   */
  @Override
  public SyncService authenticate() throws IOException {
    File credential = new File(cacheDir, "credential");
    if (!credential.exists()) {
      throw new SyncException("No cached credentials found", null);
    }
    String token = FileUtils.readFileToString(credential, StandardCharsets.UTF_8);
    remoteService.setToken(token);
    remoteService.build();
    return this;
  }

  /** Initializes cache and root directories with proper permissions. */
  private void initDirs() {
    try {
      if (!cacheDir.exists()) {
        FileUtils.forceMkdir(cacheDir);
      }
      if (!rootDir.exists()) {
        FileUtils.forceMkdir(rootDir);
      }
    } catch (IOException e) {
      log.error("Failed to initialize directories", e);
      throw new SyncException("Directory initialization failed", e);
    }
  }

  /**
   * Synchronizes a file with remote storage using incremental updates. Uploads local file if newer;
   * downloads remote file if newer.
   *
   * @param file the file to synchronize
   * @return true if synchronization succeeds, false otherwise
   */
  @Override
  public Boolean sync(File file) {
    try {
      String filePath = validateFilePath(file);
      Metadata remoteMeta =
          remoteService
              .fetchMetadata(URLEncoder.encode(filePath, StandardCharsets.UTF_8))
              .orElse(null);
      if (checkIfSyncNeeded(file, remoteMeta)) {
        log.info("File already synchronized: " + file.getName());
        return true;
      }

      if (!file.exists()) {
        return downloadFile(file, remoteMeta);
      }

      Chunker chunker =
          new ChunkerBuilder()
              .setHashMethod("SHA-256")
              .setChunkerOption(ChunkerOption.FAST_CDC)
              .setExpectedChunkSize(expectedChunkSize)
              .build();
      Iterable<Chunk> chunks = chunker.chunk(file.toPath());

      Metadata localMeta = new Metadata();
      localMeta.setFileHash(DigestUtils.sha256Hex(FileUtils.readFileToByteArray(file)));
      localMeta.setFilepath(filePath);
      localMeta.setLastModifiedTime(file.lastModified());
      localMeta.setFilesize(file.length());

      if (remoteMeta == null
          || localMeta.getLastModifiedTime() > remoteMeta.getLastModifiedTime()) {
        return updateRemote(file, localMeta, chunks, remoteMeta);
      } else {
        return updateLocal(file, remoteMeta, chunks);
      }
    } catch (IOException e) {
      log.error("Synchronization failed for file: " + file.getName(), e);
      return false;
    }
  }

  /**
   * Validates that the file path is within the root directory.
   *
   * @param file the file to validate
   * @return the relative file path
   * @throws IOException if the path is invalid
   */
  private String validateFilePath(File file) throws IOException {
    String root = rootDir.getCanonicalPath();
    String target = file.getCanonicalPath();
    if (!target.startsWith(root)) {
      throw new IOException("Illegal file path: " + target);
    }
    return rootDir.toPath().relativize(file.toPath()).normalize().toString();
  }

  /**
   * Checks if synchronization is needed based on last modified time.
   *
   * @param file the local file
   * @param remoteMeta the remote metadata
   * @return true if no sync is needed, false otherwise
   */
  private boolean checkIfSyncNeeded(File file, Metadata remoteMeta) {
    if (remoteMeta == null) {
      return false;
    }
    Long localTime = file.lastModified();
    return localTime.equals(remoteMeta.getLastModifiedTime());
  }

  /**
   * Downloads a file from remote storage and verifies its integrity.
   *
   * @param file the local file to write
   * @param remoteMeta the remote metadata
   * @return true if download succeeds, false otherwise
   */
  private boolean downloadFile(File file, Metadata remoteMeta) {
    if (remoteMeta == null) {
      log.error("No remote metadata for file: " + file.getName());
      return false;
    }

    var chunkIterator = remoteService.fetchChunks(remoteMeta.getChunkHashes()).orElse(null);
    if (chunkIterator == null) {
      log.warn("No chunks fetched for file: " + file.getName());
      return false;
    }

    try {
      if (file.exists()) {
        FileUtils.forceDelete(file);
      }
      while (chunkIterator.hasNext()) {
        Chunk chunk = chunkIterator.next();
        FileUtils.writeByteArrayToFile(file, chunk.getData(), true);
      }
      String calculatedHash = DigestUtils.sha256Hex(FileUtils.readFileToByteArray(file));
      if (!calculatedHash.equals(remoteMeta.getFileHash())) {
        log.error("Hash mismatch for file: " + file.getName());
        FileUtils.deleteQuietly(file);
        return false;
      }
      log.info("File downloaded: " + file.getName());
      return true;
    } catch (IOException e) {
      log.error("Download failed for file: " + file.getName(), e);
      return false;
    }
  }

  /**
   * Uploads a local file to remote storage incrementally.
   *
   * @param file the local file
   * @param localMeta the local metadata
   * @param chunks the file chunks
   * @param remoteMeta the remote metadata
   * @return true if upload succeeds, false otherwise
   */
  private boolean updateRemote(
      File file, Metadata localMeta, Iterable<Chunk> chunks, Metadata remoteMeta) {
    Set<String> existingChunks =
        (remoteMeta != null && remoteMeta.getChunkHashes() != null)
            ? new HashSet<>(remoteMeta.getChunkHashes())
            : new HashSet<>();

    long chunkCount = 0;
    long uploadedSize = 0;
    List<String> chunkHashes = new ArrayList<>();
    for (Chunk chunk : chunks) {
      String hash = chunk.getHexHash();
      if (!existingChunks.contains(hash)) {
        Response uploadResponse = remoteService.uploadChunk(chunk);
        if (!uploadResponse.isSuccess()) {
          log.warn(
              "Chunk upload failed for hash: "
                  + hash
                  + ", message: "
                  + uploadResponse.getMessage());
          return false;
        }
        uploadedSize += chunk.getLength();
        log.debug("Uploaded chunk: " + hash);
      } else {
        log.debug("Chunk exists on remote: " + hash);
      }
      chunkHashes.add(hash);
      chunkCount++;
    }

    Response finishResponse = remoteService.finishUploadChunk();
    if (!finishResponse.isSuccess()) {
      log.warn("Finish upload failed: " + finishResponse.getMessage());
      return false;
    }

    log.info("Uploaded chunks size: " + uploadedSize + " bytes");
    localMeta.setChunkHashes(chunkHashes);
    localMeta.setChunkCount(chunkCount);
    Response metadataResponse = remoteService.uploadMetadata(localMeta);
    if (!metadataResponse.isSuccess()) {
      log.warn("Metadata upload failed: " + metadataResponse.getMessage());
      return false;
    }

    log.info("File uploaded: " + file.getName());
    return true;
  }

  /**
   * Updates the local file with remote chunks.
   *
   * @param file the local file
   * @param remoteMeta the remote metadata
   * @param chunks the local chunks
   * @return true if update succeeds, false otherwise
   */
  private boolean updateLocal(File file, Metadata remoteMeta, Iterable<Chunk> chunks) {
    Set<String> remoteChunks = new HashSet<>(remoteMeta.getChunkHashes());

    for (Chunk chunk : chunks) {
      String hash = chunk.getHexHash();
      if (remoteChunks.contains(hash)) {
        try {
          File chunkFile = new File(cacheDir, hash);
          if (!chunkFile.exists()) {
            FileUtils.writeByteArrayToFile(chunkFile, chunk.getData());
          }
        } catch (IOException e) {
          log.error("Failed to cache chunk: " + hash, e);
          return false;
        }
        remoteChunks.remove(hash);
      }
    }

    var chunkIterator = remoteService.fetchChunks(Lists.newArrayList(remoteChunks)).orElse(null);
    if (chunkIterator == null && !remoteChunks.isEmpty()) {
      log.warn("No chunks fetched for file: " + file.getName());
      return false;
    }

    long downloadedSize = 0L;

    if (chunkIterator != null) {
      while (chunkIterator.hasNext()) {
        Chunk chunk = chunkIterator.next();
        File chunkFile = new File(cacheDir, chunk.getHexHash());
        downloadedSize += chunk.getLength();
        try {
          if (!chunkFile.exists()) {
            FileUtils.writeByteArrayToFile(chunkFile, chunk.getData());
          }
        } catch (IOException e) {
          log.error("Failed to cache chunk: " + chunk.getHexHash(), e);
          return false;
        }
      }
    }
    log.info("Downloaded chunks size: " + downloadedSize + " bytes");
    try {
      File backup = new File(cacheDir, file.getName());
      Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
      for (String hash : remoteMeta.getChunkHashes()) {
        File chunkFile = new File(cacheDir, hash);
        if (!chunkFile.exists()) {
          log.error("Missing chunk: " + hash);
          return false;
        }
        FileUtils.writeByteArrayToFile(file, FileUtils.readFileToByteArray(chunkFile), true);
      }
      String calculatedHash = DigestUtils.sha256Hex(FileUtils.readFileToByteArray(file));
      if (!calculatedHash.equals(remoteMeta.getFileHash())) {
        log.error("Hash mismatch for file: " + file.getName());
        Files.move(backup.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return false;
      }
      if (!file.setLastModified(remoteMeta.getLastModifiedTime())) {
        log.warn(
            String.format(
                "Synchronize file time failed, remote: %d, but local: %d",
                remoteMeta.getLastModifiedTime(), file.lastModified()));
      }
    } catch (IOException e) {
      log.error("Update failed for file: " + file.getName(), e);
      return false;
    }
    log.info("File updated: " + file.getName());
    return true;
  }

  /** Custom exception for synchronization errors. */
  private static class SyncException extends RuntimeException {
    public SyncException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
