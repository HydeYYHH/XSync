package service.impl;

import com.github.luben.zstd.Zstd;
import entity.Chunk;
import entity.Metadata;
import entity.Response;
import io.github.zabuzard.fastcdc4j.internal.util.Validations;
import java.io.*;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.DeferredFileOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import service.SyncService;
import utils.Const;
import utils.EncryptionUtils;
import utils.HashUtils;

/**
 * Implementation of {@link SyncService} for synchronizing files between local directories and
 * remote storage. Handles authentication, metadata management, and incremental synchronization
 * using chunking.
 */
public class XSyncServiceImpl implements SyncService {

  /** Logger for this class. */
  private static final Log log = LogFactory.getLog(XSyncServiceImpl.class);

  /** Remote service for communication with the server. */
  private final XSyncRemoteService remoteService;

  /** Expected size of each chunk in bytes (default: 8KB). */
  private int expectedChunkSize = Const.DEFAULT_EXPECTED_CHUNK_SIZE;

  /** Root directory for local file operations. */
  private File rootDir = new File(".");

  /** Cache directory for temporary files and credentials. */
  private File cacheDir = new File("./.cache");

  /** Flag indicating whether compression is enabled. */
  private Boolean isCompressed = false;

  /** Flag indicating whether encryption is enabled. */
  private Boolean isEncrypted = false;

  /** Initializes the remote service. */
  public XSyncServiceImpl() {
    remoteService = new XSyncRemoteService();
  }

  /**
   * Sets the expected chunk size for file chunking.
   *
   * @param chunkSize the chunk size in bytes
   * @return this instance for method chaining
   */
  @Override
  public SyncService setExpectedChunkSize(int chunkSize) {
    expectedChunkSize = chunkSize;
    return this;
  }

  /**
   * Sets the cache directory and creates it if it doesn't exist.
   *
   * @param cacheDir the cache directory
   * @return this instance for method chaining
   * @throws IOException if directory creation fails
   */
  @Override
  public SyncService setCacheDir(File cacheDir) throws IOException {
    Objects.requireNonNull(cacheDir, "cacheDir");
    this.cacheDir = cacheDir;
    if (!cacheDir.exists()) {
      FileUtils.forceMkdir(cacheDir);
    }
    return this;
  }

  /**
   * Sets the root directory and creates it if it doesn't exist.
   *
   * @param rootDir the root directory
   * @return this instance for method chaining
   * @throws IOException if directory creation fails
   */
  @Override
  public SyncService setRootDir(File rootDir) throws IOException {
    Objects.requireNonNull(rootDir, "rootDir");
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
    Validations.require(StringUtils.isNotEmpty(username), "username");
    Validations.require(StringUtils.isNotEmpty(password), "password");
    Response response = remoteService.login(username, password);
    if (response != null && response.isSuccess()) {
      String token = response.getBody().toString();
      // Cache the authentication token
      FileUtils.writeStringToFile(
          new File(cacheDir, Const.credentialFilename), token, StandardCharsets.UTF_8);
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
   * @throws IOException if cached credentials are invalid or missing
   */
  @Override
  public SyncService authenticate() throws IOException {
    File credential = new File(cacheDir, Const.credentialFilename);
    if (!credential.exists()) {
      throw new SyncException("No cached credentials found", null);
    }
    String token = FileUtils.readFileToString(credential, StandardCharsets.UTF_8);
    remoteService.setToken(token);
    return this;
  }

  /**
   * Enables or disables compression for file chunks.
   *
   * @param enabled true to enable compression, false otherwise
   * @return this instance for method chaining
   */
  @Override
  public SyncService setCompressed(Boolean enabled) {
    this.isCompressed = enabled;
    return this;
  }

  /**
   * Enables or disables encryption for file chunks using a secret key.
   *
   * @param enabled true to enable encryption, false otherwise
   * @return this instance for method chaining
   * @throws IOException if the secret key file is missing
   */
  @Override
  public SyncService setEncrypted(Boolean enabled) throws IOException {
    File secretKeyFile = new File(cacheDir, Const.secretKeyFilename);
    if (!secretKeyFile.exists()) {
      throw new SyncException("Secret key file doesn't exist", null);
    }
    this.isEncrypted = enabled;
    String key = FileUtils.readFileToString(secretKeyFile, StandardCharsets.UTF_8);
    EncryptionUtils.initialize(key);
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
      Objects.requireNonNull(file, "file");
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
      try (var is = FileUtils.openInputStream(file);
          var bis = new BufferedInputStream(is, Const.bufferSize)) {
        // Chunk the file for incremental synchronization
        Iterable<Chunk> chunks =
            new SimplifiedChunker()
                .setExpectedChunkSize(expectedChunkSize)
                .chunk(bis, file.length());
        Metadata localMeta = new Metadata();
        localMeta.setFilepath(filePath);
        localMeta.setLastModifiedTime(file.lastModified());
        localMeta.setFilesize(file.length());
        if (remoteMeta == null
            || localMeta.getLastModifiedTime() > remoteMeta.getLastModifiedTime()) {
          return updateRemote(file, localMeta, chunks, remoteMeta);
        } else {
          return updateLocal(file, remoteMeta, chunks);
        }
      }
    } catch (IOException e) {
      log.error("Synchronization failed for file: " + file.getName(), e);
      return false;
    }
  }

  /**
   * Deletes a file from remote storage.
   *
   * @param file the file to delete
   * @return true if deletion succeeds, false otherwise
   */
  @Override
  public Boolean delete(File file) {
    Objects.requireNonNull(file, "file");
    try {
      String filePath = validateFilePath(file);
      log.info("Deleting for " + filePath);
      var rep = remoteService.delete(URLEncoder.encode(filePath, StandardCharsets.UTF_8));
      log.info(
          String.format(
              "Response from server: (statusCode: %d, message: \"%s\")",
              rep.getCode(), rep.getMessage()));
      return rep.isSuccess();
    } catch (IOException e) {
      log.error("Delete file failed: " + file.getName(), e);
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
    Objects.requireNonNull(file, "file");
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
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(remoteMeta, "remoteMeta");
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
    Objects.requireNonNull(file, "file");
    if (remoteMeta == null) {
      log.error("No remote metadata for file: " + file.getName());
      return false;
    }
    log.info("Downloading file: " + file.getName());
    Iterable<Chunk> chunks = () -> remoteService.fetchChunks(remoteMeta.getChunkHashes());
    try {
      File backup = new File(cacheDir, file.getName() + ".backup");
      if (file.exists()) {
        log.info("Backing up file: " + file.getName());
        Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
      // Compute hash incrementally during download
      HashUtils.Hasher hasher = new HashUtils.Hasher("SHA-256");
      for (Chunk chunk : chunks) {
        byte[] data = maybeDecryptAndDecompress(chunk.getData());
        hasher.update(data);
        FileUtils.writeByteArrayToFile(file, data, true);
      }
      String calculatedHash = hasher.getHash();
      if (!calculatedHash.equals(remoteMeta.getFileHash())) {
        log.error("Hash mismatch for file: " + file.getName());
        log.error(
            "Calculated hash: " + calculatedHash + ", remote hash: " + remoteMeta.getFileHash());
        FileUtils.deleteQuietly(file);
        if (backup.exists()) {
          log.info("Restoring backup file: " + backup.getName());
          Files.move(backup.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
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
   * @throws IOException if an I/O error occurs
   */
  private boolean updateRemote(
      File file, Metadata localMeta, Iterable<Chunk> chunks, Metadata remoteMeta)
      throws IOException {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(localMeta, "localMeta");
    Objects.requireNonNull(chunks, "chunks");
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
        byte[] originalData = chunk.getData();
        fileHasher.update(originalData);
        byte[] processedData = maybeEncryptAndCompress(originalData);
        String hash = HashUtils.hash(processedData, Const.hashAlgorithm);
        chunkHashes.add(hash);
        if (!existingChunks.contains(hash)) {
          // Write chunk length and data only if the chunk doesn't exist remotely
          chunksHasher.update(processedData);
          lengthBuffer.clear();
          lengthBuffer.putInt(processedData.length);
          dfo.write(lengthBuffer.array());
          dfo.write(processedData);
          uploadedCount++;
          uploadedSize += processedData.length;
          log.debug(String.format("Wrote chunk [%s], size=%d bytes", hash, processedData.length));
        } else {
          log.debug(String.format("Skipped existing chunk [%s]", hash));
        }
      }
      localMeta.setFileHash(fileHasher.getHash());
      localMeta.setChunkHashes(chunkHashes);
      localMeta.setChunkCount(chunkHashes.size());
      dfo.close();
      try (InputStream is = dfo.toInputStream()) {
        // Upload the input stream to the remote server
        Response rep = remoteService.upload(is, localMeta, chunksHasher.getHash());
        if (!rep.isSuccess()) {
          log.error("Failed to upload file " + file.getName() + ": " + rep.getMessage());
          return false;
        }
      }
    } catch (IOException e) {
      log.error("Failed to update remote file: " + file.getName(), e);
      return false;
    }
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
   * @throws IOException if an I/O error occurs
   */
  private boolean updateLocal(File file, Metadata remoteMeta, Iterable<Chunk> chunks)
      throws IOException {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(remoteMeta, "remoteMeta");
    Objects.requireNonNull(chunks, "chunks");
    Set<String> remoteChunks = new HashSet<>(remoteMeta.getChunkHashes());
    for (Chunk chunk : chunks) {
      byte[] originalData = chunk.getData();
      byte[] processedData = maybeEncryptAndCompress(originalData);
      String hash = HashUtils.hash(processedData, Const.hashAlgorithm);
      if (remoteChunks.contains(hash)) {
        try {
          File chunkFile = new File(cacheDir, hash);
          if (!chunkFile.exists()) {
            FileUtils.writeByteArrayToFile(chunkFile, processedData);
          }
        } catch (IOException e) {
          log.error("Failed to cache chunk: " + hash, e);
          return false;
        }
        remoteChunks.remove(hash);
      }
    }
    log.info("Fetching chunks from server: " + remoteChunks);
    if (remoteChunks.isEmpty()) {
      log.warn("No chunks need to fetch for file: " + file.getName());
      return false;
    }
    long downloadedSize = 0L;
    var iterator = remoteService.fetchChunks(new ArrayList<>(remoteChunks));
    while (iterator.hasNext()) {
      Chunk chunk = iterator.next();
      byte[] data = chunk.getData();
      downloadedSize += data.length;
      FileUtils.writeByteArrayToFile(
          new File(cacheDir, HashUtils.hash(data, Const.hashAlgorithm)), data);
    }
    log.info("Downloaded chunks size: " + downloadedSize + " bytes");
    try {
      File backup = new File(cacheDir, file.getName() + ".backup");
      if (file.exists()) {
        log.info("Backing up file: " + file.getName());
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
        byte[] processedData = FileUtils.readFileToByteArray(chunkFile);
        byte[] data = maybeDecryptAndDecompress(processedData);
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

  /**
   * Processes a chunk for upload by applying encryption and compression if enabled.
   *
   * @param data the chunk data
   * @return the processed chunk data
   */
  private byte[] maybeEncryptAndCompress(byte[] data) {
    if (isEncrypted) {
      data = EncryptionUtils.encrypt(data);
    }
    if (isCompressed) {
      data = Zstd.compress(data, Const.compressionLevel);
    }
    return data;
  }

  /**
   * Processes a chunk for download by applying decompression and decryption if enabled.
   *
   * @param data the chunk data
   * @return the processed chunk data
   */
  private byte[] maybeDecryptAndDecompress(byte[] data) {
    if (isCompressed) {
      data = Zstd.decompress(data, (int) Zstd.decompressedSize(data));
    }
    if (isEncrypted) {
      data = EncryptionUtils.decrypt(data);
    }
    return data;
  }

  /** Custom exception for synchronization errors. */
  private static class SyncException extends RuntimeException {
    public SyncException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
