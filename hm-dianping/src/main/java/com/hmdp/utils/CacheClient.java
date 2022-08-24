package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;

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
            - 泛型 -> 先定义再使用 在public后面使用小尖括号<A, B, C>定义出来再使用 名字并没有严格约束
            - 返回值类型不确定 R
            - id的类型不确定 ID
            - 传进来一个class用来确定返回值类型 即调用者需要指明返回值类型R
            - 如果redis中不存在信息 需要到数据库中查询 但是我们并不能确定去哪个表中查询 因此需要调用者传进来一个Function<参数, 返回值>
              Function<T, K> 其中T是这个函数的参数类型 K是这个函数的返回值类型
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix,
                                          ID id, Class<R> type,
                                          Function<ID, R> dbFallback,
                                          Long time,
                                          TimeUnit unit) {
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


    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /*
        泛型 和 参数的说明参考上面
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, String lockKeyPrefix,
                                            ID id,
                                            Class<R> type,
                                            Function<ID, R> dbFallback,
                                            Long time,
                                            TimeUnit unit) {
        String key = keyPrefix + id;
        // 1- 从redis中查询信息
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2- 判断是否存在
        if (StrUtil.isBlank(json)) {
            // 3- 未命中缓存直接返回null (一般来说不会存在这种问题 如果未命中的话那么只能说明该店铺并不是热点店铺 没有参加活动)
            return null;
        }

        // 4- 命中 需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        // 这里面反序列化取出来的字符串本质是JSONObject 然后再次转换为实体类
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);

        // 5- 是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        // 过期时间在当前时间之后 -> 说明还没有过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1- 未过期 返回
            return r;
        }


        // 5.2- 过期 重建缓存
        // 6- 重建缓存
        String lockKey = lockKeyPrefix + id;
        // 6.1- 获取互斥锁
        boolean isLock = tryLock(lockKey);

        // 6.2- 失败 返回

        if (isLock) {
            // TODO: 6.3- 成功 开启独立线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库 -> 由于不知道具体的查询情况 因此交给调用者实现 参数为一个Function
                    R r1 = dbFallback.apply(id);
                    // 重建缓存
                    this.setWithLogicalExpire(key, r1, time, unit);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }

        // 6.4- 返回过期的店铺信息
        return r;
    }


    /**
     * 尝试获取锁
     * 通过redis的setnx命令来设置锁 这样只有第一个线程能够设置成功 其他线程设置时返回的结果均为0
     * @param key 键
     * @return 结果
     */
    private boolean tryLock(String key) {
        // setIfAbsent就是setnx absent不存在
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }


    /**
     * 释放锁 实际上就是从redis中删除key键
     * @param key 键
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }



}
