package com.hhst.xsync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * <p>
 * metadata of the file
 * </p>
 *
 * @author hhst
 * @since 2025-05-02
 */
@Getter
@Setter
@ToString
@TableName("file")
@AllArgsConstructor
public class File implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("filepath")
    private String filepath;

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
    private Long chunkCount;

    /**
     * file size(byte)
     */
    @TableField("size")
    private Long size;

    /**
     * file hash hex string
     */
    @TableField("hash")
    private String hash;
}
