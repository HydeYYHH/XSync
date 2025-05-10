package service;

import java.io.File;
import java.io.IOException;

public interface SyncService {

  SyncService setExpectedChunkSize(int chunkSize);

  SyncService setCacheDir(File cacheDir) throws IOException;

  SyncService setRootDir(File rootDir) throws IOException;

  SyncService authenticate(String username, String password) throws IOException;

  SyncService authenticate() throws IOException;

  SyncService setCompressed(Boolean enabled);

  SyncService setEncrypted(Boolean enabled) throws IOException;

  /** core method */
  Boolean sync(File file);

  /** Delete file from server */
  Boolean delete(File file);
}
