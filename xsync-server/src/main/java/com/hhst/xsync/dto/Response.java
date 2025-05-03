package com.hhst.xsync.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
@AllArgsConstructor
public class Response {
  private Integer code;
  private String message;
  private Object body;

  public static Response build(HttpStatus status) {
    return new Response(status.value(), status.getReasonPhrase(), null);
  }

  public static Response build(HttpStatus status, String message) {
    return new Response(status.value(), message, null);
  }

  public static Response build(HttpStatus status, String message, Object body) {
    return new Response(status.value(), message, body);
  }
}
