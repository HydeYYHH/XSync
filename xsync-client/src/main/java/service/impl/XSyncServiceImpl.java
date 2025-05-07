package service.impl;

import entity.Chunk;
import entity.Metadata;
import entity.Response;
import java.io.*;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.DeferredFileOutputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import service.SyncService;
import utils.Const;
import utils.HashUtils;

/**
 * Implementation of SyncService for synchronizing files between local directories and remote
 * storage. Handles authentication, metadata management, and incremental synchronization using
 * chunking.
 */
public class XSyncServiceImpl implements SyncService {

  private static final Log log = LogFactory.getLog(XSyncServiceImpl.class);
  private final XSyncRemoteService remoteService;
  private int expectedChunkSize = Const.DEFAULT_EXPECTED_CHUNK_SIZE; // 8KB default
  private File rootDir = new File(".");
  private File cacheDir = new File("./.cache");

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
  public SyncService setCacheDir(File cacheDir) throws IOException {
    this.cacheDir = cacheDir;
    if (!cacheDir.exists()) {
      FileUtils.forceMkdir(cacheDir);
    }
    return this;
  }

  /**
   * Sets the root directory and initializes directories.
   *
   * @param rootDir the root directory
   * @return this instance for method chaining
   */
  @Override
  public SyncService setRootDir(File rootDir) throws IOException {
    this.rootDir = rootDir;
    if (!rootDir.exists()) {
      FileUtils.forceMkdir(rootDir);
    }
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
    return this;
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
      log.info("Fetching metadata for " + filePath);
      Metadata remoteMeta =
          remoteService.fetchMetadata(URLEncoder.encode(filePath, StandardCharsets.UTF_8));
      if (remoteMeta != null && checkIfSyncNeeded(file, remoteMeta)) {
        log.info("File already synchronized: " + file.getName());
        return true;
      }
      if (!file.exists()) {
        return downloadFile(file, remoteMeta);
      }
      try (var is = FileUtils.openInputStream(file)) {
        Iterable<Chunk> chunks =
            new OptimizedChunker().setExpectedChunkSize(expectedChunkSize).chunk(is, file.length());

        Metadata localMeta = new Metadata();

        localMeta.setFilepath(filePath);
        localMeta.setLastModifiedTime(file.lastModified());
        localMeta.setFilesize(file.length());

        if (remoteMeta == null
            || localMeta.getLastModifiedTime() > remoteMeta.getLastModifiedTime()) {
          return updateRemote(file, localMeta, chunks, remoteMeta);
        } else {
          return updateLocal(file, remoteMeta, chunks, is);
        }
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
    log.info("Downloading file: " + file.getName());
    Iterable<Chunk> chunks = () -> remoteService.fetchChunks(remoteMeta.getChunkHashes());

    try {
      File backup = new File(cacheDir, file.getName());
      if (file.exists()) {
        log.info("Backing up file: " + file.getName());
        Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }

      log.info("Downloading file: " + file.getName());
      for (Chunk chunk : chunks) {
        FileUtils.writeByteArrayToFile(file, chunk.getData(), true);
      }
      try (var is = FileUtils.openInputStream(file)) {
        String calculatedHash = Hex.encodeHexString(DigestUtils.sha256(is));
        if (!calculatedHash.equals(remoteMeta.getFileHash())) {
          log.error("Hash mismatch for file: " + file.getName());
          log.error(
              "Calculated hash: " + calculatedHash + ", remote hash: " + remoteMeta.getFileHash());
          FileUtils.deleteQuietly(file);
          if (backup.exists()) {
            // restore backup
            log.info("Restoring backup file: " + backup.getName());
            Files.move(backup.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
          }
          return false;
        }
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
      File file, Metadata localMeta, Iterable<Chunk> chunks, Metadata remoteMeta)
      throws IOException {

    // Initialize existing chunks from remote metadata, or use an empty set if none exist
    Set<String> existingChunks =
        (remoteMeta != null) ? new HashSet<>(remoteMeta.getChunkHashes()) : Collections.emptySet();

    log.info("Updating remote file: " + file.getName());

    int uploadedCount = 0;
    int uploadedSize = 0;
    File tmpFile = new File(cacheDir, file.getName() + ".tmp");
    List<String> chunkHashes = new ArrayList<>();
    try (DeferredFileOutputStream dfo =
        DeferredFileOutputStream.builder()
            .setOutputFile(tmpFile)
            .setThreshold(Const.deferredStreamThreshold)
            .get()) {
      HashUtils.Hasher chunksHasher = new HashUtils.Hasher("SHA-256");
      HashUtils.Hasher fileHasher = new HashUtils.Hasher("SHA-256");
      ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
      for (Chunk chunk : chunks) {
        byte[] data = chunk.getData();
        fileHasher.update(data);
        String hash = HashUtils.hash(data, Const.hashAlgorithm);
        chunkHashes.add(hash);
        if (!existingChunks.contains(hash)) {
          // Write chunk length and data only if the chunk doesn't exist remotely
          chunksHasher.update(data);
          lengthBuffer.clear();
          lengthBuffer.putInt(chunk.length());
          dfo.write(lengthBuffer.array());
          dfo.write(chunk.getData());
          uploadedCount++;
          uploadedSize += chunk.length();
          log.debug(String.format("Wrote chunk [%s], size=%d bytes", hash, chunk.length()));
        } else {
          log.debug(String.format("Skipped existing chunk [%s]", hash));
        }
      }
      localMeta.setFileHash(fileHasher.getHash());
      localMeta.setChunkHashes(chunkHashes);
      localMeta.setChunkCount(chunkHashes.size());
      // Chunks small enough
      dfo.close();
      try (InputStream is = dfo.toInputStream()) {
        // Upload the input stream to the remote server
        Response rep = remoteService.upload(is, localMeta, chunksHasher.getHash());
        // Check if the upload was successful
        if (!rep.isSuccess()) {
          log.error("Failed to upload file " + file.getName() + ": " + rep.getMessage());
          return false;
        }
      }
    } catch (IOException e) {
      log.error("Failed to update remote file: " + file.getName(), e);
      return false;
    }
    // Log upload statistics
    log.info(
        String.format(
            "Uploaded %d chunks (%d bytes) for file %s",
            uploadedCount, uploadedSize, file.getName()));
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
  private boolean updateLocal(
      File file, Metadata remoteMeta, Iterable<Chunk> chunks, InputStream is) throws IOException {
    Set<String> remoteChunks = new HashSet<>(remoteMeta.getChunkHashes());
    for (Chunk chunk : chunks) {
      byte[] data = chunk.getData();
      String hash = HashUtils.hash(data, Const.hashAlgorithm);
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

    is.close();
    log.info("Fetching chunks from server: " + remoteChunks);
    if (remoteChunks.isEmpty()) {
      log.warn("No chunks need to fetch for file: " + file.getName());
      return false;
    }
    long downloadedSize = 0L;
    var iterator = remoteService.fetchChunks(remoteChunks.stream().toList());
    while (iterator.hasNext()) {
      var chunk = iterator.next();
      FileUtils.writeByteArrayToFile(
          new File(cacheDir, HashUtils.hash(chunk.getData(), Const.hashAlgorithm)),
          chunk.getData());
      downloadedSize += chunk.length();
    }
    log.info("Downloaded chunks size: " + downloadedSize + " bytes");
    try {
      log.info("Backing up file: " + file.getName());
      File backup = new File(cacheDir, file.getName());
      if (file.exists()) {
        Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
      log.info("Merging file: " + file.getName());
      HashUtils.Hasher hasher = new HashUtils.Hasher("SHA-256");
      for (String hash : remoteMeta.getChunkHashes()) {
        File chunkFile = new File(cacheDir, hash);
        if (!chunkFile.exists()) {
          log.error("Missing chunk: " + hash);
          return false;
        }
        byte[] data = FileUtils.readFileToByteArray(chunkFile);
        hasher.update(data);
        FileUtils.writeByteArrayToFile(file, data, true);
      }
      String calculatedHash = hasher.getHash();
      if (!calculatedHash.equals(remoteMeta.getFileHash())) {
        log.error("Hash mismatch for file: " + file.getName());
        if (backup.exists()) {
          log.info("Restoring file from backup: " + file.getName());
          Files.move(backup.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
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
