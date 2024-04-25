-- KEYS[1]这个是锁的key
-- ARGV[1]是超时时间, ARGV[2]是线程id
-- 调用脚本的时候传入的参数会给出keys和argv, 而且argv必须是string类型

-- 比较线程标识与所种的标识是否一致
if (redis.call('get', KEYS[1]) == ARGV[1]) then
    -- 释放锁 del key
    return redis.call('del', KEYS[1])
end
return 0
