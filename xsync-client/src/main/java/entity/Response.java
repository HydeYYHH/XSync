package entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Response {
  private Integer code;
  private String message;
  private Object body;

  public Boolean isSuccess() {
    return code != null && code >= 200 && code < 300;
  }
}
