package utils;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.Blake3;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.digest.XXHash32;

public class HashUtils {

  public static String hash(byte[] input, String algorithm) {
    return Hex.encodeHexString(hashToBytes(input, algorithm));
  }

  public static byte[] hashToBytes(byte[] input, String algorithm) {
    switch (algorithm) {
      case "Blake3":
        return Blake3.hash(input);
      case "xxHash32":
        int seed = 0;
        var xxHash32 = new XXHash32(seed);
        xxHash32.update(input);
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(xxHash32.getValue());
        return buffer.array();
      default:
        return DigestUtils.digest(DigestUtils.getDigest(algorithm), input);
    }
  }

  public void update(byte[] data) {}

  public static class Hasher {
    private final String algorithm;
    private final Object hasher;

    public Hasher(String algorithm) {
      this.algorithm = algorithm;
      switch (algorithm) {
        case "Blake3":
          hasher = Blake3.initHash();
          break;
        case "xxHash32":
          int seed = 0;
          hasher = new XXHash32(seed);
          break;
        default:
          hasher = DigestUtils.getDigest(algorithm);
      }
    }

    public void update(byte[] data) {
      switch (algorithm) {
        case "Blake3":
          ((Blake3) hasher).update(data);
          break;
        case "xxHash32":
          ((XXHash32) hasher).update(data);
          break;
        default:
          ((MessageDigest) hasher).update(data);
      }
    }

    public String getHash() {
      return switch (algorithm) {
        case "Blake3" -> Hex.encodeHexString(((Blake3) hasher).doFinalize(32));
        case "xxHash32" -> Long.toHexString(((XXHash32) hasher).getValue());
        default -> Hex.encodeHexString(((MessageDigest) hasher).digest());
      };
    }
  }
}
