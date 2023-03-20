package com.hmdp.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * redisson测试
 *
 * @author DELL_
 * @date 2023/02/28
 */
@Slf4j
@SpringBootTest
class RedissonTest {

    /**
     * redisson客户端, 注意注入名称
     */
    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RedissonClient redissonClient2;

    @Resource
    private RedissonClient redissonClient3;

    private RLock lock;

    /**
     * 测试方法执行前先执行该方法
     */
    @BeforeEach
    void setUp() {
        // 三个独立Redis节点对应三把独立锁
        RLock lock1 = redissonClient.getLock("order");
        RLock lock2 = redissonClient2.getLock("order");
        RLock lock3 = redissonClient3.getLock("order");

        /*
            创建联锁, getMultiLock方法是new RedissonMultiLock, 因此无论使用哪一个客户端都可以
            联锁在第二次获取时, 锁的过期时间会被看门狗重置, 前提是没有手动传入释放时间
         */
        lock = redissonClient.getMultiLock(lock1, lock2, lock3);
    }

    @Test
    void method1() throws InterruptedException {
        // 尝试获取锁
        /*
            tryLock(long time, TimeUnit unit)
            如果给出了time参数的话, 当线程获取锁失败后不会立即返回false, 而会在time规定的时间内重试, 如果time时间内仍未获取锁, 则返回false
         */
        boolean isLock = lock.tryLock(1L, TimeUnit.SECONDS);
        if (!isLock) {
            log.error("获取锁失败 .... 1");
            return;
        }
        try {
            log.info("获取锁成功 .... 1");
            method2();
            log.info("开始执行业务 ... 1");
        } finally {
            log.warn("准备释放锁 .... 1");
            lock.unlock();
        }
    }
    void method2() {
        // 尝试获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("获取锁失败 .... 2");
            return;
        }
        try {
            log.info("获取锁成功 .... 2");
            log.info("开始执行业务 ... 2");
        } finally {
            log.warn("准备释放锁 .... 2");
            lock.unlock();
        }
    }
}
