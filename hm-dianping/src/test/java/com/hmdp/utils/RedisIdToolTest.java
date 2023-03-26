package com.hmdp.utils;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;
import static org.junit.Assert.*;

@SpringBootTest
public class RedisIdToolTest {

    @Resource
    private RedisIdTool redisIdTool;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

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

    /**
     * Redisson获取锁脚本
     */
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

    /**
     * redisson释放锁lua脚本
     */
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

    /**
     * 测试for循环的初始化和循环成立条件, 注意初始化条件仅执行一次, 循环成立条件每次都需要判断
     */
    @Test
    public void hasNext() {
        List<String> strings = new ArrayList<>();
        strings.add("s1");
        strings.add("s2");
        strings.add("s3");

        // strings.iterator只是获取迭代器, 还没有开始迭代
        for (Iterator<String> iterator = strings.iterator(); iterator.hasNext();) {
            String next = iterator.next();
            System.out.println(next);

        }
    }

    /**
     * 添加用户
     */
    @Test
    public void addUsers() {
        // 数据库批量添加用户

        // 创建用户
        User user = new User();
        user.setPhone(randomPhoneNumber());
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));

        userMapper.insert(user);

        // 添加用户token
        addTokens(user);
    }

    /**
     * 生成随机电话号码
     *
     * @return {@link String}
     */
    public String randomPhoneNumber() {
        StringBuilder stringBuilder = new StringBuilder("1");
        Random random = new Random();

        // 生成后面十位数
        for (int i = 0; i < 10; i++) {
            stringBuilder.append(random.nextInt(10));
        }
        // System.out.println(stringBuilder.toString());
        return stringBuilder.toString();
    }

    /**
     * 添加token
     */
    @Test
    public void addTokens(User user) {
        // 生成UUID
        String token = UUID.randomUUID().toString(true);
        // token对应的key
        String tokenKey = LOGIN_USER_KEY + token;

        // 将用户信息脱敏
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreError(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);

    }

    /**
     * 查询用户信息
     */
    @Test
    public void selectUser() {
        // 查询条件
        LambdaQueryWrapper<User> userLambdaQueryWrapper = new LambdaQueryWrapper<>();

        // 查询所有用户
        List<User> userList = userMapper.selectList(userLambdaQueryWrapper);
    }


}
