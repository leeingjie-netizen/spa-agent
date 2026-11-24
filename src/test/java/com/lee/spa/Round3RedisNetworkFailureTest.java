package com.lee.spa;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.config.Config;
import org.redisson.api.RedissonClient;
import java.net.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
/** Real Redisson clients and owned TCP endpoints; never touches external Redis. */
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="SPA_REAL_CONCURRENCY_TESTS",matches="true")
class Round3RedisNetworkFailureTest {
 Config config(int port,String password){
  Config c=new Config();c.setNettyThreads(2);c.setThreads(2);
  c.useSingleServer().setAddress("redis://127.0.0.1:"+port).setPassword(password).setConnectTimeout(300).setTimeout(300).setRetryAttempts(0).setConnectionMinimumIdleSize(1).setConnectionPoolSize(2);
  return c;
 }
 void recovered(){RedissonClient client=Redisson.create(config(16379,System.getenv("SPA_ACCEPTANCE_REDIS_PASSWORD")));try{RedisTestSupport.roundTrip(client);}finally{client.shutdown();}}
 @Test void badAuthenticationFailsThenValidAuthenticationRecovers(){
  assertThrows(RuntimeException.class,()->Redisson.create(config(16379,"deliberately-invalid-synthetic-password")));recovered();
 }
 @Test void ownedClosedEndpointFailsWithoutHangingThenRealRedisRecovers() throws Exception {
  int port;try(var socket=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){port=socket.getLocalPort();}
  long start=System.nanoTime();assertThrows(RuntimeException.class,()->Redisson.create(config(port,null)));assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-start)<10);recovered();
 }
 @Test void acceptedButSilentTcpConnectionTimesOutThenRealRedisRecovers() throws Exception {
  try(var server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
   server.setSoTimeout(5000);var pool=Executors.newSingleThreadExecutor();
   var peer=pool.submit(()->{try(var socket=server.accept()){Thread.sleep(2000);}return null;});
   try{long start=System.nanoTime();assertThrows(RuntimeException.class,()->Redisson.create(config(server.getLocalPort(),null)));assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-start)<10);}
   finally{peer.get(6,TimeUnit.SECONDS);pool.shutdownNow();assertTrue(pool.awaitTermination(3,TimeUnit.SECONDS));}
  }recovered();
 }
}
