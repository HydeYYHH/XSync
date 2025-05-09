package com.hhst.xsync.config;

import com.hhst.xsync.entity.Chunk;
import com.hhst.xsync.service.IChunkService;
import com.hhst.xsync.service.ObjectStorageService;
import java.util.List;
import java.util.concurrent.CompletionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@Slf4j
public class ChunksCleaner {

  @Autowired private ObjectStorageService storageService;
  @Autowired private IChunkService chunkService;

  /** Remove all isolated chunks in scheduled */
  @Scheduled(fixedRateString = "${xsync.chunks-cleaner.rate}")
  @Transactional
  public void clean() {
    List<Chunk> isolatedChunks = chunkService.getIsolatedChunks();
    if (isolatedChunks.isEmpty()) return;

    try {
      storageService.removeObjects(isolatedChunks.stream().map(Chunk::getHash).toList()).join();
    } catch (CompletionException e) {
      log.error("Failed to remove isolated chunks from storage", e.getCause());
      throw new RuntimeException("Storage deletion failed", e.getCause());
    }

    chunkService.removeBatchByIds(isolatedChunks);
    log.info("Cleaned {} isolated chunks", isolatedChunks.size());
  }
}
