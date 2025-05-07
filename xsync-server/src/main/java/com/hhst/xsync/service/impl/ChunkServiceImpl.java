package com.hhst.xsync.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hhst.xsync.entity.Chunk;
import com.hhst.xsync.mapper.ChunkMapper;
import com.hhst.xsync.service.IChunkService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChunkServiceImpl extends ServiceImpl<ChunkMapper, Chunk> implements IChunkService {

  @Autowired private ChunkMapper mapper;

  @Override
  @Transactional
  public Integer upsert(Chunk chunk) {
    Chunk existing = getById(chunk.getHash());
    if (existing != null) {
      int delta = chunk.getSize() - existing.getSize();
      updateById(chunk);
      return delta;
    } else {
      save(chunk);
      return chunk.getSize();
    }
  }

  @Override
  @Transactional
  public Integer upsertBatch(List<Chunk> chunks) {
    int totalDelta = 0;

    for (Chunk chunk : chunks) {
      Chunk existing = getById(chunk.getHash());
      if (existing != null) {
        int delta = chunk.getSize() - existing.getSize();
        updateById(chunk);
        totalDelta += delta;
      } else {
        save(chunk);
        totalDelta += chunk.getSize();
      }
    }

    return totalDelta;
  }
}
