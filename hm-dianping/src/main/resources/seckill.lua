-- KEYS[1]这个是锁的key
-- ARGV[1]是超时时间, ARGV[2]是线程id
-- 调用脚本的时候传入的参数会给出keys和argv, 而且argv必须是string类型

-- 参数
-- 1.1 优惠券id
local voucherId = ARGV[1]
-- 1.2 用户id
local userId = ARGV[2]
-- 1.3 订单id
local id = ARGV[3]

-- 2. 数据key
-- 2.1. 库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2. 订单key, 存储的值是userId
local orderKey = 'seckill:order:' .. voucherId

-- 脚本业务
-- 3.1. 判断库存是否充足get stockKey
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 3.2. 库存不足, 返回1
    return 1
end

-- 3.2. 判断用户是否下单 SISMEMBER orderKey userId
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 3.3. 存在, 说明重复下单, 返回2
    return 2
end

-- 3.4. 扣库存 incrby stockKey -1
redis.call('incrby', stockKey, -1)
-- 3.5. 下单(保存用户)
redis.call('sadd', orderKey, userId)
-- 3.6. 直接向stream中存入信息 XADD stream.orders * k1 v1 k2 v2, 订单id直接改成id, 这样和VoucherOrder实体类的成员变量名称一致
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', id)
return 0