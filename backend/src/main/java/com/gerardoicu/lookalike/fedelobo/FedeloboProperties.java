package com.gerardoicu.lookalike.fedelobo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lookalike.fedelobo")
record FedeloboProperties(String profilePath) {

	FedeloboProperties {
		profilePath = profilePath == null ? "" : profilePath;
	}
}
