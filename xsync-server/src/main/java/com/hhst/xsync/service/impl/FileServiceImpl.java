package com.hhst.xsync.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hhst.xsync.entity.File;
import com.hhst.xsync.mapper.FileMapper;
import com.hhst.xsync.service.IFileService;
import com.hhst.xsync.utils.RedisUtil;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FileServiceImpl extends ServiceImpl<FileMapper, File> implements IFileService {

  @Autowired private FileMapper mapper;
  @Autowired private RedisUtil redisUtil;

  @Override
  public void upsert(File file, Integer delta) {
    mapper.upsert(file, delta);
    redisUtil.set(
        String.format("file:%s:%s", file.getEmail(), file.getFilepath()),
        file,
        30,
        TimeUnit.MINUTES);
  }

  @Override
  public File getFileWithCache(String email, String path) {
    File file = redisUtil.get(String.format("file:%s:%s", email, path), File.class);
    if (file == null) {
      file = getOne(new QueryWrapper<>(File.class).allEq(Map.of("filepath", path, "email", email)));
    }
    return file;
  }

  @Override
  public Boolean deleteFileWithCache(String email, String path) {
    redisUtil.del(String.format("file:%s:%s", email, path));
    return remove(new QueryWrapper<>(File.class).allEq(Map.of("filepath", path, "email", email)));
  }
}
