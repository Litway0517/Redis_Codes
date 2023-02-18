package com.hmdp.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 加上配置类注解, 将其加载到Spring容器
@Configuration
public class RedissonConfig {

    // RedissonClient是一个工厂类, 从中就能取到各种工具类
    @Bean
    public RedissonClient redisClient() {
        // 配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379").setDatabase(12);

        // 创建RedissonClient对象
        return Redisson.create(config);
    }
}
