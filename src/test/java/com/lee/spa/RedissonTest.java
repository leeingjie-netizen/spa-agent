package com.lee.spa;
import org.junit.jupiter.api.*;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="SPA_LEGACY_INTEGRATION_TESTS",matches="true")
class RedissonTest {
 @Autowired RedissonClient redis; @Autowired javax.sql.DataSource dataSource;
 @BeforeEach void isolated() throws Exception {AcceptanceIsolation.verify(dataSource);RedisTestSupport.real(redis);}
 @Test void testRedisConnection() throws Exception {RedisTestSupport.roundTrip(redis);}
 @Test void testTwoThreadsCompeteForLock() throws Exception {RedisTestSupport.contention(redis);}
 @Test void testWaitForLock() throws Exception {RedisTestSupport.waitThenAcquire(redis);}
}
