package com.hhst.xsync.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhst.xsync.entity.File;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface FileMapper extends BaseMapper<File> {

  @Insert(
      """
    insert into file(filepath, email, lastModifiedTime, chunkCount, size, hash)
    values(#{filepath}, #{email}, #{lastModifiedTime}, #{chunkCount}, #{size}, #{hash})
    on duplicate key update lastModifiedTime=values(lastModifiedTime),
                            chunkCount=values(chunkCount),
                            size=values(size), hash=values(hash),
                            id=LAST_INSERT_ID(id)
    """)
  @Options(useGeneratedKeys = true, keyColumn = "id", keyProperty = "id")
  void upsert(File file);
}
