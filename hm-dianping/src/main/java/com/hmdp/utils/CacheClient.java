package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * Redis缓存工具
 * <p>
 * TODO:
 *      - 缓存穿透: 指的是redis缓存中没有查找到对应缓存, 缓存未命中, 同时数据库中也未查询到相关数据.
 *                使用缓存空值来解决, 空值设置的时间一般不应该过长.
 *      - 缓存雪崩: 指的是大量的缓存采用了相同的失效时间, 请求全部转发到了数据库, 从而导致数据库压力骤增.
 *                将key对应的缓存设置一个随机数, 让key均匀失效等.
 *      - 缓存击穿: 指的是对于单个热点key具有高并发量, 在其失效的瞬间, 持续的请求就会击破缓存, 直接请求到数据库.
 *                使用互斥锁(Mutex key); 热点key不过期, 后台异步更新; 提前使用互斥锁, 在value内部设置一个比缓存短的时间,
 *                当异步线程发现该值快过期时, 马上延长内置的这个时间, 并从数据库重新加载数据, 设知道缓存中去.
 * </p>
 *
 * @author DELL_
 * @date 2023/02/11
 */
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

    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 设置逻辑过期时间
     *
     * @param key   键
     * @param value 值
     * @param time  时间
     * @param unit  时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 为了设置逻辑过期时间需要将value再封装到RedisData中
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 使用逻辑过期时间, 所以不手动设置过期时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    /*
        泛型方法
            - 泛型 -> 先定义再使用 在public后面使用小尖括号<A, B, C>定义出来再使用 名字并没有严格约束
            - 返回值类型不确定 R
            - id的类型不确定 ID
            - 方法中传进来一个class参数用来确定返回值类型 即调用者需要指明返回值类型R
            - 如果redis中不存在信息 需要到数据库中查询 但是我们并不能确定去哪个表中查询 因此需要调用者传进来一个Function<参数, 返回值>
              Function<T, K> 其中T是这个函数的参数类型 K是这个函数的返回值类型
     */
    /**
     * 泛型方法
     *      - 泛型 -> 先定义再使用 在public后面使用小尖括号<A, B, C>定义出来再使用 名字并没有严格约束
     *      - 返回值类型不确定 R
     *      - id的类型不确定 ID
     *      - 方法中传进来一个class参数用来确定返回值类型 即调用者需要指明返回值类型R
     *      - 如果redis中不存在信息 需要到数据库中查询 但是我们并不能确定去哪个表中查询 因此需要调用者传进来一个Function<参数, 返回值>
     *        Function<T, K> 其中T是这个函数的参数类型 K是这个函数的返回值类型
     * @param keyPrefix key值
     * @param id 数据的id值
     * @param type 返回值类型
     * @param dbFallback 回调函数 指明到哪张表查询
     * @param time 时间长度
     * @param unit 时间单位
     * @return 返回指定的type类型
     * @param <R> 返回值类型
     * @param <ID> 待进行缓存数据的唯一标志
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix,
                                          ID id, Class<R> type,
                                          Function<ID, R> dbFallback,
                                          Long time,
                                          TimeUnit unit) {
        String key = keyPrefix + id;
        // 1- 从redis中查询信息 -> 返回的就是普通json串 注意: 存到redis中的都是字符串 即使是对象也是处理为字符串后再存储的
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2- 判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3- 存在直接返回
            return JSONUtil.toBean(json, type);
        }

        // 判断命中的是否是空值
        if (json != null) {
            // 返回错误信息
            return null;
        }

        // json为null值情况, 则重建
        // 4- 不存在 根据id查询数据库
        R r = dbFallback.apply(id);

        // 5- 不存在数据库中
        if (r == null) {
            // 将空值存储到redis中 空字符串 过期时间改成2分钟 不应该设置得太长
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回错误信息
            return null;
        }

        // 6- 存在数据库中 保存到redis中 -> 先转成JSON字符串
        this.set(key, r, time, unit);

        // 7- 返回
        return r;
    }




    /*
        泛型 和 参数的说明参考上面
     */

    /**
     * 通过逻辑缓存解决缓存
     * @param keyPrefix     key前缀, 待缓存数据的业务前缀, 加上数据的唯一标志即可
     * @param lockKeyPrefix 使用互斥锁时的业务前缀
     * @param id 数据唯一标志
     * @param type 数据类型
     * @param dbFallback 重建缓存时查询方法的逻辑
     * @param time 逻辑过期时间
     * @param unit 单位
     * @return 缓存
     * @param <R> 缓存类型
     * @param <ID> 待缓存数据的唯一标志
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, String lockKeyPrefix,
                                            ID id,
                                            Class<R> type,
                                            Function<ID, R> dbFallback,
                                            Long time,
                                            TimeUnit unit) {
        String key = keyPrefix + id;
        // 1- 从redis中查询信息 -> 返回的就是普通json串 注意: 存到redis中的都是字符串 即使是对象也是处理为字符串后再存储的
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2- 判断是否存在
        if (StrUtil.isBlank(json)) {
            // 3- 未命中缓存直接返回null (一般来说不会存在这种问题 如果未命中的话那么只能说明该店铺并不是热点店铺 没有参加活动)
            /*
                问题: 如果缓存根本未预热, 那么从redis中查询的总会是null, 所以下面的逻辑不会执行.
                针对热点key需要使用单元测试预热
             */
            // 如果redis没有数据, 查询结果会一直为空, 创建缓存
            CompletableFuture<R> futureResult = rebuildCache(lockKeyPrefix, id, dbFallback, time, unit, key);
            R r = null;
            try {
                if (futureResult != null) {
                    r = futureResult.get();
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            return r;
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

        // 6.2- 失败 返回(返回过期信息)

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
     * 重建缓存, 返回值为CompletableFuture, 异步线程的执行结果需要使用future接收
     * @param lockKeyPrefix 锁前缀
     * @param id id
     * @param dbFallback 方法
     * @param time 时间
     * @param unit 单位
     * @param key 锁
     * @return 重建的缓存内容
     * @param <R> 返回值类型
     * @param <ID> 缓存内容对应的唯一id
     */
    private <R, ID> CompletableFuture<R> rebuildCache(String lockKeyPrefix, ID id, Function<ID, R> dbFallback, Long time, TimeUnit unit, String key) {
        CompletableFuture<R> futureResult = new CompletableFuture<>();
        // 6- 重建缓存
        String lockKey = lockKeyPrefix + id;
        // 6.1- 获取互斥锁
        boolean isLock = tryLock(lockKey);

        // 6.2- 失败 返回(返回过期信息)

        if (isLock) {
            // TODO: 6.3- 成功 开启独立线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库 -> 由于不知道具体的查询情况 因此交给调用者实现 参数为一个Function
                    R r1 = dbFallback.apply(id);
                    // 重建缓存
                    this.setWithLogicalExpire(key, r1, time, unit);
                    futureResult.complete(r1);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
            return futureResult;
        }
        return null;
    }

    public <R, ID> R queryWithMutex(String prefix, ID id,
                                    Class<R> type,
                                    Function<ID, R> dbFallback,
                                    Long time, TimeUnit unit) {
        String key = prefix + id;
        // 1. 从redis中查询信息
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3. 存在, 直接返回
            return JSONUtil.toBean(json, type);
        }

        // 判断命中的是否为空值
        if (json!=null) {
            return null;
        }

        // 4. 实现缓存重建
        // 4.1. 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2. 判断锁获取成功
            if (!isLock) {
                // 4.3. 锁获取失败, 休眠重试
                Thread.sleep(50);
                return queryWithMutex(prefix, id, type, dbFallback, time, unit);
            }
            // 4.4. 锁获取成功
            r = dbFallback.apply(id);
            // 5. 不存在, 返回错误
            if (r == null) {
                // 将空值写入redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 返回报错信息
                return null;
            }
            // 6. 存在, 写入redis
            this.set(key, r, time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7. 释放锁
            unlock(lockKey);
        }
        // 8. 返回
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
