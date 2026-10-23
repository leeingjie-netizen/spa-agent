package com.lee.spa;


import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

@SpringBootTest
class SpaAgentApplicationTests {

    @Autowired
    private RedissonClient redissonClient;
    @Test
    void testRedisConnection() {

        RBucket<String> bucket =
                redissonClient.getBucket("spa:test");

        bucket.set("hello redis");

        System.out.println(bucket.get());
    }


    @Test
    void testTwoThreadsCompeteForLock() throws InterruptedException {

        Runnable task = () -> {

            String threadName = Thread.currentThread().getName();

            RLock lock = redissonClient.getLock("spa:test:lock");

            boolean locked = false;

            try {
                System.out.println(threadName + "：开始尝试获取锁");

                locked = lock.tryLock(
                        1,
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
//@SpringBootTest
//class SpaAgentApplicationTests {
//
//    @Test
//    void contextLoads() {
//    }
//
//}

