package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constant.ShopConstant.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;


    /**
     * 根据商户id查询商户信息  在大型活动中, 实际上店铺就是一个热门key, 因此这里使用缓存来降低数据库压力. 针对 店铺 做缓存
     *
     * @param id id
     * @return Result
     */
    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        // Shop shop = queryWithPassThrough(id);    // 最原始的解决方法


        /*
            下面这个是经过工具类的封装
            第三个参数是一个函数Function 参数不能是id 因为前面第二个参数已经用过了 当参数是一个的时候可以使用lambda的形式
         */
        // Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿问题
        // Shop shop = queryWithPMutex(id);     // 原始方法
        // if (shop == null) {
        //     return Result.fail("店铺不存在！");
        // }


        // 逻辑过期解决缓存击穿问题
        // Shop shop = queryWithLogicalExpire(id);  // 原始方法
        // if (shop == null) {
        //     return Result.fail("店铺不存在！");
        // }


        // 工具类解决
        Shop shop = cacheClient.queryWithLogicalExpire(
                CACHE_SHOP_KEY, LOCK_SHOP_KEY, id, Shop.class, this::getById,
                20L, TimeUnit.SECONDS);


        // 7- 返回
        return Result.ok(shop);
    }


    /**
     * 缓存穿透解决方法: 缓存未命中 且 数据库中也不存在该店铺id对应的数据 -> 通过缓存空值来解决
     * @param id id
     * @return 结果
     */
    public Shop queryWithPassThrough(Long id) {
        // 1- 从redis中查询商户信息
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        // 2- 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3- 存在直接返回
            return JSONUtil.toBean(shopJson, Shop.class);

        }

        // (当数据库也不存在相关数据时, 想redis存储一个短暂时间的null)因此redis中存储的可能是空值 也可能是 店铺真实值 所以这里再判断一次
        if (shopJson != null) {
            return null;
        }

        // 4- 不存在 根据id查询数据库
        Shop shop = getById(id);

        // 5- 不存在数据库中
        if (shop == null) {
            // 将空值存储到redis中 空字符串 过期时间改成2分钟 不应该设置得太长
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 6- 存在数据库中 保存到redis中 -> 先转成JSON字符串 -> 过期时间设置为30分钟
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 7- 返回
        return shop;
    }


    /**
     * 互斥锁 -> 利用互斥锁解决缓存击穿问题 这里实际上采用了redis的setnx指令 因为setnx只有第一个设置的线程能够设置成功
     *          因此 后面的线程只能等待重试 这样就做到了只有其中一个线程能够拿到锁 进而进行重建缓存
     * @param id id
     * @return 结果
     */
    public Shop queryWithPMutex(Long id) {
        // 1- 从redis中查询商户信息
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        // 2- 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3- 存在直接返回
            return JSONUtil.toBean(shopJson, Shop.class);

        }

        // (当数据库也不存在相关数据时, 想redis存储一个短暂时间的null)因此redis中存储的可能是空值 也可能是 店铺真实值 所以这里再判断一次
        if (shopJson != null) {
            return null;
        }

        // 4- 实现缓存重建
        // 4.1- 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2- 判断是否获取成功
            if (!isLock) {
                // 4.3- 失败 则休眠重试 暂时尝试休眠50ms
                Thread.sleep(50);
                // 失败之后过了休眠还要重新尝试 因此就是递归
                return queryWithPMutex(id);
            }

            // 4.4- 成功 根据id查询数据库
            // 4- 不存在 根据id查询数据库
            shop = getById(id);

            // 模拟重建延时 让大量请求进入 测试锁的可靠性
            // Thread.sleep(200);

            // 5- 不存在数据库中
            if (shop == null) {
                // 将空值存储到redis中 空字符串 过期时间改成2分钟 不应该设置得太长
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 6- 存在数据库中 保存到redis中 -> 先转成JSON字符串 -> 过期时间设置为30分钟
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7- 释放锁 无论上面成不成功都需要释放锁
            unlock(lockKey);
        }

        // 8- 返回
        return shop;
    }


    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 使用逻辑缓存解决 缓存击穿
     * @param id id
     * @return 结果
     */
    public Shop queryWithLogicalExpire(Long id) {
        // 1- 从redis中查询商户信息
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        // 2- 判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            // 3- 未命中缓存直接返回null (一般来说不会存在这种问题 如果未命中的话那么只能说明该店铺并不是热点店铺 没有参加活动)
            return null;
        }

        // 4- 命中 需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        // 这里面反序列化取出来的字符串本质是JSONObject 然后再次转换为实体类
        Shop shop = JSONUtil.toBean(data, Shop.class);

        // 5- 是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        // 过期时间在当前时间之后 -> 说明还没有过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1- 未过期 返回
            return shop;
        }


        // 5.2- 过期 重建缓存
        // 6- 重建缓存
        String lockKey = LOCK_SHOP_KEY + id;
        // 6.1- 获取互斥锁
        boolean isLock = tryLock(lockKey);

        // 6.2- 失败 返回
        if (isLock) {
            // TODO: 6.3- 成功 开启独立线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }

        // 6.4- 返回过期的店铺信息
        return shop;
    }


    /**
     * 根据id更新店铺信息 同时删除redis缓存
     *
     * @param shop 商店
     * @return {@link Result}
     */
    @Override
    @Transactional
    public Result updateShopById(Shop shop) {
        // 先判断一下id是否存在
        if (shop.getId() == null) {
            return Result.fail("店铺id不能为空");
        }

        // 1- 更新数据库
        updateById(shop);

        // 2- 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());

        return Result.ok();
    }


    /**
     * 尝试获取锁
     * 通过redis的setnx命令来设置锁 这样只有第一个线程能够设置成功 其他线程设置时返回的结果均为0
     * @param key 键
     * @return flag为true则返回true 除此之外均为false
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


    // 将数据预热到redis
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1- 查询店铺数据
        Shop shop = getById(id);
        // Thread.sleep(200);

        // 2- 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        // 3- 写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

}
