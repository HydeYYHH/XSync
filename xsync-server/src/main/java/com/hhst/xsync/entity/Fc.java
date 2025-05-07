package com.hhst.xsync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;
import lombok.*;

/**
 * the middle table between chunk and file
 *
 * @author hhst
 * @since 2025-05-05
 */
@Getter
@Setter
@ToString
@TableName("fc")
@AllArgsConstructor
@NoArgsConstructor
public class Fc implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  @TableField("fileId")
  private Long fileId;

  /** chunk hash */
  @TableField("chunkHash")
  private String chunkHash;

  /**
   * The chunk's index in the file reflects its first occurrence, rather than its actual position in
   * the sequence
   */
  @TableField("index")
  private Integer index;
}
