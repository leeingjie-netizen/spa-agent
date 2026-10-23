package com.lee.spa;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;
@SpringBootTest
public class RedissonTest {

    @Autowired
    private RedissonClient redissonClient;
    @Test
    void testRedisConnection() {

        RBucket<String> bucket =
                redissonClient.getBucket("spa:test");

        bucket.set("hello redis");

        System.out.println(bucket.get());
    }

//    只是先规定：
//    每一个用户来了
//      ↓
//    获取锁
//      ↓
//    模拟业务
//      ↓
//    释放锁
    @Test
    void testTwoThreadsCompeteForLock() throws InterruptedException {
        // 定义“一名用户进来以后要执行什么事情”。
        Runnable task = () -> {
            // 获取当前正在执行的线程的名称    可能得到“用户A”或者“用户B”   纯粹为了让控制台输出容易观察。
            String threadName = Thread.currentThread().getName();

            // getLock()≠获得锁；它只是表示：我要操作 Redis 中名字为 spa:test:lock 的这把锁。
//            由于 Key 完全一样：spa:test:lock；所以两个人竞争的是同一个资源。
            //            这就是以后我们设计：spa:appointment:lock:2:2026-09-16  的根本原因。
            RLock lock = redissonClient.getLock("spa:test:lock");

            boolean locked = false;  // 获取锁这件事只有成功或者失败两种情况

            try {
                System.out.println(threadName + "：开始尝试获取锁");

                locked = lock.tryLock(
                        1,  // waitTime = 1 秒，等待锁的最大时间。如果别人已经拿着锁，我最多愿意等 1 秒。（愿意等多久）
                        10,  // leaseTime = 10 秒，锁的自动释放时间。如果业务执行时间超过 10 秒，锁会自动释放。（拿到后最多占多久）
                        TimeUnit.SECONDS   //表示：前面的 1 和 10 的单位都是秒。不然 Java 不知道是什么单位。（时间单位）
                );

                if (!locked) {
                    System.out.println(threadName + "：获取锁失败");
                    return;
                }

                System.out.println(threadName + "：成功获取锁");
                /**
                 *    不是说我们的 SPA 预约以后要故意卡 3 秒。
                 *                 这里纯粹是为了实验。
                 *                 相当于模拟：
                 *                 查询 Slot
                 *                 创建 Appointment
                 *                 创建 AppointmentSlot
                 *                 提交事务
                 *                 这些业务正在执行。
                 *                 我们故意让 A：
                 *                 占着锁 3 秒
                 *                 这样 B 才有时间过来抢。
                 */
                Thread.sleep(3000);

                System.out.println(threadName + "：业务执行完成");

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();

            } finally {   //finally表达的是：不管前面正常还是异常，都执行释放资源的逻辑。


                if (locked && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                    System.out.println(threadName + "：释放锁");
                }
            }
        };

        Thread userA = new Thread(task, "用户A");
        Thread userB = new Thread(task, "用户B");

        userA.start();

        Thread.sleep(100);

        userB.start();

        userA.join();
        userB.join();
    }



    @Test
    void testWaitForLock() throws InterruptedException {

        Runnable task = () -> {

            String threadName = Thread.currentThread().getName();

            RLock lock = redissonClient.getLock("spa:test:wait-lock");

            boolean locked = false;

            try {

                System.out.println(threadName + "：开始尝试获取锁");

                locked = lock.tryLock(
                        5,
                        10,
                        TimeUnit.SECONDS
                );

                if (!locked) {
                    System.out.println(threadName + "：获取锁失败");
                    return;
                }

                System.out.println(threadName + "：成功获取锁");

                Thread.sleep(3000);

                System.out.println(threadName + "：业务执行完成");

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();

            } finally {

                if (locked && lock.isHeldByCurrentThread()) {

                    lock.unlock();

                    System.out.println(threadName + "：释放锁");
                }
            }
        };

        Thread userA = new Thread(task, "用户A");
        Thread userB = new Thread(task, "用户B");

        userA.start();

        Thread.sleep(100);

        userB.start();

        userA.join();
        userB.join();
    }
}
