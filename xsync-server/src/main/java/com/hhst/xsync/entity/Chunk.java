package com.hhst.xsync.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


@Getter
@Setter
@ToString
@TableName("chunk")
@AllArgsConstructor
public class Chunk implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * hash string of the chunk
     */
    @TableId("hash")
    private String hash;
}
