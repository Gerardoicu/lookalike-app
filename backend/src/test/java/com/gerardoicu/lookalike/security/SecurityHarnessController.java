package com.gerardoicu.lookalike.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SecurityHarnessController {

	static int inferenceCalls;

	private final AnonymousAnalysisSecurityGate securityGate;

	SecurityHarnessController(AnonymousAnalysisSecurityGate securityGate) {
		this.securityGate = securityGate;
	}

	@PostMapping("/api/v1/security-test/analysis")
	ResponseEntity<String> analyze(
			@RequestHeader(name = "X-Turnstile-Token", required = false) String turnstileToken,
			@RequestParam(defaultValue = "true") boolean complete,
			HttpServletRequest request,
			HttpServletResponse response
	) {
		AnonymousAnalysisAuthorization authorization = securityGate.authorizePreAnalysis(request, response, turnstileToken);
		inferenceCalls++;
		if (complete) {
			securityGate.recordSuccessfulAnalysis(response, authorization);
		}
		return ResponseEntity.ok("authorized");
	}
}
