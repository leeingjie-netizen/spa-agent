package com.lee.spa;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lee.spa.auth.*;
import com.lee.spa.config.AiRuntimeConfiguration;
import com.lee.spa.controller.AiDiagnosticsController;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class AiConfigurationTest {
 @Test void originalOpenAiPropertyResolvesInheritedDashscopeEnvironmentWithoutLeakingKey() throws Exception {
  var env=new MockEnvironment();String secret="unit-secret-do-not-log";
  env.getPropertySources().addFirst(new MapPropertySource("inherited-environment",Map.of("DASHSCOPE_API_KEY",secret)));
  env.withProperty("spring.ai.openai.api-key","${DASHSCOPE_API_KEY}");
  env.withProperty("spring.ai.openai.base-url","https://dashscope.aliyuncs.com/compatible-mode/v1");
  var config=new AiRuntimeConfiguration(env);
  assertTrue(config.configured());assertEquals("inherited-environment",config.status().get("credentialSource"));
  assertEquals("spring.ai.openai.api-key",config.status().get("effectiveProperty"));
  assertFalse(new ObjectMapper().writeValueAsString(config.status()).contains(secret));
  assertEquals(false,config.status().get("providerRequestMade"));
 }
 @Test void chatSpecificKeyOverridesCommonPlaceholderAndBlankChatFallsBack(){
  var env=new MockEnvironment().withProperty("spring.ai.openai.api-key","local-unconfigured").withProperty("spring.ai.openai.chat.api-key","unit-chat-credential");
  assertTrue(new AiRuntimeConfiguration(env).configured());
  assertEquals("spring.ai.openai.chat.api-key",new AiRuntimeConfiguration(env).status().get("effectiveProperty"));
  env.setProperty("spring.ai.openai.chat.api-key","");env.setProperty("spring.ai.openai.api-key","unit-common-credential");
  assertTrue(new AiRuntimeConfiguration(env).configured());
 }
 @Test void missingAndPlaceholderAreDistinguishedFromWrongProperty(){
  var env=new MockEnvironment().withProperty("spring.ai.dashscope.api-key","unit-wrong-property");
  assertFalse(new AiRuntimeConfiguration(env).configured());assertEquals("MISSING",new AiRuntimeConfiguration(env).status().get("keyState"));
  env.setProperty("spring.ai.openai.api-key","local-unconfigured");
  assertEquals("PLACEHOLDER",new AiRuntimeConfiguration(env).status().get("keyState"));
 }
 @Test void diagnosticsRequiresAdministrator(){
  var controller=new AiDiagnosticsController(new AiRuntimeConfiguration(new MockEnvironment()));
  try {
   assertThrows(AuthException.class,controller::status);
   AuthContext.set(new AuthPrincipal(1L,"USER",null));assertThrows(AuthException.class,controller::status);
   AuthContext.set(new AuthPrincipal(1L,"ADMIN",null));assertNotNull(controller.status().getData());
  } finally {AuthContext.clear();}
 }
}
