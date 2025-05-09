package com.hhst.xsync.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhst.xsync.entity.Chunk;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChunkMapper extends BaseMapper<Chunk> {

  @Select(
      """
    SELECT c.*
    FROM chunk c
    LEFT JOIN file f ON c.hash = f.hash
    WHERE f.hash IS NULL;
    """)
  List<Chunk> getIsolatedChunks();
}
