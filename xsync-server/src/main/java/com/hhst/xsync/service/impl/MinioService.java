package com.hhst.xsync.service.impl;

import com.hhst.xsync.config.MinioProperties;
import com.hhst.xsync.service.ObjectStorageService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MinioService implements ObjectStorageService {

  @Autowired MinioClient client;
  @Autowired MinioProperties properties;

  @Async
  @Override
  public void putObject(String name, byte[] data) throws Exception {
    client.putObject(
        PutObjectArgs.builder().bucket(properties.getBucket()).object(name).stream(
                new ByteArrayInputStream(data), data.length, -1)
            .contentType("application/octet-stream")
            .build());
  }

  @Override
  public byte[] getObject(String name) throws Exception {
    try (InputStream is =
        client.getObject(
            GetObjectArgs.builder().bucket(properties.getBucket()).object(name).build())) {
      return IOUtils.toByteArray(is);
    }
  }
}
