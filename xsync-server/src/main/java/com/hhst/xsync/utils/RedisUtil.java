package com.hhst.xsync.utils;

import com.alibaba.fastjson.JSON;
import jakarta.validation.constraints.NotNull;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisUtil {

  @Autowired private StringRedisTemplate stringRedisTemplate;

  @Nullable
  @Cacheable(cacheNames = "redis", key = "#key")
  public <T> T get(@NotNull String key, Class<T> clazz) {
    return JSON.parseObject(stringRedisTemplate.opsForValue().get(key), clazz);
  }

  @CachePut(cacheNames = "redis", key = "#key")
  public void set(@NotNull String key, Object value, long timeout, TimeUnit timeUnit) {
    stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(value), timeout, timeUnit);
  }

  @CacheEvict(cacheNames = "redis", key = "#key")
  public void del(@NotNull String key) {
    stringRedisTemplate.delete(key);
  }
}
