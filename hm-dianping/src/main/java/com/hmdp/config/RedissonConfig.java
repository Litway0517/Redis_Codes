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
    /*
        @Bean注解用于将指定方法的返回值注入到Spring容器中, Spring容器默认是单例模式, 所以需要关注Bean的名称
        默认情况下, 使用@Bean时, 如果不指出name参数的值, 则使用方法名作为名称, 因此下面注入了三个Redis客户端实例
     */
    @Bean
    public RedissonClient redissonClient() {
        // 配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379").setDatabase(12);

        // 创建RedissonClient对象
        return Redisson.create(config);
    }

    // @Bean
    // public RedissonClient redissonClient2() {
    //     // 配置
    //     Config config = new Config();
    //     config.useSingleServer().setAddress("redis://127.0.0.1:6380").setDatabase(12);
    //
    //     // 创建RedissonClient对象
    //     return Redisson.create(config);
    // }
    //
    // @Bean
    // public RedissonClient redissonClient3() {
    //     // 配置
    //     Config config = new Config();
    //     config.useSingleServer().setAddress("redis://127.0.0.1:6381").setDatabase(12);
    //
    //     // 创建RedissonClient对象
    //     return Redisson.create(config);
    // }
}
