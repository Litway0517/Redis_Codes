package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService iFollowservice;

    /**
     * @param followUserId 用户id
     * @param isFollow 是否关注
     * @return {@link Result }
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
        return iFollowservice.follow(followUserId, isFollow);
    }

    /**
     * @param id 用户id
     * @return {@link Result }
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long id) {
        return iFollowservice.isFollow(id);
    }

    /**
     * @param id 用户id
     * @return {@link Result }
     */
    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id) {
        return iFollowservice.followCommons(id);
    }
}