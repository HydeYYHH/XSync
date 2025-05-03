package com.hhst.xsync.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhst.xsync.entity.Chunk;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChunkMapper extends BaseMapper<Chunk> {

  @Insert(
      """
      insert into chunk(hash) values(#{hash})
      on duplicate key update hash=values(hash)
      """)
  void upsert(Chunk chunk);

  void upsertBatch(List<Chunk> chunks);

}

