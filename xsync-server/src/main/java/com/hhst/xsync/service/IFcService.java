package com.hhst.xsync.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hhst.xsync.entity.Fc;
import com.hhst.xsync.entity.File;

import java.util.List;

public interface IFcService extends IService<Fc> {

    void upsert(Fc fc);
    Boolean upsertBatch(List<Fc> fcList);
    List<String> getChunkHashes(File file);

}
