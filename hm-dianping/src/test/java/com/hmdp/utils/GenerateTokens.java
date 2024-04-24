package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@SpringBootTest
public class GenerateTokens {

    @Resource
    private UserServiceImpl userService;

    private final ExecutorService es = Executors.newFixedThreadPool(500);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 使用线程池插入, 但是是一条一条插入, 比下面的批量插入慢太多了
    @Test
    public void genUsers() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(100);

        Runnable task = () -> {
            for (int i = 0; i < 10; i++) {
                // 1- 创建用户
                User user = new User();
                String phone = genRandomPhoneNumber();
                user.setPhone(phone);
                user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
                // 2- 保存用户
                userService.save(user);
            }
            latch.countDown();
        };

        long start = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("运行时间" + (end - start));
    }

    // 批量插入
    @Test
    public void genUsers2() {
        ArrayList<User> list = new ArrayList<User>();
        for (int i = 0; i < 1000; i++) {
            User user = new User();
            user.setPhone(genRandomPhoneNumber());
            user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
            list.add(user);
        }

        userService.saveBatch(list);
    }

    @Test
    public void delUsers() {
        // userService.removeByIds();
    }

    @Test
    public void genTokens() {
        List<User> userList = userService.list(new LambdaQueryWrapper<>(User.class)
                .select(User::getId, User::getPhone, User::getNickName)
                .gt(User::getId, 1037)
        );

        ArrayList<String> tokens = new ArrayList<>();
        userList.forEach(user -> {
            // token前缀
            String token = UUID.randomUUID().toString(true);
            // 存储到redis中的token
            String tokenKey = LOGIN_USER_KEY + token;
            tokens.add(token);

            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create().setIgnoreError(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
            stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        });

        // 写入到txt
        try {
            FileWriter fileWriter = new FileWriter("L:\\IDEA-Java\\15-HM-Redis\\Redis_Code\\Redis_Code\\Redis_Code\\hm-dianping\\src\\main\\resources\\tokens.txt");
            tokens.forEach(token -> {
                try {
                    fileWriter.write(token);
                    fileWriter.write("\n");
                    fileWriter.flush();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public String genRandomPhoneNumber() {
        Random rand = new Random();

        // 随机选择前三位数字
        String firstThreeDigits = String.format("1%s", rand.nextInt(8) + 3);

        // 生成后八位随机数字
        StringBuilder lastEightDigits = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            lastEightDigits.append(rand.nextInt(10));
        }

        // 拼接成完整的手机号码
        return firstThreeDigits + lastEightDigits;
    }

    @Test
    public void testPath() {
        String path = this.getClass().getResource("classpath:tokens.txt").getPath();
        System.out.println(path);
    }

}