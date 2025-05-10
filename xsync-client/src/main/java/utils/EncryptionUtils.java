package utils;

import org.jasypt.encryption.pbe.StandardPBEByteEncryptor;
import org.jasypt.salt.RandomSaltGenerator;

public class EncryptionUtils {

  private static final StandardPBEByteEncryptor encryptor = new StandardPBEByteEncryptor();

  public static void initialize(String key) {
    encryptor.setPassword(key);
    encryptor.setAlgorithm(Const.encryptionType);
    encryptor.setSaltGenerator(new RandomSaltGenerator());
    encryptor.initialize();
  }

  public static byte[] encrypt(byte[] data) {
    return encryptor.encrypt(data);
  }

  public static byte[] decrypt(byte[] data) {
    return encryptor.decrypt(data);
  }

}
