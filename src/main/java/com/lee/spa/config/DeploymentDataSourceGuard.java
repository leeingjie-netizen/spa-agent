package com.lee.spa.config;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.util.*;
@Component
public final class DeploymentDataSourceGuard implements BeanPostProcessor {
 private final Environment env;
 public DeploymentDataSourceGuard(Environment env){this.env=env;}
 @Override public Object postProcessAfterInitialization(Object bean,String name){
  if(!(bean instanceof DataSource source))return bean;
  String expected=DeploymentPolicy.validate(env);if(expected==null)return bean;
  try(var c=source.getConnection();var s=c.createStatement()){
   try(var r=s.executeQuery("SELECT DATABASE()")){if(!r.next())throw new IllegalStateException("Database identity unavailable");DeploymentPolicy.verifyConnectedDatabase(expected,r.getString(1));}
   List<String> grants=new ArrayList<>();try(var r=s.executeQuery("SHOW GRANTS")){while(r.next())grants.add(r.getString(1));}DeploymentPolicy.verifyGrants(expected,grants);
  }catch(java.sql.SQLException failure){throw new IllegalStateException("Deployment database identity/privilege verification failed");}
  return bean;
 }
}
