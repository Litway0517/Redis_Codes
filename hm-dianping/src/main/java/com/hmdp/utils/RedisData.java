package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {
    // 逻辑过期时间
    private LocalDateTime expireTime;

    // 能够存放任意对象 例如Shop实体
    private Object data;
}
