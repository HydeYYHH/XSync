package com.hhst.xsync.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

  @Autowired MinioProperties properties;

  @Bean
  public MinioClient minioClient() {
    var client =
        MinioClient.builder()
            .endpoint(properties.getEndpoint())
            .credentials(properties.getAccessKey(), properties.getSecretKey())
            .build();
    try {
      if (!client.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build())) {
        client.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return client;
  }
}
