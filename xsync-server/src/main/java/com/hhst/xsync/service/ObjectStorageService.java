package com.hhst.xsync.service;

public interface ObjectStorageService {
  void putObject(String name, byte[] data) throws Exception;

  byte[] getObject(String name) throws Exception;
}
