package com.lee.spa;
import com.lee.spa.config.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DeploymentPolicyTest {
 private MockEnvironment staging(){var e=new MockEnvironment().withProperty("spa.deployment.mode","staging").withProperty("spring.datasource.url","jdbc:mysql://mysql:3306/spa_staging?serverTimezone=Asia/Shanghai").withProperty("spa.auth.admin-password-hash","210000:"+Base64.getEncoder().encodeToString(new byte[16])+":"+Base64.getEncoder().encodeToString(new byte[32]));e.setActiveProfiles("staging");return e;}
 @Test void normalStagingAndProductionAreAccepted(){assertEquals("spa_staging",DeploymentPolicy.validate(staging()));var e=staging();e.setActiveProfiles("production");e.setProperty("spa.deployment.mode","production");e.setProperty("spring.datasource.url","jdbc:mysql://mysql:3306/spa_production");assertEquals("spa_production",DeploymentPolicy.validate(e));}
 @Test void businessRestoreAndWrongEnvironmentNamesFailBeforeConnection(){for(String db:List.of("spa","spa_restore_20261123031256","spa_production")){var e=staging().withProperty("spring.datasource.url","jdbc:mysql://mysql:3306/"+db);assertThrows(IllegalStateException.class,()->DeploymentPolicy.validate(e));}}
 @Test void mockDevLoginAndPlaintextPasswordAreRejected(){for(String key:List.of("spa.auth.dev-login-enabled","spa.payment.mock-enabled","spa.auth.admin-password")){var e=staging().withProperty(key,"true");assertThrows(IllegalStateException.class,()->DeploymentPolicy.validate(e));}}
 @Test void acceptanceOverlayCannotTurnAProductionDatabaseIntoATestDatabase(){var e=staging().withProperty("spa.deployment.mode","acceptance").withProperty("spa.acceptance.authorized","true");e.setActiveProfiles("test");assertThrows(IllegalStateException.class,()->DeploymentPolicy.validate(e));e.setProperty("spring.datasource.url","jdbc:mysql://mysql:3306/spa_acceptance_20300101000000");assertEquals("spa_acceptance_20300101000000",DeploymentPolicy.validate(e));}
 @Test void mixedProfilesMissingModeAndMissingHashAreRejected(){var e=staging();e.setActiveProfiles("staging","test");assertThrows(IllegalStateException.class,()->DeploymentPolicy.validate(e));assertThrows(IllegalStateException.class,()->DeploymentPolicy.validate(staging().withProperty("spa.deployment.mode","")));assertThrows(IllegalStateException.class,()->DeploymentPolicy.validate(staging().withProperty("spa.auth.admin-password-hash","")));}
 @Test void actualConnectionIdentityMismatchAndDdlGrantsAreRejected(){assertThrows(IllegalStateException.class,()->DeploymentPolicy.verifyConnectedDatabase("spa_staging","spa"));assertThrows(IllegalStateException.class,()->DeploymentPolicy.verifyGrants(List.of("GRANT ALL PRIVILEGES ON spa_staging.* TO account")));DeploymentPolicy.verifyGrants(List.of("GRANT USAGE ON *.* TO account","GRANT SELECT, INSERT, UPDATE, DELETE ON spa_staging.* TO account"));}
 @Test void dataSourceGuardRejectsActualWrongIdentityBeforeAnyWrite() throws Exception {
  var ds=org.mockito.Mockito.mock(javax.sql.DataSource.class);var c=org.mockito.Mockito.mock(java.sql.Connection.class);var s=org.mockito.Mockito.mock(java.sql.Statement.class);var r=org.mockito.Mockito.mock(java.sql.ResultSet.class);
  org.mockito.Mockito.when(ds.getConnection()).thenReturn(c);org.mockito.Mockito.when(c.createStatement()).thenReturn(s);org.mockito.Mockito.when(s.executeQuery("SELECT DATABASE()")).thenReturn(r);org.mockito.Mockito.when(r.next()).thenReturn(true);org.mockito.Mockito.when(r.getString(1)).thenReturn("spa");
  assertThrows(IllegalStateException.class,()->new DeploymentDataSourceGuard(staging()).postProcessAfterInitialization(ds,"dataSource"));
  org.mockito.Mockito.verify(s,org.mockito.Mockito.never()).executeUpdate(org.mockito.ArgumentMatchers.anyString());org.mockito.Mockito.verify(s,org.mockito.Mockito.never()).executeQuery("SHOW GRANTS");
 }
 @Test void environmentProcessorRejectsInvalidConfigurationWithoutCreatingContext(){
  var env=staging().withProperty("spa.payment.mock-enabled","true");
  assertThrows(IllegalStateException.class,()->new DeploymentEnvironmentGuard().postProcessEnvironment(env,new org.springframework.boot.SpringApplication()));
 }
 @Test void roleGrantsAndCrossDatabasePrivilegesAreRejected(){
  assertThrows(IllegalStateException.class,()->DeploymentPolicy.verifyGrants("spa_staging",List.of("GRANT privileged_role TO account")));
  assertThrows(IllegalStateException.class,()->DeploymentPolicy.verifyGrants("spa_staging",List.of("GRANT SELECT ON spa.* TO account")));
 }
}
