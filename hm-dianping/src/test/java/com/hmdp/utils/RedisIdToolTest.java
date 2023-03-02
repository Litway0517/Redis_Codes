package com.hmdp.utils;


import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

@SpringBootTest
public class RedisIdToolTest {

    @Resource
    private RedisIdTool redisIdTool;

    private static final long BEGIN_TIMESTAMP = 1640995200L;

    private final ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    public void testId() throws InterruptedException {
        // 300个线程 所以这里面填写300 这些线程会依次减少
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdTool.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };

        long begin = System.currentTimeMillis();
        // 使用500个线程将该任务提交300次 单位任务内容为生成100个id
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

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

    @Test
    public void lua() {
        String script = "if (redis.call('exists', KEYS[1]) == 0) then " +
                "redis.call('hincrby', KEYS[1], ARGV[2], 1); " +
                "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                "return nil; " +
                "end; " +
                "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
                "redis.call('hincrby', KEYS[1], ARGV[2], 1); " +
                "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                "return nil; " +
                "end; " +
                "return redis.call('pttl', KEYS[1]);";
        System.out.println(script);

    }

    @Test
    public void unlockRedisson() {
        String redissonUnlockScript = "if (redis.call('hexists', KEYS[1], ARGV[3]) == 0) then " +
                "return nil;" +
                "end; " +
                "local counter = redis.call('hincrby', KEYS[1], ARGV[3], -1); " +
                "if (counter > 0) then " +
                "redis.call('pexpire', KEYS[1], ARGV[2]); " +
                "return 0; " +
                "else " +
                "redis.call('del', KEYS[1]); " +
                "redis.call('publish', KEYS[2], ARGV[1]); " +
                "return 1; " +
                "end; " +
                "return nil;";
        System.out.println(redissonUnlockScript);
    }

    @Test
    public void hasNext() {
        List<String> strings = new ArrayList<>();
        strings.add("s1");
        strings.add("s2");
        strings.add("s3");

        for (Iterator<String> iterator = strings.iterator(); iterator.hasNext();) {
            String next = iterator.next();
            System.out.println(next);

        }


    }


}
