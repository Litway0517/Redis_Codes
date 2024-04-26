package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀优惠券
     * @param voucherId 优惠券id
     * @return 结果
     */
    Result secKillVoucher(Long voucherId);

    /**
     * 创建优惠券订单
     *
     * @param voucherId 券id
     * @return {@link Result}
     */
    Result createVoucherOrder(Long voucherId);

    /**
     * 创建订单优惠券
     * @param voucherOrder 优惠券
     */
    void createVoucherOrder(VoucherOrder voucherOrder);
}
