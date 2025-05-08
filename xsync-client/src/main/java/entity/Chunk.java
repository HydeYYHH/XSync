package entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Chunk {
  private final byte[] data;
  private final int length;
}
