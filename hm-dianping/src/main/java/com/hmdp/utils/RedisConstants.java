package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "Login:Code:";
    public static final Long LOGIN_CODE_TTL = 30L;


    public static final String LOGIN_USER_KEY = "Login:Token:";
    public static final Long LOGIN_USER_TTL = 30L;


    public static final Long CACHE_NULL_TTL = 2L;
    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "Cache:Shop:";

    public static final String LOCK_SHOP_KEY = "Lock:Shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    // redis的分隔符
    public static final String DELIMITER = ":";

    // redis自增
    public static final String INCREMENT = "Icr:";

    // 优惠券订单前缀
    public static final String SECKILL_ORDER = "Seckill:Order:";


    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
