package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

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

    /**
     * @param followUserId 用户id
     * @param isFollow     是否关注
     * @return {@link Result }
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2. 判断到底是关注还是取关
        if (isFollow) {
            // 3. 关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            save(follow);
        } else {
            // 4. 取关
            remove(new LambdaQueryWrapper<>(Follow.class)
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId)
            );
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
}
