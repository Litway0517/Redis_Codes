package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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

    /**
     * 发送验证码
     *
     * @param phone   手机号
     * @param session session
     * @return 结果
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1- 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2- 如果不符合, 返回错误信息
            return Result.fail("手机号格式错误，请仔细检查！");
        }

        // 3- 符合, 生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 4- 保存验证码到session
        session.setAttribute("code", code);

        // 5- 发送验证码 这里面需要调用其他的服务 不是重点 所以以日志方式输出
        log.debug("发送验证码成功，验证码：{}", code);

        // 返回ok
        return Result.ok();
    }

    /**
     * 用户登录
     *
     * @param loginForm 登陆表单 使用@RequestBody接收前端的JSON字符串
     * @param session   session
     * @return 结果
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        /*
            采用反向校验 这样不用嵌套 不然就越套越深
         */

        // 因为login和sendCode是两个接口 这里必须再次校验手机号

        String phone = loginForm.getPhone();
        // 1- 校验提交的手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误，请仔细检查！");
        }

        // 2- 校验验证码
        // 先从session中获取验证码
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.toString().equals(code)) {
            // 3- 不一致则返回报错
            return Result.fail("验证码错误！");
        }

        // 4- 一致
        User user = query().eq("phone", phone).one();

        // 5- 根据手机号查询用户
        if (user==null) {
            // 6- 用户不存在 那么就创建并保存
            user = createUserWithPhone(phone);
        }

        // 7- 存在 保存到session中  注意存储的时候不存储完整的User信息 必须脱敏处理 不然就会有密码等信息
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        return Result.ok();
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
