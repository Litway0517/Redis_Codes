package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }


    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 为了设置逻辑过期时间需要将value再封装到RedisData中
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    /*
        泛型方法
            - 先定义在使用 在public后面使用小尖括号<A, B, C>定义出来再使用
            - 返回值类型不确定 R
            - id的类型不确定 ID
            - 传进来一个class用来确定返回值类型
            - 如果redis中不存在信息 需要到数据库中查询 但是我们并不能确定去哪个表中查询 因此需要调用者传进来一个Function<参数, 返回值>
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix,
                                          ID id, Class<R> type,
                                          Function<ID, R> dbFallback,
                                          Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1- 从redis中查询信息 -> 返回的就是普通json串
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2- 判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3- 存在直接返回
            return JSONUtil.toBean(json, type);
        }

        // 因为向redis中存储的可能是空值 也可能是 店铺真实值 所以这里再判断一次
        if (json != null) {
            return null;
        }

        // 4- 不存在 根据id查询数据库
        R r = dbFallback.apply(id);

        // 5- 不存在数据库中
        if (r == null) {
            // 将空值存储到redis中 空字符串 过期时间改成2分钟 不应该设置得太长
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 6- 存在数据库中 保存到redis中 -> 先转成JSON字符串
        this.set(key, r, time, unit);

        // 7- 返回
        return r;
    }



}
