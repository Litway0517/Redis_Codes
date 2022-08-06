package com.hmdp.interceptor;

import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1- 获取session
        HttpSession session = request.getSession();

        // 2- 获取session中的用户
        Object user = session.getAttribute("user");

        // 3- 判断
        if (user == null) {
            // 4- 不存在 拦截 返回状态码401 未授权
            response.setStatus(401);
            return false;
        }

        // 5- 存在 保存到ThreadLocal 有工具类
        UserHolder.saveUser((User) user);
        return true;
    }

    /**
     * 拦截器的这个方法是在线程处理完之后执行的
     * @param request 请求
     * @param response 响应
     * @param handler 处理
     * @param ex 异常
     * @throws Exception 异常
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}
