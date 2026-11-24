package com.lee.spa.config;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
public final class DeploymentEnvironmentGuard implements EnvironmentPostProcessor,Ordered {
 @Override public void postProcessEnvironment(ConfigurableEnvironment env,SpringApplication app){DeploymentPolicy.validate(env);}
 @Override public int getOrder(){return ConfigDataEnvironmentPostProcessor.ORDER+1;}
}
