package com.hhst.xsync.entity;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Metadata {
  private String filepath;
  private Long filesize;
  private String fileHash;
  private Long lastModifiedTime;
  private Long chunkCount;
  private List<String> chunkHashes;
}
