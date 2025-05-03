package com.hhst.xsync.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhst.xsync.entity.Fc;
import com.hhst.xsync.entity.File;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FcMapper extends BaseMapper<Fc> {

  @Insert(
      """
      insert into fc(fileId, chunkHash, `index`)
      values (#{fileId}, #{chunkHash}, #{index})
      on duplicate key update chunkHash = values(chunkHash)
    """)
  void upsert(Fc fc);

  void upsertBatch(@Param("list") List<Fc> fcs);

  @Delete(
    """
    delete from fc
    where fileId = #{fileId} and `index` >= (
        select chunkCount from file where id = #{fileId}
    )
    """)
  void deleteInvalid(Long fileId);

  @Select(
    """
    select chunkHash from fc
    where `index` < #{chunkCount} and fileId = #{id}
    order by `index`
    """)
  List<String> getChunkHashes(File file);
}
