package com.hmdp.utils;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.Assert.*;

public class RedisIdToolTest {

    private static final long BEGIN_TIMESTAMP = 1640995200L;

    @Test
    public void testCount() {
        LocalDateTime now = LocalDateTime.now();
        long currentSeconds = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = currentSeconds - BEGIN_TIMESTAMP;
        long time = timestamp << 32;
        long count = 1;
        long result = time | count;
        System.out.println(result);

        long a = 100;
        long b = a << 2;
        System.out.println(b);
        long c = 1;
        long d = b | c;
        System.out.println(d);

    }


}
