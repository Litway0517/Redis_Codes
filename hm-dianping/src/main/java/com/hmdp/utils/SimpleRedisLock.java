package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private final StringRedisTemplate stringRedisTemplate;

    private final String name;

    private static final String KEY_PREFIX = "lock:";

    /**
     * id前缀 UUID偏移地址
     */
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    /**
     * 尝试获取锁
     *
     * @param timeoutSec 持有锁的时间, 超时自动释放锁, 单位秒
     * @return true代表锁获取成功; false代表锁获取失败
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        /*
            删除锁的时候, 线程之间可能会误删, 因此需要判断一下再删除锁. 方案就是key存储的value为 UUID+threadId
            Jvm内部每创建一个线程就会, 线程的id会自增, 不同的Jvm之间的进程id很容易出现相同, 因此加上一个ID_PREFIX偏移地址
         */
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        // Spring对返回的结果ok和nil进行了自动封装
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        // 防止返回null结果而导致boolean基本类型报错, return时再判断一次, success为null时返回false
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放锁
     */
    @Override
    public void unLock() {
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁中的标识
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if (threadId.equals(id)) {
            // 释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
