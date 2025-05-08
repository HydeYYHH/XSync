package utils;

public class Const {

    // Unirest
    public static final String defaultUrl = "http://localhost:8080";

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


}
