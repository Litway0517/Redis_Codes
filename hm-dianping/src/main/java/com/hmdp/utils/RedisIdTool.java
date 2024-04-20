package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static com.hmdp.utils.RedisConstants.INCREMENT;


@Component
public class RedisIdTool {

    /**
     * 基础秒数 -> 因为算法的高32位为时间 但是以秒数为存储 所以这里要计算一个开始时间距离1970-1-1的秒数
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    /**
     * 序列号的位数 -> 如果以后的算法设计中序列号位数增加的话 就可以改这里
     */
    private static final int COUNT_BITS = 32;

    private final StringRedisTemplate stringRedisTemplate;

    // 这是通过构造方法的方式去初始化StringRedisTemplate
    public RedisIdTool(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 生成唯一的id
     * @param keyPrefix 业务前缀
     * @return id
     */
    public long nextId(String keyPrefix) {
        // 1- 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSeconds - BEGIN_TIMESTAMP;

        // 2- 生成序列号
        /*
            不能这样递增:
                stringRedisTemplate.opsForValue().increment("Icr" + keyPrefix);
                因为这样的话就表明, keyPrefix对应的业务都是使用的一个自增的id, 那么随着时间的漂移总会有超过redis上限2^64的时候,
                而且此处设计的算法其序列号仅有32位, 并不是64位. 2^32大约为42亿, 这个值还是很可能超过的.
                方法: 在key后面再加上一个日期, 还能起到一个统计效果. 比如加上单位天, 则能够统计当日的订单量, 加上月份能够统计当月的订单量.
                在redis中, 以冒号为分隔符之后redis会自动帮助我们分层, 便于观察.

                下面提示可能会报出Null错误, 其实不会: 因为他认为在拆箱的过程中, 如果说你redis库中压根不存在key=A的键, 那么自增可能会出现Null,
                实际上经过实践得出, 如果redis的库中没有对应key为A的键, redis会自动创建, 并且赋值为1.
         */
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment(INCREMENT + keyPrefix + date);

        // 3- 拼接 -> 算法设计为 最高位为1(符号位)  紧接着31位为时间戳  后32为为序列号. 因此需要先将时间戳左移32位 然后再进行或运算
        return timestamp << COUNT_BITS | count;
    }

}
