package com.hmdp.constant;

public class UserConstant {

    // 用户发送验证码时 服务器将验证码存储到redis中 用到的key的前缀
    public static final String USER_LOGIN_SEND_CODE_KEY = "Login:Code:";

    // 用户发送验证码的超时时间
    public static final int USER_LOGIN_CODE_TIMEOUT = 2;

    // 从请求头中提出token的字段名
    public static final String AUTHORIZATION = "authorization";




}
