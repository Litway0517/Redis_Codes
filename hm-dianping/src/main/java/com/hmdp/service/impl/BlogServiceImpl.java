package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.BlogVo;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

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
        BeanUtil.copyProperties(blog, blogVo, true);
        // 查询同时确定用户是否点赞
        isBlogLiked(blog);
        return Result.ok(blogVo);
    }

    /**
     * @param blog 帖子
     */
    private void isBlogLiked(Blog blog) {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2. 判断当前用户是否点赞
        String key = BLOG_LIKED_KEY + blog.getUserId();
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        // isLike字段设置true表示已经点过赞
        blog.setIsLike(BooleanUtil.isTrue(isMember));
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
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        if (BooleanUtil.isFalse(isMember)) {
            // 3. 如果未点赞, 可以点赞
            // 3.1 数据库点赞+1
            boolean isSuccess = update(new LambdaUpdateWrapper<>(Blog.class)
                    .setSql("liked = liked + 1")
                    .eq(Blog::getId, id));
            // 3.2 保存点赞用户到redis集合
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key, userId.toString());
            }
        } else {
            // 4. 如果已经点赞, 取消点赞
            // 4.1 数据库点赞 -1
            boolean isSuccess = update(new LambdaUpdateWrapper<>(Blog.class)
                    .setSql("liked = liked - 1")
                    .eq(Blog::getId, id));
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key, userId.toString());
            }
        }
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
