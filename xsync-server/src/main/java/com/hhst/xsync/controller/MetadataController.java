package com.hhst.xsync.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hhst.xsync.dto.Response;
import com.hhst.xsync.entity.Chunk;
import com.hhst.xsync.entity.Fc;
import com.hhst.xsync.entity.File;
import com.hhst.xsync.entity.Metadata;
import com.hhst.xsync.service.*;
import com.hhst.xsync.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/metadata")
public class MetadataController {

  @Autowired private IFileService fileService;
  @Autowired private IFcService fcService;
  @Autowired private IChunkService chunkService;
  @Autowired private JwtUtils jwtUtils;

  /**
   * Fetch metadata for a given filepath.
   *
   * @param path The filepath for which to fetch metadata.
   * @return Response containing the metadata.
   */
  @Transactional
  @GetMapping("/fetch")
  public Response fetch(@RequestParam("path") @NotEmpty String path, HttpServletRequest request) {
    // Delegate to service layer to fetch metadata
    // extract user information in header
    String subject = extractUserSubject(request).orElse(null);
    if (subject == null) {
      return Response.build(HttpStatus.UNAUTHORIZED, "Authorization header is invalid");
    }
    // get metadata from database
    File file =
        fileService.getOne(
            new QueryWrapper<>(File.class).allEq(Map.of("filepath", path, "email", subject)));
    if (file == null) {
      return Response.build(HttpStatus.NOT_FOUND, "File not found");
    }
    List<String> hashes = fcService.getChunkHashes(file);
    Metadata metadata =
        new Metadata(
            path,
            file.getSize(),
            file.getHash(),
            file.getLastModifiedTime(),
            file.getChunkCount(),
            hashes); // Replace with actual service call
    return Response.build(HttpStatus.OK, "Metadata fetched", metadata);
  }

  /**
   * Update metadata for a given filepath.
   *
   * @param path The filepath for which to update metadata.
   * @param meta The metadata to update.
   * @return Response indicating the result of the operation.
   */
  @Transactional
  @PutMapping("/upsert")
  public Response upsert(
      @RequestParam("path") @NotEmpty String path,
      @RequestBody @Valid Metadata meta,
      HttpServletRequest request) {
    // Delegate to service layer to update metadata
    // extract user information in header
    String subject = extractUserSubject(request).orElse(null);
    if (subject == null) {
      return Response.build(HttpStatus.UNAUTHORIZED, "Authorization header is invalid");
    }
    // save or update in file metadata service
    File file =
        new File(
            null,
            path,
            subject,
            meta.getLastModifiedTime(),
            meta.getChunkCount(),
            meta.getFilesize(),
            meta.getFileHash());
    fileService.upsert(file);
    // save or update in chunk metadata service
    chunkService.upsertBatch(
        meta.getChunkHashes().stream().map(Chunk::new).collect(Collectors.toList()));
    // save or update in fc service (connect file and chunks)
    List<Fc> fcs = new ArrayList<>();
    List<String> chunkHashes = meta.getChunkHashes();
    for (long i = 0; i < meta.getChunkCount(); i++) {
      Fc fc = new Fc(null, file.getId(), chunkHashes.get((int) i), i);
      fcs.add(fc);
    }
    if (!fcService.upsertBatch(fcs)) {
      return Response.build(HttpStatus.BAD_REQUEST, "Update failed");
    }
    return Response.build(HttpStatus.OK, "Metadata updated");
  }

  private Optional<String> extractUserSubject(HttpServletRequest request) {
    String bearer = request.getHeader("Authorization");
    if (bearer == null || !bearer.startsWith("Bearer ")) {
      return Optional.empty();
    }
    String token = bearer.substring(7);
    Claims claims = jwtUtils.parseToken(token);
    return Optional.of(claims.getSubject());
  }
}
