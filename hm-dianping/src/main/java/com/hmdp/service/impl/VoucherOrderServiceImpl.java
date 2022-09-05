package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdTool;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

import static com.hmdp.utils.RedisConstants.SECKILL_ORDER;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service("voucherOrderService")
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdTool redisIdTool;

    /**
     * 秒杀优惠券
     *
     * @param voucherId 优惠券id
     * @return 结果
     */
    @Override
    @Transactional
    public Result secKillVoucher(Long voucherId) {
        // 1- 根据优惠券id查询优惠券信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher == null) {
            // 优惠券为空
            return Result.fail("优惠券异常~");
        }

        // 2- 判断 秒杀是否开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 未开始 直接返回
            return Result.fail("活动尚未开始哟，请耐心等待~");
        }

        // 3- 判断 秒杀是否结束
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 已结束 直接返回
            return Result.fail("活动已结束，下次记着早点参与呦~");
        }

        // 4- 判断 库存是否充足
        Integer stock = seckillVoucher.getStock();
        if (stock < 1) {
            // 不充足 直接返回
            return Result.fail("今日优惠券已经发放完毕，请明日记着早点来呦~");
        }

        // 5- 扣减库存 对于最后一张优惠券有可能出现问题
        /*
            setSql -> 实际上就是set条件 -> set stock = stock - 1
            这里eq就是where条件 -> 要求SeckillVoucher::getVoucherId字段
                                (这里是lambda表达式来取字段名, 这样就不会导致输错字段名这种情况)的值为voucherId

            update tb_seckill_voucher set stock = stock - 1 where id = voucherId and stock = 原stock
            - 改进失败率很高的问题: 只要库存 > 0 即可成功. 但是这样会引发新的问题
            - 改进: 还是改成乐观锁, 然后加上延时和自旋, 这样解决应该会更好. 待优化.
         */
        boolean success = seckillVoucherService.lambdaUpdate()
                .setSql("stock = stock - 1")
                .eq(SeckillVoucher::getVoucherId, voucherId)
                .gt(SeckillVoucher::getStock, 0).update();
        if (!success) {
            // try {
            //     Thread.sleep(10);
            //     this.secKillVoucher(voucherId);
            // } catch (Exception e) {
            //     throw new RuntimeException(e);
            // }

            // 原因也可能是库存不足, 所以返回这个结果 -> 改成延时和自旋, 下面的代码就不用了
            return Result.fail("今日优惠券已经发放完毕，请明日记着早点来呦~");
        }

        // 6- 新增订单信息
        VoucherOrder voucherOrder = new VoucherOrder();
        // 设置订单id(使用全局唯一生成工具类) | 用户id | 优惠券id
        long orderId = redisIdTool.nextId(SECKILL_ORDER);
        Long userId = UserHolder.getUser().getId();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        // 7- 返回结果
        return Result.ok(orderId);
    }
}
