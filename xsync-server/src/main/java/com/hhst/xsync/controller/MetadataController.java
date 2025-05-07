package com.hhst.xsync.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hhst.xsync.dto.Response;
import com.hhst.xsync.entity.File;
import com.hhst.xsync.entity.Metadata;
import com.hhst.xsync.service.*;
import com.hhst.xsync.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotEmpty;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/metadata")
public class MetadataController {

  @Autowired private IFileService fileService;
  @Autowired private IFcService fcService;
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
    String subject = jwtUtils.extractUserSubject(request).orElse(null);
    if (subject == null) {
      return Response.build(HttpStatus.UNAUTHORIZED, "Authorization header is invalid");
    }
    // get metadata from database
    path = URLDecoder.decode(path, StandardCharsets.UTF_8);
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

}
