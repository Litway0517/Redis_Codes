package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;


/*
    实现WebMvcConfig接口表示这个类是MVC的配置类
    并且加上@COnfiguration注解标识为这个类是一个配置类
 */
@Configuration
public class SpringMvcConfig implements WebMvcConfigurer {

    // @Configuration注解表示改类由Spring容器进行管理
    @Resource
    private StringRedisTemplate redisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        InterceptorRegistration loginInterceptor = registry.addInterceptor(new LoginInterceptor(redisTemplate));
        // 放行以下请求 /**表示放行所有
        loginInterceptor.excludePathPatterns(
                "/user/code",
                "/user/login",
                "/blog/hot",
                "/shop/**",
                "/shop-type/**",
                "/upload/**",
                "/voucher/**"
        );
    }
}
