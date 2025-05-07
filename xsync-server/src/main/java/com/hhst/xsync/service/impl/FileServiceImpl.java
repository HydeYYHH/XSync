package com.hhst.xsync.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hhst.xsync.entity.File;
import com.hhst.xsync.mapper.FileMapper;
import com.hhst.xsync.service.IFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FileServiceImpl extends ServiceImpl<FileMapper, File> implements IFileService {

  @Autowired private FileMapper mapper;

  @Override
  public void upsert(File file, Integer delta) {
    mapper.upsert(file, delta);
  }
}
