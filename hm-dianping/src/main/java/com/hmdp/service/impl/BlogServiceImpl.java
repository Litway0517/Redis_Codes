package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.BlogVo;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

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
        records.forEach(this::queryUserByBlog);
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
        return Result.ok(blogVo);
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
