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
            /*
                释放锁操作时, 在判断成立时发生了阻塞, 那么仍有可能误删其他线程的锁, 这里使用lua脚本解决
                - Redis通过Lua解释器执行lua脚本, redis在2.6.0版本之后内嵌了Lua解释器
                    通过EVAL script numkeys key [key...] arg [arg...]命令执行
                    script是脚本内容, numkeys是key参数的个数, KEYS是key参数数组, ARGV是其他参数数组

                - redis通过调用方式执行命令
                    redis.call('命令名称(如set get)', 'key', '其他参数', ...)
                    redis.call('set', 'name', 'Jack') <==> set name Jack
                    redis.call是内置方法

                - redis调用时添加参数
                    上面的调用是固定的, 没有可变参数, 而使用EVAL命令时能够传参, 如下
                    redis.call('命令名称', KEYS[1], ARGV[1]) 1 name Jack

                判断锁是否存在与释放锁原子性脚本如下
                if (redis.call('GET', KEYS[1]) == ARGV[1]) then
                    return redis.call('DEL', KEYS[1])
                end
                return 0
             */
            // 释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
