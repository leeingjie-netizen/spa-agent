package com.lee.spa;

import org.springframework.test.context.*;
import org.springframework.core.env.MapPropertySource;
import java.sql.DriverManager;
import java.util.*;

/** Runs before any application beans, jobs, or fixtures can write. Fail closed. */
public final class AcceptanceIsolation implements ContextCustomizerFactory {
    public static void verify(javax.sql.DataSource source) throws Exception {
        try (var c = source.getConnection()) { verify(c); }
    }
    public static void verify(java.sql.Connection c) throws Exception {
        String expected = System.getenv("SPA_ACCEPTANCE_DB");
        if (expected == null || !expected.matches("spa_acceptance_[0-9]{14}"))
            throw new IllegalStateException("Explicit isolated acceptance database required");
        try (var s = c.createStatement(); var r = s.executeQuery("SELECT DATABASE(),@@port")) {
            if (!r.next() || !expected.equals(r.getString(1)) || r.getInt(2) != 3306)
                throw new IllegalStateException("Database identity mismatch; writes forbidden");
        }
    }
    @Override public ContextCustomizer createContextCustomizer(Class<?> type, List<ContextConfigurationAttributes> attributes) {
        return new Guard(type.getName().equals("com.lee.spa.commerce.Round2TimeoutSchedulerTest"));
    }
    private record Guard(boolean schedulerTest) implements ContextCustomizer {
        @Override public void customizeContext(org.springframework.context.ConfigurableApplicationContext ctx, MergedContextConfiguration config) {
            String db = System.getenv("SPA_ACCEPTANCE_DB");
            if (db == null || !db.matches("spa_acceptance_[0-9]{14}"))
                throw new IllegalStateException("Refusing Spring integration tests without isolated database");
            String user = System.getenv("SPA_ACCEPTANCE_DB_USER"), password = System.getenv("SPA_ACCEPTANCE_DB_PASSWORD");
            String url = "jdbc:mysql://127.0.0.1:3306/"+db+"?serverTimezone=Asia/Shanghai";
            try (var c = DriverManager.getConnection(url,user,password)) { verify(c); }
            catch (Exception e) { throw new IllegalStateException("Acceptance identity check failed; no context started"); }
            Map<String,Object> values = new HashMap<>();
            values.put("spring.datasource.url",url); values.put("spring.datasource.username",user); values.put("spring.datasource.password",password);
            values.put("spa.orders.timeout-enabled","false"); values.put("spa.scheduling.enabled","false");
            values.put("server.address","127.0.0.1");
            if(schedulerTest) {
                values.put("spa.orders.timeout-enabled","true");
                values.put("spa.orders.timeout-delay-ms","100");
                values.put("spa.orders.timeout-initial-delay-ms","100");
            }
            values.put("spring.ai.openai.api-key","local-unconfigured");
            values.put("spring.ai.openai.base-url","http://127.0.0.1:1");
            values.put("logging.level.org.springframework.web","INFO");
            values.put("logging.level.root","INFO");
            values.put("logging.level.org.springframework","INFO");
            values.put("logging.level.org.springframework.jdbc.support.JdbcTransactionManager","INFO");
            values.put("mybatis-plus.configuration.log-impl","org.apache.ibatis.logging.nologging.NoLoggingImpl");
            // Dedicated endpoint required. Never inherit the development Redis address.
            values.put("spring.data.redis.host","127.0.0.1"); values.put("spring.data.redis.port","16379");
            values.put("spring.data.redis.database","0"); values.put("spring.data.redis.password",System.getenv().getOrDefault("SPA_ACCEPTANCE_REDIS_PASSWORD",""));
            if ("true".equals(System.getenv("SPA_MYSQL_ONLY_TESTS"))) {
                values.put("spring.autoconfigure.exclude","org.redisson.spring.starter.RedissonAutoConfigurationV2");
                ctx.getBeanFactory().registerSingleton("acceptanceUnavailableRedis",org.mockito.Mockito.mock(org.redisson.api.RedissonClient.class));
            }
            ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("acceptanceIsolation",values));
        }
    }
}
