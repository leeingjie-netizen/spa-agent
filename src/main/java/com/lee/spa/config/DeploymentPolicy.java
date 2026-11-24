package com.lee.spa.config;
import org.springframework.core.env.Environment;
import java.util.*;
import java.util.regex.Pattern;
public final class DeploymentPolicy {
 private DeploymentPolicy(){}
 public static String validate(Environment env) {
  Set<String> profiles=new HashSet<>(Arrays.asList(env.getActiveProfiles()));
  String mode=env.getProperty("spa.deployment.mode","");
  boolean guarded=profiles.stream().anyMatch(p->Set.of("staging","prod","production").contains(p));
  if(mode.isBlank()&&!guarded)return null;
  String expected;
  if(mode.equals("acceptance")) {
   if(!profiles.equals(Set.of("test"))||!env.getProperty("spa.acceptance.authorized",Boolean.class,false))throw rejected();
   expected=database(env);
   if(!expected.matches("spa_acceptance_[0-9]{14}"))throw rejected();
  } else {
   boolean staging=mode.equals("staging"),production=mode.equals("production");
   if(!staging&&!production)throw rejected();
   if(staging&&!profiles.equals(Set.of("staging")))throw rejected();
   if(production&&!(profiles.equals(Set.of("prod"))||profiles.equals(Set.of("production"))))throw rejected();
   if(env.getProperty("spa.auth.dev-login-enabled",Boolean.class,false)||env.getProperty("spa.payment.mock-enabled",Boolean.class,false))throw rejected();
   if(!env.getProperty("spa.auth.admin-password","").isBlank())throw rejected();
   expected=staging?"spa_staging":"spa_production";
   if(!expected.equals(database(env)))throw rejected();
   String hash=env.getProperty("spa.auth.admin-password-hash","");
   try {String[] parts=hash.split(":");int n=Integer.parseInt(parts[0]);if(parts.length!=3||n<100000||n>1000000||Base64.getDecoder().decode(parts[1]).length!=16||Base64.getDecoder().decode(parts[2]).length!=32)throw rejected();}catch(RuntimeException invalid){throw rejected();}
  }
  return expected;
 }
 private static String database(Environment env) {
  var m=Pattern.compile("^jdbc:mysql://[^/]+/([A-Za-z0-9_]+)(?:\\?.*)?$").matcher(env.getProperty("spring.datasource.url",""));
  if(!m.matches())throw rejected();return m.group(1);
 }
 public static void verifyConnectedDatabase(String expected,String actual){if(!Objects.equals(expected,actual))throw rejected();}
 public static void verifyGrants(List<String> grants){verifyGrants("spa_staging",grants);}
 public static void verifyGrants(String database,List<String> grants){
  if(grants.isEmpty())throw rejected();
  for(String grant:grants){
   var m=Pattern.compile("^GRANT (.+?) ON (.+?) TO .+$",Pattern.CASE_INSENSITIVE).matcher(grant);
   if(!m.matches()||grant.toUpperCase(Locale.ROOT).contains("WITH GRANT OPTION"))throw rejected();
   Set<String> privileges=new HashSet<>();for(String p:m.group(1).split(","))privileges.add(p.trim().toUpperCase(Locale.ROOT));
   String scope=m.group(2).replace("`","");
   if(privileges.equals(Set.of("USAGE"))&&scope.equals("*.*"))continue;
   if(!Set.of("SELECT","INSERT","UPDATE","DELETE").containsAll(privileges)||!scope.equals(database+".*"))throw rejected();
  }
 }
 private static IllegalStateException rejected(){return new IllegalStateException("Deployment safety policy rejected configuration or database identity");}
}
