package com.hhst.xsync.service.impl;

import com.hhst.xsync.config.MinioProperties;
import com.hhst.xsync.service.ObjectStorageService;
import io.minio.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MinioService implements ObjectStorageService {

  @Autowired private MinioClient client;
  @Autowired private MinioProperties properties;

  @Autowired
  @Qualifier("minioExecutor")
  private Executor executor;

  @Override
  public CompletableFuture<Void> putObject(String name, byte[] data) {
    return CompletableFuture.runAsync(
        () -> {
          try {
            client.putObject(
                PutObjectArgs.builder().bucket(properties.getBucket()).object(name).stream(
                        new ByteArrayInputStream(data), data.length, -1)
                    .contentType("application/octet-stream")
                    .build());
          } catch (Exception e) {
            throw new CompletionException(e);
          }
        },
        executor);
  }

  @Override
  public CompletableFuture<byte[]> getObject(String name) {
    return CompletableFuture.supplyAsync(
        () -> {
          try (InputStream is =
              client.getObject(
                  GetObjectArgs.builder().bucket(properties.getBucket()).object(name).build())) {
            return IOUtils.toByteArray(is);
          } catch (Exception e) {
            throw new CompletionException(e);
          }
        },
        executor);
  }

  @Override
  public CompletableFuture<Void> removeObject(String name) {
    return CompletableFuture.runAsync(
        () -> {
          try {
            client.removeObject(
                RemoveObjectArgs.builder().bucket(properties.getBucket()).object(name).build());
          } catch (Exception e) {
            throw new CompletionException(e);
          }
        },
        executor);
  }

  @Override
  public CompletableFuture<Void> removeObjects(List<String> names) {
    return CompletableFuture.runAsync(
        () -> {
          try {
            Iterable<Result<DeleteError>> results =
                client.removeObjects(
                    RemoveObjectsArgs.builder()
                        .bucket(properties.getBucket())
                        .objects(names.stream().map(DeleteObject::new).toList())
                        .build());
            // Check results
            for (Result<DeleteError> result : results) {
              try {
                DeleteError error = result.get();
                log.error(
                    "Failed to delete object: {} with error: {}",
                    error.objectName(),
                    error.message());
              } catch (Exception e) {
                log.error("Error processing deletion result for object", e);
              }
            }
          } catch (Exception e) {
            throw new CompletionException(e);
          }
        },
        executor);
  }
}
