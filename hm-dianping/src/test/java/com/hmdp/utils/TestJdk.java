package com.hmdp.utils;

import org.junit.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TestJdk {

    @Test
    public void testRedissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:16379").setPassword("root@B16");
        RedissonClient redissonClient = Redisson.create(config);
        System.out.println(redissonClient);

    }



}
