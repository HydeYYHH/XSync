package utils;

public class Const {

  // Unirest
  public static final String defaultUrl = "http://localhost:8080";

  // File
  public static final String credentialFilename = "credential";
  public static final String secretKeyFilename = "secret-key";

  // Hash
  public static final String hashAlgorithm = "SHA-256";

  // FastCDC
  public static final int DEFAULT_EXPECTED_CHUNK_SIZE = 8 * 1_024;
  public static final double DEFAULT_MAX_SIZE_FACTOR = 8;
  public static final double DEFAULT_MIN_SIZE_FACTOR = 0.25;
  public static final int DEFAULT_NORMALIZATION_LEVEL = 2;
  public static final long DEFAULT_MASK_GENERATION_SEED = 941_568_351L;

  // Stream
  public static final Integer deferredStreamThreshold = 4 * 1024 * 1024;
  public static final Integer bufferSize = 64 * 1024;

  // Compress
  public static final String compressionType = "zstd";
  public static final Integer compressionLevel = 7;

  // Encrypt
  public static final String encryptionType = "PBEWithMD5AndDES";
}
