package com.hhst.xsync.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hhst.xsync.entity.Chunk;
import com.hhst.xsync.mapper.ChunkMapper;
import com.hhst.xsync.service.IChunkService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChunkServiceImpl extends ServiceImpl<ChunkMapper, Chunk> implements IChunkService {

  @Autowired private ChunkMapper mapper;

  @Override
  public void upsert(Chunk chunk) {
    mapper.upsert(chunk);
  }

  @Override
  public void upsertBatch(List<Chunk> chunks) {
    mapper.upsertBatch(chunks);
  }
}
