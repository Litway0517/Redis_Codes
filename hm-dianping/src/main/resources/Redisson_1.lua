-- KEYS[1]这个是锁的key
-- ARGV[1]是超时时间, ARGV[2]是线程id

-- 如果锁不存在
if (redis.call('exists', KEYS[1]) == 0) then
    -- 将key对应的线程id计数+1(使用指令hincrby即使在一个key不存在的情况下使其+1)
    redis.call('hincrby', KEYS[1], ARGV[2], 1);
    -- 设置key的超时时间未ARGV[1]
    redis.call('pexpire', KEYS[1], ARGV[1]);
    -- 返回nil
    return nil;
end ;

-- 如果锁存在 & 对应线程的计数值也存在
if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then
    -- 对应线程的计数+1
    redis.call('hincrby', KEYS[1], ARGV[2], 1);
    -- 重置超时时间为ARGV[1]
    redis.call('pexpire', KEYS[1], ARGV[1]);
    -- 返回nil
    return nil;
end ;

-- key对应的锁存在 并且不是当前现成的
return redis.call('pttl', KEYS[1]);
