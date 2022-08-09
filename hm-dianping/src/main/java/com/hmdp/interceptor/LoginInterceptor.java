package com.hmdp.interceptor;

import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;



/*
    TODO:
        - 这个LoginInterceptor拦截器并不是由spring容器管理的, 也就是说初始化的时候是手动new出来的, 就在SpringMvcConfig中
        - 因此为了在LoginInterceptor中使用redisTemplate, 应该使用构造方法
        SpringMvcConfig这个类是由spring容器进行管理创建的, 因此可以在这个配置类自动注入redisTemplate, 然后再赋值给LoginInterceptor.

        不使用以上方法的话, 就在LoginInterceptor加上@Component注解

    TODO: LoginInterceptor拦截器不再需要RedisTemplate, 因为放到了CommonInterceptor中了
 */

public class LoginInterceptor implements HandlerInterceptor {


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (UserHolder.getUser() == null) {
            // 用户未登录 直接拦截. 返回状态码401 未授权.
            response.setStatus(401);
            return false;
        }

        // 放行
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
