package com.lee.spa.auth;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CredentialLoggingTest {
 @Test void debugDescriptionsRedactSecretsWithoutBreakingAuthenticationJson() throws Exception {
  String secret="never-log-this-secret";
  var credentials=new AuthController.Credentials("admin",secret);
  var login=new AuthService.Login(secret,null,null,"ADMIN");
  assertFalse(credentials.toString().contains(secret));
  assertFalse(new AuthController.Wechat(secret).toString().contains(secret));
  assertFalse(new AdminAccountController.CreateStaff("staff",secret,1L).toString().contains(secret));
  assertFalse(login.toString().contains(secret));
  var mapper=new ObjectMapper();
  assertEquals(secret,mapper.readTree(mapper.writeValueAsString(login)).get("token").asText());
  assertEquals(secret,mapper.readValue(mapper.writeValueAsString(credentials),AuthController.Credentials.class).password());
 }
}
