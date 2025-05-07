package com.hhst.xsync.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;
import lombok.*;

/**
 * @author hhst
 * @since 2025-05-05
 */
@Getter
@Setter
@ToString
@TableName("user")
@AllArgsConstructor
@NoArgsConstructor
public class User implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** user email */
  @TableId("email")
  private String email;

  /** user password */
  @TableField("password")
  private String password;

  /** user role in spring security */
  @TableField("role")
  private String role;
}
