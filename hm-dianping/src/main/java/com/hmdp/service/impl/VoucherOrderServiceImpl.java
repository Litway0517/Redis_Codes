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
import org.springframework.aop.framework.AopContext;
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

        // 将创建订单逻辑抽离出来, 作为一个方法
        /*
            sync锁应该锁住的是该方法, 锁的id是用户的id
            直接调用方法时, 使用的是this对象调用, 而this对象不是Spring动态代理的对象, 因此需要通过AopContext获取到代理对象,
            同时引入aspectj依赖, 并在引导类上开启Aop对外暴露, 这样事务就不会失效了

            锁失效
                当SpringBoot服务集群部署时, 就会出现线程并发安全问题, 因为8081和8082是不一样的进程, 注意是进程. 这样就相当于是
                两个应用, 两个进程, 两个Jvm, 两个Tomcat. 对于跨Jvm情况, synchronized锁自然会失效, 因为Jvm内部有一个锁监视器,
                锁监视器会控制线程串行执行, 不同的Jvm自然对应不同的锁监视器, 因此每个Tomcat中都会有一个进程成功.
         */
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            // 获取跟事务有关的代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            // 事务提交之后才会释放锁
            return proxy.createVoucherOrder(voucherId);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        /*
            synchronized锁改成代码块锁, 如果是public synchronized Result createVoucherOrder(id)加锁将是针对service对象,
            是一个方法锁, 以方法为单位, 凡是执行这个方法的线程都会互斥, 单线程执行.
            锁的粒度太粗了, 会影响到每个线程的执行, 因此控制锁的粗粒度是一件很重要的事情.
         */

        // 5- 一人一单功能(存在并发安全性问题)
        Long userId = UserHolder.getUser().getId();

        /*
            如果使用userId.toString作为锁的id, 那么每次都是new一个字符串(跟进源码就能发现), 所以调用intern方法字符串常量池,
            如果字符串常量池中已有相等的string字符, 则返回池中字符串, 否则将该值加入到池中并返回引用
         */

        // 5.1- 查询订单 根据登录用户查询优惠券订单 直接使用lambdaQuery代表的就是voucherOrderService
        Integer count = lambdaQuery().eq(VoucherOrder::getUserId, userId).eq(VoucherOrder::getVoucherId, voucherId).count();
        // 5.2- 判断是否存在
        if (count > 0) {
            // 该用户已经购买过了
            return Result.fail("该用户已经购买过本消费券了~");
        }

        // 6- 扣减库存 对于最后一张优惠券有可能出现问题
            /*
                setSql -> 实际上就是set条件 -> set stock = stock - 1
                这里eq就是where条件 -> 要求SeckillVoucher::getVoucherId字段
                                    (这里是lambda表达式来取字段名, 这样就不会导致输错字段名这种情况)的值为voucherId

                update tb_seckill_voucher set stock = stock - 1 where id = voucherId and stock = 原stock
                - 改进失败率很高的问题: 只要库存 > 0 即可成功. 但是这样会引发新的问题
                - 改进: 还是改成乐观锁, 然后加上延时和自旋, 这样解决应该会更好. 待优化.
                        这里面的stock相当于乐观锁里面的version版本号, 每次操作之前对比.
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


        // 7- 新增订单信息
        VoucherOrder voucherOrder = new VoucherOrder();
        // 设置订单id(使用全局唯一生成工具类) | 用户id | 优惠券id
        long orderId = redisIdTool.nextId(SECKILL_ORDER);

        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        // 8- 返回结果
        return Result.ok(orderId);

        /*
            存在问题: sync锁执行到上面的括号就开始释放锁, 但是Spring管理的事务还没有提交到数据库, 而锁也已经打开了,
            此时有可能会被其他线程执行产生安全问题
         */
    }
}
