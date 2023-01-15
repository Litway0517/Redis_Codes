package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherService extends IService<Voucher> {

    /**
     * 查询店铺优惠券
     *
     * @param shopId 店铺id
     * @return {@link Result}
     */
    public Result queryVoucherOfShop(Long shopId);

    /**
     * 添加优惠券
     *
     * @param voucher 优惠券
     */
    public void addSeckillVoucher(Voucher voucher);
}
