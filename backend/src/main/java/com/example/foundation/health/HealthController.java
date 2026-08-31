package com.example.foundation.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

	@GetMapping("/api/v1/health")
	HealthResponse health() {
		return new HealthResponse(true);
	}

}
