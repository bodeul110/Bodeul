package com.bodeul.core.session;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CompanionSessionProperties.class)
class CompanionSessionConfiguration {
}
