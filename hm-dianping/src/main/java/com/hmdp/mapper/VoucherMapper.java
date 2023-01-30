package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface VoucherMapper extends BaseMapper<Voucher> {

    /**
     * 查询店铺的优惠券信息
     *
     * @param shopId 店铺id
     * @return {@link List}<{@link Voucher}>
     */
    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);

    /**
     * 选择所有
     *
     * @return {@link List}<{@link Voucher}>
     */
    List<Voucher> selectAll();


}
