package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.BlogVo;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    /**
     * @param blog 帖子
     * @return {@link Result}
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 2. 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("新增笔记失败");
        }
        // 3. 查询博主的所有粉丝, 需要去follow表中查询, 博主id就是当前登录用户id, follow表中follow_user_id=登录用户id
        List<Follow> follows = followService.list(new LambdaQueryWrapper<>(Follow.class)
                .select(Follow::getUserId)
                .eq(Follow::getFollowUserId, user.getId())
        );
        // 4. 推送笔记id给所有粉丝, 实现粉丝的收件箱, 使用redis的zset结构
        for (Follow follow : follows) {
            // 4.1 获取粉丝id
            Long userId = follow.getUserId();
            // 4.2 推送, 这个key是收件箱的
            String key = "feed:" + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    /**
     * @param current 当前页数
     * @return 结果
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据 page对象中有许多数据
        List<Blog> records = page.getRecords();
        // 查询用户 -> 改成方法引用, 在lambda表达式中引用需要使用双冒号
        records.forEach(blog -> {
            this.queryUserByBlog(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * @param id 帖子id
     * @return {@link Result}
     */
    @Override
    public Result queryById(Long id) {
        // 返回一个封装的对象, 实际上在这里不这样也行
        BlogVo blogVo = new BlogVo();
        // 1. 查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("帖子不存在");
        }
        queryUserByBlog(blog);
        // 查询同时确定用户是否点赞
        isBlogLiked(blog);
        BeanUtil.copyProperties(blog, blogVo, true);
        return Result.ok(blogVo);
    }

    /**
     * @param blog 帖子
     */
    private void isBlogLiked(Blog blog) {
        // 1. 获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 当前没有登录用户
            return;
        }
        Long userId = user.getId();
        // 2. 判断当前用户是否点赞
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        /*
            isLike字段表示是否点赞, 用户在集合中isMember返回true表示已经点过赞
            更改为zset结构, score不为nil表示点赞
         */
        blog.setIsLike(score != null);
    }

    /**
     * @param id 帖子id
     */
    @Override
    public void likeBlog(Long id) {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2. 判断当前用户是否点赞
        String key = BLOG_LIKED_KEY + id;
        // 查询userId的score分数, 如果为nil说明redis中不存在
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 3. 如果未点赞, 可以点赞
            // 3.1 数据库点赞+1
            boolean isSuccess = update(new LambdaUpdateWrapper<>(Blog.class)
                    .setSql("liked = liked + 1")
                    .eq(Blog::getId, id));
            // 3.2 保存点赞用户到redis集合 -> 改成zset实现 zadd key score value
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 4. 如果已经点赞, 取消点赞
            // 4.1 数据库点赞 -1
            boolean isSuccess = update(new LambdaUpdateWrapper<>(Blog.class)
                    .setSql("liked = liked - 1")
                    .eq(Blog::getId, id));
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
    }

    /**
     * @param id 帖子id
     * @return {@link Result}
     */
    @Override
    public Result queryBlogLikesById(Long id) {
        String key = BLOG_LIKED_KEY + id;
        // 1. 查询top5点赞用户 zrange key 0 4
        Set<String> tops = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        // 2. 解析id
        if (tops == null || tops.isEmpty()) {
            // 给个空集合避免空指针
            return Result.ok(Collections.emptyList());
        }
        List<Long> userIds = tops.stream().map(Long::valueOf).collect(Collectors.toList());
        // 3. 查询用户信息 -> 返回的id顺序是点赞顺序, 但是传入到mysql中查询返回的结果是按照id从小到大查询的, 因此加上order by field(id, id字符串)
        // 使用last在最后拼接字符串
        String idStr = StrUtil.join(",", userIds);
        List<UserDTO> userDTOList = userService.
                list(new LambdaQueryWrapper<>(User.class)
                        .select(User::getId, User::getNickName, User::getIcon)
                        .in(User::getId,  userIds)
                        .last("ORDER BY FIELD(id, "+ idStr +")")
                )
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOList);
    }

    /**
     * @param blog 帖子
     */
    private void queryUserByBlog(Blog blog) {
        // 2. 查询blog有关的用户
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
