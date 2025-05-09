package com.hhst.xsync.service;


import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ObjectStorageService {
  CompletableFuture<Void> putObject(String name, byte[] data);

  CompletableFuture<byte[]> getObject(String name);

  CompletableFuture<Void> removeObject(String name);

  CompletableFuture<Void> removeObjects(List<String> names);

}
