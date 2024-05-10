package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据商户id查询商户信息
     * @param id id
     * @return Result
     */
    public Result queryById(Long id);

    /**
     * 更新店铺信息
     *
     * @param shop 实体对象
     * @return 结果
     */
    public Result updateShopById(Shop shop);

    Result queryOfType(Integer typeId, Integer current, Double x, Double y);
}
