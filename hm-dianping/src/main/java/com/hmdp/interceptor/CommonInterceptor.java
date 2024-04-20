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

/**
 * TODO:
 *         - 这个LoginInterceptor拦截器并不是由spring容器管理的, 也就是说初始化的时候是手动new出来的, 就在SpringMvcConfig中
 *         - 因此为了在LoginInterceptor中使用redisTemplate, 应该使用构造方法
 *         - LoginInterceptor拦截器, 后来封装成了CommonInterceptor
 *         SpringMvcConfig这个类是由spring容器进行管理创建的, 因此可以在这个配置类自动注入redisTemplate, 然后再赋值给LoginInterceptor.
 * <p>
 *         不使用以上方法的话, 就在LoginInterceptor加上@Component注解
 * <p>
 *
 * TODO:
 *         因为LoginInterceptor拦截器仅仅拦截的是部分需要登陆的界面.
 *         因此考虑一种情况: 那就是用户登录了之后, 30分钟内一直访问其他不需要登录的界面, 30分钟后仍然会丢掉登陆状态
 * <p>
 *         这里再使用一个拦截器解决这个问题
 * <p>
 * TODO:
 *         所有的请求都会被该拦截器拦截并检查是否携带了authorization请求头, 携带了该请求头则查询对应的redis用户信息并转换为User对象,
 *         然后将用户信息存到当前的线程中, 无论该线程分发到哪个Tomcat服务器都会包含用户登陆的信息, 从而达到redis共享登录信息的目的.
 */
public class CommonInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public CommonInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1- 获取session -> 改为获取请求头中的token
        String token = request.getHeader(AUTHORIZATION);
        if (StrUtil.isBlank(token)) {
            // 请求中没有token: 直接放行 -> 不会执行下面的代码(放行了之后就会继续执行该请求地址请求的资源, 当然是在没有其他拦截器的情况下) 没有token就不根据token去查询登录信息
            return true;
        }

        // 2- 获取session中的用户 -> 改为基于token获取redis中的用户信息
        String loginUserKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(loginUserKey);

        // 3- 判断用户是否存在 即是否已经登录
        if (userMap.isEmpty()) {
            // 没有获取到用户: 放行
            return true;
        }

        // 5- 将 从redis中取出来的用户转为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        /*
            TODO: 6- 存在 保存到ThreadLocal 有工具类.
            注意这里仍然是保存到ThreadLocal的内存中 并不是redis 因为这个请求会被分发到不同的tomcat
            请求到达nginx后, 有可能会被分发到不同的Tomcat中, 这样来说就会出现在第一个Tomcat中登录了, 但是在第二个Tomcat中未登录.
            但是, 每次将登录信息保存到ThreadLocal中就不会出现这种问题. 当然每次需要从redis中查询用户登录的token令牌
            每当请求进来Tomcat总会默认开启一个新的线程去处理这个请求, 该请求即使被分发到不同的Tomcat服务器后, 仍能获取到用户登录信息 因为用户信息保存在线程中
         */
        UserHolder.saveUser(userDTO);

        /*
            TODO: 7- 刷新token的有效期 即用户登录状态的有效期
            只有用户连续30分钟不操作才会在redis中清楚这个用户 这也是session的做法
         */
        stringRedisTemplate.expire(loginUserKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

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
