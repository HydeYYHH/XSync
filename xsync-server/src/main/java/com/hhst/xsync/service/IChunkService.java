package com.hhst.xsync.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hhst.xsync.entity.Chunk;
import java.util.List;

public interface IChunkService extends IService<Chunk> {

  void upsert(Chunk chunk);

  void upsertBatch(List<Chunk> chunks);
}
