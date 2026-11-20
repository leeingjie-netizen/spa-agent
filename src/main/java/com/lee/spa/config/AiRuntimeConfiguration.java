package com.lee.spa.config;

import org.springframework.core.env.*;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.*;

/** Reports configuration presence only. Never expose credentials, their length or fingerprint. */
@Component
public class AiRuntimeConfiguration {
 private final Environment environment;
 public AiRuntimeConfiguration(Environment environment){this.environment=environment;}
 private String keyProperty(){return present(environment.getProperty("spring.ai.openai.chat.api-key")) ? "spring.ai.openai.chat.api-key" : "spring.ai.openai.api-key";}
 private static boolean present(String value){return value!=null&&!value.isBlank();}
 public boolean configured(){String key=environment.getProperty(keyProperty());return present(key)&&!"local-unconfigured".equals(key);}
 private String source(String property){
  if(environment instanceof ConfigurableEnvironment configurable) for(PropertySource<?> source:configurable.getPropertySources()) {
   if(!"configurationProperties".equals(source.getName())&&source.containsProperty(property)) return source.getName();
  }
  return "not-defined";
 }
 public Map<String,Object> status(){
  String property=keyProperty(),key=environment.getProperty(property),origin=source(property);
  if(environment instanceof ConfigurableEnvironment configurable) for(PropertySource<?> source:configurable.getPropertySources()) {
   if(source.getName().equals(origin)&&String.valueOf(source.getProperty(property)).contains("${DASHSCOPE_API_KEY")) origin=source("DASHSCOPE_API_KEY");
  }
  String baseUrl=environment.getProperty("spring.ai.openai.chat.base-url",environment.getProperty("spring.ai.openai.base-url","https://api.openai.com"));
  String host;
  try{host=Objects.toString(URI.create(baseUrl).getHost(),"invalid-url");}catch(IllegalArgumentException invalid){host="invalid-url";}
  return Map.of("configured",configured(),"keyState",!present(key)?"MISSING":configured()?"PRESENT":"PLACEHOLDER",
   "effectiveProperty",property,"credentialSource",origin,"profiles",List.of(environment.getActiveProfiles()),
   "providerHost",host,"model",environment.getProperty("spring.ai.openai.chat.options.model","not-explicitly-set"),
   "providerRequestMade",false);
 }
}
