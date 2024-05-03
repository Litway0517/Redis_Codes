package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * @param blog 帖子
     * @return boolean
     */
    Result saveBlog(Blog blog);

    /**
     * @param current 当前页
     * @return {@link Result}
     */
    Result queryHotBlog(Integer current);

    /**
     * @param id 帖子id
     * @return {@link Result}
     */
    Result queryById(Long id);

    /**
     * @param id 帖子id
     */
    void likeBlog(Long id);

    /**
     * @param id 帖子id
     * @return {@link Result}
     */
    Result queryBlogLikesById(Long id);

    /**
     * @param max 每一次最大查询值
     * @param offset 偏移量
     * @return {@link Result }
     */
    Result queryBlogOfFollow(Long max, Integer offset);
}
