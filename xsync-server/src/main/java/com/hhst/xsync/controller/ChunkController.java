package com.hhst.xsync.controller;

import com.hhst.xsync.dto.Response;
import com.hhst.xsync.entity.Chunk;
import com.hhst.xsync.entity.Fc;
import com.hhst.xsync.entity.File;
import com.hhst.xsync.entity.Metadata;
import com.hhst.xsync.service.*;
import com.hhst.xsync.utils.HashUtils;
import com.hhst.xsync.utils.JwtUtils;
import com.hhst.xsync.utils.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/chunk")
public class ChunkController {

  private static final Log log = LogFactory.getLog(ChunkController.class);

  @Value("${xsync.speed-limiter.upload-batch.rate}")
  private long uploadRate;

  @Value("${xsync.speed-limiter.fetch-batch.rate}")
  private long fetchRate;

  @Autowired private ObjectStorageService storageService;
  @Autowired private IChunkService chunkService;
  @Autowired private IFileService fileService;
  @Autowired private IFcService fcService;
  @Autowired private JwtUtils jwtUtils;

  /**
   * Upload a file containing multiple chunks.
   *
   * @param ha hash algorithm
   * @param meta metadata of file
   * @param multipart of the file
   * @return response
   */
  @PostMapping(value = "/upload/batch", consumes = "multipart/form-data")
  @Transactional
  public Response uploadBatch(
      @RequestPart("hash") @NotEmpty String hash,
      @RequestPart("hash-algorithm") @NotEmpty String ha,
      @RequestPart("metadata") @Valid Metadata meta,
      @RequestPart("file") @NotNull MultipartFile multipart,
      HttpServletRequest request) {

    String subject = jwtUtils.extractUserSubject(request).orElse(null);
    if (subject == null) {
      return Response.build(HttpStatus.UNAUTHORIZED, "Unauthorized request");
    }

    try (InputStream is = multipart.getInputStream()) {
      RateLimiter limiter = RateLimiter.newInstance(uploadRate);
      // Create Chunk entities
      List<Chunk> chunks = new ArrayList<>();
      // Create Fc entities
      List<Fc> fcs = new ArrayList<>();

      HashUtils.Hasher hasher = new HashUtils.Hasher("SHA-256");

      List<CompletableFuture<Void>> futures = new ArrayList<>();

      while (true) {
        try {
          byte[] lenBytes = IOUtils.readFully(is, 4); // read chunk length (int)
          int length = ByteBuffer.wrap(lenBytes).getInt();
          byte[] chunk = IOUtils.readFully(is, length); // read chunk

          // Rate limiting
          limiter.limiting(length);

          // Compute chunk hash
          String chunkHash = HashUtils.hash(chunk, ha);
          // Update chunk hash for computing file hash
          hasher.update(chunk);

          chunks.add(new Chunk(chunkHash, chunk.length));
          /*
           * NOTE: the index is not the actual index in the file but the first occurrence place in the
           * file, this field is used to delete the invalid chunk(delete if index greater than chunk
           * count) but indicate the index of chunk.
           */
          int index = meta.getChunkHashes().indexOf(chunkHash);
          if (index == -1) {
            return Response.build(HttpStatus.BAD_REQUEST, "Invalid chunk");
          }
          fcs.add(new Fc(null, null, chunkHash, index));
          // Upload it to minio server
          futures.add(storageService.putObject(chunkHash, chunk));

        } catch (EOFException e) {
          break;
        } catch (RuntimeException e) {
          return Response.build(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
      }

      // Check file integrity
      if (!hash.equals(hasher.getHash())) {
        return Response.build(HttpStatus.BAD_REQUEST, "File integrity check failed");
      }

      // Wait all storage service tasks completed
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
      // Upsert chunks in batch and get the file delta size
      int delta = chunkService.upsertBatch(chunks);

      // Create file entity
      File file =
          new File(
              null,
              meta.getFilepath(),
              subject,
              meta.getLastModifiedTime(),
              meta.getChunkHashes().size(),
              meta.getFilesize(), // use the post file size first
              meta.getFileHash());
      fileService.upsert(file, delta);

      Boolean ignored = fcService.upsertBatch(fcs, file.getId());

      return Response.build(HttpStatus.CREATED, "Chunks uploaded");

    } catch (IllegalArgumentException e) {
      log.error("Invalid argument: ", e);
      return Response.build(HttpStatus.BAD_REQUEST, e.getMessage());
    } catch (Exception e) {
      log.error("Upload batch failed", e);
      return Response.build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
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
          try (BufferedOutputStream buffer =
              RateLimiter.newInstance(fetchRate).stream(outputStream)) {
            List<CompletableFuture<byte[]>> futures = new ArrayList<>();
            for (String hash : hashes) {
              futures.add(storageService.getObject(hash));
            }
            ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
            for (var future : futures) {
              byte[] chunk = future.get();
              // write chunk length
              lengthBuffer.clear();
              buffer.write(lengthBuffer.putInt(chunk.length).array());
              // write chunk data
              buffer.write(chunk);
              buffer.flush();
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
