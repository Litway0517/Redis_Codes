package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constant.UserConstant.AUTHORIZATION;



/*
    TODO:
        - 这个LoginInterceptor拦截器并不是由spring容器管理的, 也就是说初始化的时候是手动new出来的, 就在SpringMvcConfig中
        - 因此为了在LoginInterceptor中使用redisTemplate, 应该使用构造方法
        SpringMvcConfig这个类是由spring容器进行管理创建的, 因此可以在这个配置类自动注入redisTemplate, 然后再赋值给LoginInterceptor.

        不使用以上方法的话, 就在LoginInterceptor加上@Component注解
 */

public class LoginInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1- 获取session -> 改为获取请求头中的token
        String token = request.getHeader(AUTHORIZATION);
        if (StrUtil.isBlank(token)) {
            // 用户未登录 直接拦截. 返回状态码401 未授权.
            response.setStatus(401);
            return false;
        }

        // 2- 获取session中的用户 -> 改为基于token获取redis中的用户信息
        String loginUserKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(loginUserKey);

        // 3- 判断用户是否存在 即是否已经登录
        if (userMap.isEmpty()) {
            // 未登录 直接拦截. 返回状态码401 未授权
            response.setStatus(401);
            return false;
        }

        // 5- 将 从redis中取出来的用户转为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        /*
            TODO: 6- 存在 保存到ThreadLocal 有工具类.
            注意这里仍然是保存到ThreadLocal的内存中 并不是redis 因为这个请求会被分发到不同的tomcat
         */
        UserHolder.saveUser(userDTO);

        /*
            TODO: 7- 刷新token的有效期 即用户登录状态的有效期
            只有用户连续30分钟不操作才会在redis中清楚这个用户 这也是session的做法
         */
        stringRedisTemplate.expire(loginUserKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        return true;
    }

    /**
     * 拦截器的这个方法是在线程处理完之后执行的
     *
     * @param request  请求
     * @param response 响应
     * @param handler  处理
     * @param ex       异常
     * @throws Exception 异常
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}
