package com.hhst.xsync.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hhst.xsync.entity.Fc;
import com.hhst.xsync.entity.File;
import com.hhst.xsync.mapper.FcMapper;
import com.hhst.xsync.service.IFcService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FcServiceImpl extends ServiceImpl<FcMapper, Fc> implements IFcService {

  @Autowired private FcMapper mapper;

  @Transactional
  @Override
  public void upsert(@NotNull Fc fc) {
    mapper.upsert(fc);
    // Delete all invalid items
    mapper.deleteInvalid(fc.getFileId());
  }


  /**
   * Insert or update Fc with same fileId and delete all invalid(duplicate) items.
   * @param fcs A list of fc with same fileId
   * @return false: list is empty or list with different fileId
   */
  @Transactional
  @Override
  public Boolean upsertBatch(@NotNull List<Fc> fcs) {
    if (fcs.isEmpty()) {
      return false;
    }
    mapper.upsertBatch(fcs);
    mapper.deleteInvalid(fcs.getFirst().getFileId());
    return true;
  }

  @Override
  public List<String> getChunkHashes(File file) {
    return mapper.getChunkHashes(file);
  }
}
