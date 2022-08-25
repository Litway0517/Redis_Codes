package com.hmdp.utils;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class RedisIdTool {

    private static final long BEGIN_TIMESTAMP = 1640995200L;

    public long nextId(String keyPrefix) {
        // 1- 生成时间戳


        // 2- 生成序列号


        // 3- 拼接

        return 0L;
    }



    public static void main(String[] args) {
        // 获取一个初始时间对应的秒数
        LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0);
        long l = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println(l);
    }


}
