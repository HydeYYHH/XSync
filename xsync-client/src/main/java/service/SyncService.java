package service;

import java.io.File;
import java.io.IOException;

public interface SyncService {

  SyncService setExpectedChunkSize(int chunkSize);

  SyncService setCacheDir(File cacheDir);

  SyncService setRootDir(File rootDir);

  SyncService authenticate(String username, String password) throws IOException;

  SyncService authenticate() throws IOException;

  /** core method */
  Boolean sync(File file);
}
