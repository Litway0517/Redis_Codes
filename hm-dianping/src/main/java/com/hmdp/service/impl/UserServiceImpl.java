package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码
     *
     * @param phone   手机号
     * @return 结果
     */
    @Override
    public Result sendCode(String phone) {
        // 1- 校验手机号 返回值true表示不匹配
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2- 如果不符合, 返回错误信息
            return Result.fail("手机号格式错误，请仔细检查！");
        }

        // 3- 符合, 生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 4- 保存验证码到session -> 更改成保存到redis
        /*
            因为redis是一个共享的存储域 如果说直接存储 手机号 为key 那么可能其他业务也是这样 这就会覆盖数据 甚至报错
            因此我们加上USER_LOGIN_KEY的业务前缀
            设置为30秒
         */
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.SECONDS);

        // 5- 发送验证码 这里面需要调用其他的服务 不是重点 所以以日志方式输出
        log.debug("发送验证码成功，验证码：{}", code);

        // 返回ok
        return Result.ok();
    }

    /**
     * 用户登录
     *
     * @param loginForm 登陆表单 使用@RequestBody接收前端的JSON字符串
     * @return 结果
     */
    @Override
    public Result login(LoginFormDTO loginForm) {
        /*
            采用反向校验 这样不用嵌套 不然就越套越深
         */

        // 因为login和sendCode是两个接口 这里必须再次校验手机号
        String phone = loginForm.getPhone();
        // 1- 校验提交的手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误，请仔细检查！");
        }

        // 2- 校验验证码 -> 从redis中获取验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null) {
            return Result.fail("验证码已经超时，请重新发送！");
        }
        if (!code.equals(cacheCode)) {
            // 不一致
            return Result.fail("验证码错误！");
        }

        // 4- 一致 根据手机号查询用户
        User user = lambdaQuery().eq(StrUtil.isNotBlank(phone), User::getPhone, phone).one();

        // 5- 用户是否存在
        if (user == null) {
            // 6- 用户不存在 那么就创建并保存
            user = createUserWithPhone(phone);
        }

        // TODO: 7- 保存用户信息到redis中  注意存储的时候不存储完整的User信息 必须脱敏处理 不然就会有密码等信息
        // 7.1 随机生成token, 作为登陆令牌
        String token = UUID.randomUUID().toString(true);

        // 7.2 将User对象转换为UserDTO再转换为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        /*
            因为StringRedisTemplate是一个存储<String, String>的模板 但是UserDTO的id是Long 因此需要转换
            setFieldValueEditor是字段值的修改器, 接收一个函数, 函数接收两个参数, 字段名, 字段值. 返回值是修改后的字段值.
         */
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreError(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        // 7.3 存储 -> 改为存储到redis key的结构是token value的结构是hash
        String tokenKey = LOGIN_USER_KEY + token;
        // 向redis中保存脱敏用户的信息, 将token作为键, 用户作为value这样就对应了. 前面的拦截器检查如果请求没有token直接放行
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 设置有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 返回token, 前端通过sessionStorage将token保存到浏览器, 前端所有请求通过common.js预处理发送请求
        return Result.ok(token);
    }

    /**
     * 签到
     *
     * @return {@link Result }
     */
    @Override
    public Result sign() {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2. 获取当前日期
        LocalDateTime now = LocalDateTime.now();
        // 3. 拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4. 获取今天是本月第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5. 写入redis setbit key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    /**
     * 符号计数
     *
     * @return {@link Result }
     */
    @Override
    public Result signCount() {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2. 获取当前日期
        LocalDateTime now = LocalDateTime.now();
        // 3. 拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4. 获取今天是本月第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5. 查询当前签到结果
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands
                        .create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            // 没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 6. 循环遍历
        int count = 0;
        while (true) {
            /*
                6.1 让这个数字与1做与运算, 得到的数字的最后一个bit位, 判断结果是否为0
                java中两个数字做与运算时自动转换为二进制, 不需要手动转换, 1前面会补零, 所以与1运算就可以判断对应数字的最后一位
             */
            if ((num & 1) == 0) {
                // 如果为0, 说明未签到, 结束
                break;
            } else {
                // 如果不为0, 说明已签到, 计数器+1
                count++;
            }
            // 把数字向右移1位, 抛弃最后一个bit位, 继续下一个bit位, >>>表示无符号右移, 相当于除2
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        // 1- 创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));

        // 2- 保存用户
        save(user);
        return user;
    }
}
