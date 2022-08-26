package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;


@Component
public class RedisIdTool {

    /**
     * 基础秒数
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    /**
     * 序列号的位数 -> 如果以后的算法设计中序列号位数增加的话 就可以改这里1
     */
    private static final int COUNT_BITS = 32;

    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdTool(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        // 1- 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSeconds - BEGIN_TIMESTAMP;

        // 2- 生成序列号
        /*
            不能这样递增:
                stringRedisTemplate.opsForValue().increment("Icr" + keyPrefix);
                因为这样的话就表明, keyPrefix对应的业务都是使用的一个自增的id, 那么随着时间的漂移总会有超过redis上限2^64的时候
                因此在key后面再加上一个日期
         */
        long count = stringRedisTemplate.opsForValue().increment("Icr" + keyPrefix);

        // 3- 拼接 -> 算法设计为 最高位为1(符号位)  紧接着31位为时间戳  后32为为序列号. 因此需要先将时间戳左移32位 然后再进行或运算
        return timestamp << COUNT_BITS | count;
    }

}
