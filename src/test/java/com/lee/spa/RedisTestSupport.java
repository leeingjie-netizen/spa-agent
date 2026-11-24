package com.lee.spa;
import org.redisson.api.*;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Assertions against an owned real Redis only. No FLUSH commands or shared keys. */
final class RedisTestSupport {
 static void real(RedissonClient client){assertFalse(org.mockito.Mockito.mockingDetails(client).isMock(),"Real Redis required");}
 static void roundTrip(RedissonClient client){
  real(client);RBucket<String> bucket=client.getBucket("spa:round2:bucket:"+UUID.randomUUID());
  try{bucket.set("synthetic-round2",java.time.Duration.ofSeconds(30));assertEquals("synthetic-round2",bucket.get());assertTrue(bucket.remainTimeToLive()>0);}
  finally{bucket.delete();}
 }
 static void contention(RedissonClient client) throws Exception {
  real(client);RLock lock=client.getLock("spa:round2:contention:"+UUID.randomUUID());
  var pool=Executors.newSingleThreadExecutor();boolean acquired=lock.tryLock(1,10,TimeUnit.SECONDS);assertTrue(acquired);
  try{assertFalse(pool.submit(()->lock.tryLock(200,1000,TimeUnit.MILLISECONDS)).get(3,TimeUnit.SECONDS));assertTrue(lock.isHeldByCurrentThread());}
  finally{if(acquired&&lock.isHeldByCurrentThread())lock.unlock();pool.shutdownNow();assertTrue(pool.awaitTermination(3,TimeUnit.SECONDS));}
  assertFalse(lock.isLocked());
 }
 static void waitThenAcquire(RedissonClient client) throws Exception {
  real(client);RLock lock=client.getLock("spa:round2:wait:"+UUID.randomUUID());var pool=Executors.newSingleThreadExecutor();var started=new CountDownLatch(1);
  assertTrue(lock.tryLock(1,10,TimeUnit.SECONDS));
  try{var future=pool.submit(()->{started.countDown();boolean got=lock.tryLock(3,10,TimeUnit.SECONDS);try{return got&&lock.isHeldByCurrentThread();}finally{if(got&&lock.isHeldByCurrentThread())lock.unlock();}});
   assertTrue(started.await(1,TimeUnit.SECONDS));Thread.sleep(100);assertFalse(future.isDone());lock.unlock();assertTrue(future.get(4,TimeUnit.SECONDS));assertFalse(lock.isLocked());
  }finally{if(lock.isHeldByCurrentThread())lock.unlock();pool.shutdownNow();assertTrue(pool.awaitTermination(3,TimeUnit.SECONDS));}
 }
}
