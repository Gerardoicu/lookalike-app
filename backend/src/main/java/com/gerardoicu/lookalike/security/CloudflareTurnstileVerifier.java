package com.gerardoicu.lookalike.security;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gerardoicu.lookalike.api.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
class CloudflareTurnstileVerifier implements TurnstileVerifier {

	private static final String SITEVERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

	private final SecurityProperties properties;
	private final RestClient restClient;

	CloudflareTurnstileVerifier(SecurityProperties properties, RestClient.Builder restClientBuilder) {
		this.properties = properties;
		this.restClient = restClientBuilder.baseUrl(SITEVERIFY_URL).build();
	}

	@Override
	public TurnstileVerification verify(String token, String remoteAddress) {
		if (properties.turnstile().secretKey().isBlank()) {
			throw new SecurityException(
					ErrorCode.SECURITY_TURNSTILE_UNAVAILABLE,
					HttpStatus.SERVICE_UNAVAILABLE,
					"Turnstile verification is not configured."
			);
		}
		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		body.add("secret", properties.turnstile().secretKey());
		body.add("response", token);
		if (remoteAddress != null && !remoteAddress.isBlank()) {
			body.add("remoteip", remoteAddress);
		}

		try {
			SiteverifyResponse response = restClient.post()
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(body)
				.retrieve()
				.body(SiteverifyResponse.class);
			if (response == null) {
				throw unavailable();
			}
			return new TurnstileVerification(response.success(), response.hostname(), response.action(), response.errorCodes());
		}
		catch (RestClientException ex) {
			throw unavailable();
		}
	}

	private static SecurityException unavailable() {
		return new SecurityException(
				ErrorCode.SECURITY_TURNSTILE_UNAVAILABLE,
				HttpStatus.SERVICE_UNAVAILABLE,
				"Turnstile verification is unavailable."
		);
	}

	private record SiteverifyResponse(
			boolean success,
			String hostname,
			String action,
			@JsonProperty("error-codes") List<String> errorCodes
	) {
	}
}
