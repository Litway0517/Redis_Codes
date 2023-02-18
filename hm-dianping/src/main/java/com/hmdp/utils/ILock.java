package com.hmdp.utils;

/**
 * ILock
 *
 * @author DELL_
 * @date 2023/02/14
 */
public interface ILock {

    /**
     * 尝试获取锁
     *
     * @param timeoutSec 持有锁的时间, 超时自动释放锁, 单位秒
     * @return true代表锁获取成功; false代表锁获取失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
