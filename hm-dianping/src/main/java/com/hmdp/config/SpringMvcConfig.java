package com.hmdp.config;

import com.hmdp.interceptor.CommonInterceptor;
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
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // order是能够控制拦截器的执行顺序的 数字越大表越放在后面执行 0 1 2 ...

        // 登录拦截器
        InterceptorRegistration loginInterceptor = registry.addInterceptor(new LoginInterceptor());
        // 放行以下请求 /**表示放行所有
        loginInterceptor.excludePathPatterns(
                "/user/code",
                "/user/login",
                "/blog/hot",
                "/shop/**",
                "/shop-type/**",
                "/upload/**",
                "/voucher/**"
        ).order(1);

        // token拦截器
        InterceptorRegistration commonInterceptor = registry.addInterceptor(new CommonInterceptor(stringRedisTemplate));
        // 拦截所有请求
        commonInterceptor.addPathPatterns(
                "/**"
        ).order(0);
    }
}
