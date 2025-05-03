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
import org.checkerframework.checker.units.qual.A;

/**
 * <p>
 * the middle table between chunk and file
 * </p>
 *
 * @author hhst
 * @since 2025-05-02
 */
@Getter
@Setter
@ToString
@TableName("fc")
@AllArgsConstructor
public class Fc implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("fileId")
    private Long fileId;

    /**
     * chunk hash
     */
    @TableField("chunkHash")
    private String chunkHash;

    /**
     * index of the chunk in the file
     */
    @TableField("index")
    private Long index;
}
