package com.hhst.xsync.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hhst.xsync.entity.File;

public interface IFileService extends IService<File> {
  void upsert(File file);
}
