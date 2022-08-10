package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static com.hmdp.constant.ShopConstant.CACHE_SHOP_KEY;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 根据商户id查询商户信息
     *
     * @param id id
     * @return Result
     */
    @Override
    public Result queryById(Long id) {
        // 1- 从redis中查询商户信息
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        // 2- 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3- 存在直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop );
        }

        // 4- 不存在 根据id查询数据库
        Shop shop = getById(id);

        // 5- 不存在数据库中 直接报错
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }

        // 6- 存在数据库中 保存到redis中 -> 先转成JSON字符串
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));

        // 7- 返回
        return Result.ok(shop);
    }
}
