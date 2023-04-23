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
import org.springframework.data.redis.core.*;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;
import static org.junit.Assert.*;

/**
 * Redis测试工具
 *
 * @author DELL_
 * @date null
 */
@SpringBootTest
public class RedisIdToolTest {

    @Resource
    private RedisIdTool redisIdTool;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisTemplate redisTemplate;

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
        
        // 判断每一个用户是否登录, 未登录则将其登录, 并将token存储到redis
        Integer count = 0;
        for (User user : userList) {
            if (!isRedisUser(user)) {
                addTokens(user);
                count++;
            }
        }
        System.out.println(count);

        // 遍历将用户信息脱敏
        for (User user : userList) {
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create().setIgnoreError(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        }
    }

    /**
     * 查询用户是否登录
     *
     * @param user 用户
     */
    @Test
    public void isLoginUser(User user) {
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreError(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
    }

    /**
     * 测试是否为Redis中登陆的用户
     */
    @Test
    public void testIsRedisUser() {
        User user = new User();
        user.setId(2102L);
        boolean flag = isRedisUser(user);
        System.out.println(flag);
    }

    /**
     * 是否为Redis中存储的用户
     */
    public boolean isRedisUser(User user) {
        // 扫描指定前缀的key
        Set<String> keys = stringRedisTemplate.keys("*");
        System.out.println(keys);

        Set<String> scanKeys = scanRedisMatchKeys("Login:*");

        // 对扫描到的结果遍历, 判断用户是否在登陆用户中
        for (String key : scanKeys) {
            Map<Object, Object> entry = stringRedisTemplate.opsForHash().entries(key);
            Long id = new Long(entry.get("id").toString());
            // 注意这里不能写成一条return语句, 含义不一致
            if (id.equals(user.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 扫描Redis中指定模式的keys
     *
     * @param keyPattern 关键模式
     * @return {@link Set}<{@link String}>
     */
    public Set<String> scanRedisMatchKeys(String keyPattern) {
        return (Set<String>) redisTemplate.execute((RedisCallback) connection -> {
            Set<String> set = new HashSet<>();
            Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(keyPattern).count(1000).build());

            try {
                while (cursor.hasNext()) {
                    // System.out.println("cursorId: " + cursor.getCursorId() + "cursorPosition: " + cursor.getPosition());
                    set.add(new String(cursor.next()));
                }
                // 游标cursor需要注意关闭, 否则会占用连接, 同时控制台也会提醒
                cursor.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return set;
        });
    }

    /**
     * Redis的扫描指令, 需要使用一个connection执行scan
     */
    @Test
    public void testRedisScanInstruction() {
        Set<String> result = (Set<String>) redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            // 这个就是扫描到的key值组成的set集合
            Set<String> set = new HashSet<>();
            Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match("Login:*").count(5).build());

            try {
                while (cursor.hasNext()) {
                    System.out.println("cursorId: " + cursor.getCursorId() + "cursorPosition: " + cursor.getPosition());
                    set.add(new String(cursor.next()));
                }
                // 游标cursor需要注意关闭, 否则会占用连接, 同时控制台也会提醒
                cursor.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return set;
        });
        System.out.println(result);

        for (String key : result) {
            // 根据key获取value
            Map<Object, Object> value = stringRedisTemplate.opsForHash().entries(key);
            System.out.println(value);
        }

        for (String key : result) {
            // 根据key获取value中的指定字段
            String nickName = (String) stringRedisTemplate.opsForHash().get(key, "nickName");
            System.out.println(nickName);
            // 传入一个集合, 通过multiGet方法一次性获取key对应的多个字段的值
            List<Object> list = stringRedisTemplate.opsForHash().multiGet(key, Arrays.asList("nickName", "id"));
            System.out.println(list);
        }

    }


}
