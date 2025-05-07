package com.hhst.xsync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;

import lombok.*;

/**
 * <p>
 * metadata of the file
 * </p>
 *
 * @author hhst
 * @since 2025-05-05
 */
@Getter
@Setter
@ToString
@TableName("file")
@AllArgsConstructor
@NoArgsConstructor
public class File implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * filepath for given user
     */
    @TableField("filepath")
    private String filepath;

    /**
     * user email
     */
    @TableField("email")
    private String email;

    /**
     * last modified time of the file(timestamp)
     */
    @TableField("lastModifiedTime")
    private Long lastModifiedTime;

    /**
     * number of chunks
     */
    @TableField("chunkCount")
    private Integer chunkCount;

    /**
     * file size(byte)
     */
    @TableField("size")
    private Integer size;

    /**
     * file hash hex string
     */
    @TableField("hash")
    private String hash;
}
