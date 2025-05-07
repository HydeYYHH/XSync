package com.hhst.xsync.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;

import lombok.*;
import org.checkerframework.checker.units.qual.N;

/**
 * <p>
 * 
 * </p>
 *
 * @author hhst
 * @since 2025-05-05
 */
@Getter
@Setter
@ToString
@TableName("chunk")
@AllArgsConstructor
@NoArgsConstructor
public class Chunk implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * hash string of the chunk
     */
    @TableId("hash")
    private String hash;

    /**
     * chunk size
     */
    @TableField("size")
    private Integer size;
}
