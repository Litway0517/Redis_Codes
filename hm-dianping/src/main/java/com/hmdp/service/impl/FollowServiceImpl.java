package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
