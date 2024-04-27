package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdTool;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.utils.RedisConstants.SECKILL_ORDER;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service("voucherOrderService")
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIdTool redisIdTool;

    @Resource
    private RedissonClient redissonClient;

    // redis脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        // ClassPathResource类用来加载resources目录下的指定文件
        SECKILL_SCRIPT.setLocation(new ClassPathResource(SystemConstants.LUA_SCRIPT_SECKILL_FILENAME));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 使用Blockingqueue创建一个阻塞队列, 如果队列中没有元素, 监听该阻塞队列的线程将会被阻塞, 有元素则会被唤醒
    private BlockingQueue<VoucherOrder> voucherOrderQueue = new ArrayBlockingQueue<>(1024 * 1024);
    // 创建一个线程池用于监听这个队列
    private static final ExecutorService SECKILL_ORDER_HANDLER = Executors.newSingleThreadExecutor();

    // 当服务启动之后就应该监听阻塞队列, 初始化VoucherOrderServiceImpl之后就应该执行任务, 使用Spring提供的@PostConstruct注解
    @PostConstruct
    public void init() {
        SECKILL_ORDER_HANDLER.submit(new VoucherOrderHandler());
    }


    // 定义内部类用来处理阻塞队列
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                // 1. 获取队列中的订单信息
                VoucherOrder voucherOrder = null;
                try {
                    voucherOrder = voucherOrderQueue.take();
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
                // 2. 创建订单
                handlerVoucherOrder(voucherOrder);
            }
        }
    }

    // 创建订单
    private void handlerVoucherOrder(VoucherOrder voucherOrder) {
        // 获取用户的时候不能使用UserHolder, 因为这个任务是通过开启新的线程执行的, 没有用户信息, 需要从voucherOrder中取
        // 1. 获取用户
        Long userId = voucherOrder.getUserId();
        // 2. 创建锁, 实际上这里可以不创建, 兜底策略
        RLock lock = redissonClient.getLock("lok:order:" + userId);
        // 3. 获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("不允许重复下单");
            return;
        }
        try {
            // 使用代理对象创建订单, 防止事务失效, 代理对象在主线程中获取
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    // 代理对象
    private IVoucherOrderService proxy;

    // 直接在redis中判断用户是否满足购买资格
    @Override
    public Result secKillVoucher(Long voucherId) {
        // 获取用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        // 1. 执行lua脚本, 参数分别是, redis脚本内容, 脚本需要使用的keys, 脚本需要的args, 并且args只能是string类型
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        // 2. 判断结果是否为0
        int r = result.intValue();
        if (r != 0) {
            // 2.1. 不为0, 表示没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不允许重复下单");
        }
        // 2.2. 为0, 表示有购买资格, 把下单信息保存到阻塞队列
        long orderId = redisIdTool.nextId(SECKILL_ORDER);

        // 2.3. 创建voucherOrder
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        // 保存到阻塞队列
        voucherOrderQueue.add(voucherOrder);

        // 获取跟事务有关的代理对象 -> 不能在handlerVoucherOrder方法中获取, 因为子线程AopContext获取不到代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 3. 返回订单id
        return Result.ok(orderId);
    }

    /**
     * 秒杀优惠券
     *
     * @param voucherId 优惠券id
     * @return 结果
     */
    // @Override
    public Result secKillVoucher_(Long voucherId) {
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
        // Long userId = UserHolder.getUser().getId();

        // 使用分布式锁解决集群部署问题 -> 手写的分布式锁
        // SimpleRedisLock redisLock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
        // 使用Redisson提供的工具类
        // RLock redisLock = redissonClient.getLock("lock:order:" + userId);

        // 调试使用, 时间设置的长一些 -> 更改成使用Redisson的锁工具类, 不设置时间
        // boolean success = redisLock.tryLock();
        // 反向判断 不要把逻辑放进去if
        // if (!success) {
        //     return Result.fail("请勿连续点击...");
        // }

        // try {
            // 获取跟事务有关的代理对象
            // IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            // 事务提交之后才会释放锁
        //     return proxy.createVoucherOrder(voucherId);
        // } catch (IllegalStateException e) {
        //     throw new RuntimeException(e);
        // } finally {
        //     redisLock.unlock();
        // }
        IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        return proxy.createVoucherOrder(voucherId);

    }

    /**
     * 创建优惠券订单
     *
     * @param voucherId 优惠券id
     * @return {@link Result}
     */
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        /*
            synchronized锁改成代码块锁, 如果是public synchronized Result createVoucherOrder(id)加锁将是针对service对象,
            是一个方法锁, 以方法为单位, 凡是执行这个方法的线程都会互斥, 单线程执行.
            锁的粒度太粗了, 会影响到每个线程的执行, 因此控制锁的粗粒度是一件很重要的事情.
         */

        // 5- 一人一单功能(存在并发安全性问题)
        Long userId = UserHolder.getUser().getId();

        // 创建锁对象
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 尝试获取锁
        boolean isLock = redisLock.tryLock();
        // 判断
        if (!isLock) {
            // 获取失败, 直接返回失败或者重试
            return Result.fail("抢购人数较多，请重试~");
        }


        try {
            /*
                synchronized锁
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
        } finally {
            // 释放锁
            redisLock.unlock();
        }
    }

    /**
     * 创建订单优惠券
     *
     * @param voucherOrder 优惠券
     */
    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 1- 查询订单 根据登录用户查询优惠券订单 直接使用lambdaQuery代表的就是voucherOrderService
        Long voucherId = voucherOrder.getVoucherId();
        Integer count = getBaseMapper().selectCount(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherId));
        // 2- 判断是否存在
        if (count > 0) {
            // 该用户已经购买过了
            log.info("该用户已经购买过本消费券了~");
            // 注意要返回, 否则下面的save方法会调用
            return;
        }
        // 有抢购资格, 但是扣减库存失败, 这种情况概率比较小
        boolean success = seckillVoucherService.lambdaUpdate()
                .setSql("stock = stock - 1")
                .eq(SeckillVoucher::getVoucherId, voucherId)
                .gt(SeckillVoucher::getStock, 0).update();
        if (!success) {
            log.error("异常处理, 用户具有购买资格, 但是扣减库存失败");
            return;
        }
        save(voucherOrder);
    }
}
