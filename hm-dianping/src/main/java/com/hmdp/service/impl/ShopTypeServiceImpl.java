package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.entity.ShopTypeTuple;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.constant.ShopConstant.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询所有的商户类型
     *
     * @return Result
     */
    @Override
    public Result selectAllShopType() {
        // 1- 查询redis中是否存在商户类型
        Set<String> shopTypeSet = stringRedisTemplate.opsForZSet().range(CACHE_SHOP_TYPE_KEY, 0, 100);
        if (shopTypeSet != null && !shopTypeSet.isEmpty()) {
            // 2- 有 -> 直接返回
            List<ShopType> shopTypeList = shopTypeSet.stream().map(shopType -> JSONUtil.toBean(shopType, ShopType.class)).collect(Collectors.toList());
            return Result.ok(shopTypeList);
        }

        // 3- 没有 -> 到数据库中查询所有的商户信息
        List<ShopType> shopType = lambdaQuery().orderByAsc(ShopType::getSort).list();

        // 4- 判断是否存在
        if (shopType != null && !shopType.isEmpty()) {
            // 5- 将数据组织好存储到redis  存储到Redis使用Zset类型, 对应ZSetOperations.TypedTuple

            Set<ZSetOperations.TypedTuple<String>> shopTypeTuples = new HashSet<ZSetOperations.TypedTuple<String>>();
            shopType.stream().forEach(shopTypeItem -> {
                ShopTypeTuple shopTypeTuple = new ShopTypeTuple(shopTypeItem, shopTypeItem.getSort().doubleValue());
                shopTypeTuples.add(shopTypeTuple);
            });
            stringRedisTemplate.opsForZSet().add(CACHE_SHOP_TYPE_KEY, shopTypeTuples);
        }

        // 6- 返回
        return Result.ok(shopType);
    }
}
