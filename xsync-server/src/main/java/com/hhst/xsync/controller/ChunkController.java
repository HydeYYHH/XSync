package com.hhst.xsync.controller;

import com.hhst.xsync.dto.Response;
import com.hhst.xsync.service.*;
import com.hhst.xsync.utils.RateLimitedOutputStream;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/chunk")
public class ChunkController {

  private static final Log log = LogFactory.getLog(ChunkController.class);

  @Value("${xsync.speed-limiter.rate}")
  private double rate;

  @Autowired private ObjectStorageService storageService;

  @PostMapping(value = "/upload", consumes = "multipart/form-data")
  public Response upload(
      @RequestParam("hash") @NotEmpty String hash,
      @RequestParam("hash-algorithm") @NotEmpty String ha,
      @RequestParam("file") @NotNull MultipartFile file) {
    if (file.isEmpty()) {
      return Response.build(HttpStatus.BAD_REQUEST, "File is empty");
    }
    // Process file upload in service layer
    try {
      byte[] data = file.getBytes();
      // check file integrity
      if (!Arrays.equals(DigestUtils.digest(DigestUtils.getDigest(ha), data), hash.getBytes())) {
        return Response.build(HttpStatus.BAD_REQUEST, "File maybe broken");
      }
      // upload to object storage service
      storageService.putObject(hash, data);
    } catch (IllegalArgumentException e) {
      log.error(e);
      return Response.build(HttpStatus.BAD_REQUEST, e.getMessage());
    } catch (Exception e) {
      log.error(e);
      return Response.build(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }
    return Response.build(HttpStatus.CREATED, "Chunk uploaded");
  }

  /**
   * Upload a batch-compressed file containing multiple chunks.
   *
   * @param hash hash of the compressed file
   * @param ha hash algorithm
   * @param file the compressed file
   * @return response
   */
  @PostMapping(value = "/upload/batch", consumes = "multipart/form-data")
  public Response uploadBatch(
      @RequestParam("hash") @NotEmpty String hash,
      @RequestParam("hash-algorithm") @NotEmpty String ha,
      @RequestParam("file") @NotNull MultipartFile file) {

    // Process batch upload in service layer
    // format should be like: length|chunk
    try (InputStream is = file.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BufferedOutputStream buffer = new RateLimitedOutputStream(baos, rate)) {
      MessageDigest digest = DigestUtils.getDigest(ha);

      while (true) {
        try {
          byte[] lenBytes = IOUtils.readFully(is, 4); // read chunk length (int)
          int length = ByteBuffer.wrap(lenBytes).getInt();
          byte[] chunk = IOUtils.readFully(is, length); // read actual chunk

          // compute chunk hash and upload it
          String chunkHash = Hex.encodeHexString(DigestUtils.digest(digest, chunk));
          storageService.putObject(chunkHash, chunk);
          buffer.write(lenBytes);
          buffer.write(chunk);

        } catch (EOFException e) {
          break;
        }
      }

      buffer.flush();

      // check file integrity
      if (!Hex.encodeHexString(DigestUtils.digest(digest, baos.toByteArray())).equals(hash)) {
        return Response.build(HttpStatus.BAD_REQUEST, "File integrity check failed");
      }
      return Response.build(HttpStatus.CREATED, "Chunks uploaded");

    } catch (IllegalArgumentException e) {
      log.error(e);
      return Response.build(HttpStatus.BAD_REQUEST, e.getMessage());
    } catch (Exception e) {
      log.error("Upload batch failed", e);
      return Response.build(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  @GetMapping("/fetch/{hash}")
  public ResponseEntity<byte[]> fetch(@PathVariable @NotEmpty String hash) {
    byte[] chunks;
    try {
      chunks = storageService.getObject(hash);
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=chunks")
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .body(chunks);
    } catch (Exception e) {
      log.error(e);
      return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Fetch multiple chunks by a list of hashes.
   *
   * @param hashes list of chunk hashes
   * @return list of chunk data
   */
  @PostMapping("/fetch/batch")
  public ResponseEntity<StreamingResponseBody> fetchBatch(
      @RequestBody @NotEmpty List<String> hashes) {
    StreamingResponseBody body =
        outputStream -> {
          try (BufferedOutputStream bos = new RateLimitedOutputStream(outputStream, rate)) {
            for (String hash : hashes) {
              byte[] chunk = storageService.getObject(hash);
              // write chunk length
              bos.write(ByteBuffer.allocate(4).putInt(chunk.length).array());
              // write chunk data
              bos.write(chunk);
              bos.flush();
            }
          } catch (Exception e) {
            log.error("Failed to stream chunks", e);
          }
        };
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=chunks")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(body);
  }
}
