package com.lee.spa;
import com.lee.spa.ai.tool.*;
import com.lee.spa.auth.*;
import com.lee.spa.common.exception.BusinessException;
import com.lee.spa.service.impl.AiChatServiceImpl;
import com.lee.spa.config.AiRuntimeConfiguration;
import org.springframework.mock.env.MockEnvironment;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiUnavailableTest {
 @Test void configuredProviderStillReceivesChatThroughMockedClient(){
  var builder=mock(ChatClient.Builder.class,RETURNS_SELF);
  var client=mock(ChatClient.class);when(builder.build()).thenReturn(client);
  var spec=mock(ChatClient.ChatClientRequestSpec.class,RETURNS_SELF);
  var response=mock(ChatClient.CallResponseSpec.class);
  when(client.prompt()).thenReturn(spec);when(spec.call()).thenReturn(response);when(response.content()).thenReturn("真实工具结果");
  var service=new AiChatServiceImpl(builder,mock(SpaServiceTool.class),mock(AppointmentTool.class),mock(StaffTool.class),mock(ChatMemory.class),new AiRuntimeConfiguration(new MockEnvironment().withProperty("spring.ai.openai.api-key","unit-provider-placeholder")));
  AuthContext.set(new AuthPrincipal(12L,"USER",null));
  try {assertEquals("真实工具结果",service.chat("conversation","request","查询服务"));verify(spec).user("查询服务");}
  finally {AuthContext.clear();}
 }
 @Test void unconfiguredAiRejectsLocallyWithoutCallingProviderOrTools(){
  var builder=mock(ChatClient.Builder.class,RETURNS_SELF);
  var client=mock(ChatClient.class);when(builder.build()).thenReturn(client);
  var services=mock(SpaServiceTool.class);var appointments=mock(AppointmentTool.class);var staff=mock(StaffTool.class);
  var service=new AiChatServiceImpl(builder,services,appointments,staff,mock(ChatMemory.class),new AiRuntimeConfiguration(new MockEnvironment().withProperty("spring.ai.openai.api-key","local-unconfigured")));
  AuthContext.set(new AuthPrincipal(12L,"USER",null));
  try {
   assertTrue(assertThrows(BusinessException.class,()->service.chat("conversation","request","帮我查看服务")).getMessage().contains("尚未配置"));
   verifyNoInteractions(client,services,appointments,staff);
  } finally {AuthContext.clear();}
 }
}
