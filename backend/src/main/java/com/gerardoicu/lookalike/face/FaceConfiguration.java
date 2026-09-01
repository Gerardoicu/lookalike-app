package com.gerardoicu.lookalike.face;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FaceAnalysisProperties.class)
class FaceConfiguration {
}
