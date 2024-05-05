package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Resource
    private IBlogService blogService;

    private static final DefaultRedisScript<String> REMOVE_FEED_SCRIPT;
    static {
        REMOVE_FEED_SCRIPT = new DefaultRedisScript<>();
        // ClassPathResource类用来加载resources目录下的指定文件
        REMOVE_FEED_SCRIPT.setLocation(new ClassPathResource(SystemConstants.LUA_SCRIPT_REMOVE_FEED_FILENAME));
        REMOVE_FEED_SCRIPT.setResultType(String.class);
    }

    /**
     * @param followUserId 用户id
     * @param isFollow     是否关注
     * @return {@link Result }
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        // 2. 判断到底是关注还是取关
        if (isFollow) {
            // 3. 关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                // 把关注用户的id, 放入到redis集合中 sadd userId followUserId
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            // 4. 取关
            boolean isSuccess = remove(new LambdaQueryWrapper<>(Follow.class)
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId)
            );
            if (isSuccess) {
                // 把关注用户的id从redis集合中移除
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());

                // 把属于关注用户的帖子id从粉丝的收件箱中移除
                List<Blog> blogs = blogService.list(new LambdaQueryWrapper<>(Blog.class)
                        .select(Blog::getId)
                        .eq(Blog::getUserId, followUserId));
                if (blogs == null || blogs.isEmpty()) {
                    return Result.ok();
                }
                List<Long> ids = blogs.stream().map(Blog::getId).collect(Collectors.toList());
                // 传入的是6, 7, 15格式的字符串, 在lua脚本中进行了拼接成table, 最终结果正确
                String idsStr = StrUtil.join(",", ids);
                // redis本身没有提供批量移除API, 这里使用lua脚本实现
                String r = stringRedisTemplate.execute(
                        REMOVE_FEED_SCRIPT,
                        Collections.emptyList(),
                        userId.toString(), idsStr
                );
                System.out.println(r);
            }
        }
        return Result.ok();
    }

    /**
     * @param id 用户id
     * @return {@link Result }
     */
    @Override
    public Result isFollow(Long id) {
        // 1. 获取用户
        Long userId = UserHolder.getUser().getId();
        // 2. 查询用户
        Integer count = lambdaQuery().eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, id).count();
        return Result.ok(count > 0);
    }

    /**
     * 求当前用户和访问用户的共同关注
     * @param id 用户id
     * @return {@link Result }
     */
    @Override
    public Result followCommons(Long id) {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        String loginUserKey = "follows:" + userId;
        // 2. 当前用户
        String userKey = "follows:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(loginUserKey, userKey);
        // 3. 解析id集合
        if (intersect == null) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        // 4. 查询用户
        List<UserDTO> userDTOList = userService.
                listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOList);
    }
}
