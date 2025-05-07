package com.hhst.xsync.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hhst.xsync.entity.Chunk;
import java.util.List;

public interface IChunkService extends IService<Chunk> {

  Integer upsert(Chunk chunk);

  Integer upsertBatch(List<Chunk> chunks);
}
