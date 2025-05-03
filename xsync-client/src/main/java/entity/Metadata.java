package entity;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Metadata {
  private String filepath;
  private Long filesize;
  private String fileHash;
  private Long lastModifiedTime;
  private Long chunkCount;
  private List<String> chunkHashes;
}
