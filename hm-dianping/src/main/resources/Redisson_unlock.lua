-- KEYS[1]这个是锁的key
-- ARGV[1]是超时时间, ARGV[2]是线程id
-- 调用脚本的时候传入的参数会给出keys和argv, 而且argv必须是string类型

if (redis.call('hexists', KEYS[1], ARGV[3]) == 0) then
    return nil;
end ;
local counter = redis.call('hincrby', KEYS[1], ARGV[3], -1);
if (counter > 0) then
    redis.call('pexpire', KEYS[1], ARGV[2]);
    return 0;
else
    redis.call('del', KEYS[1]);
    redis.call('publish', KEYS[2], ARGV[1]);
    return 1;
end ;
return nil;
