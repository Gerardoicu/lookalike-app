package com.gerardoicu.lookalike.security;

import java.io.IOException;

import com.gerardoicu.lookalike.api.ErrorCode;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter implements Filter {

	private final SecurityProperties properties;

	public RequestSizeLimitFilter(SecurityProperties properties) {
		this.properties = properties;
	}

	@Override
	public void doFilter(jakarta.servlet.ServletRequest servletRequest, jakarta.servlet.ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;
		Long contentLength = contentLength(request);
		if (contentLength != null && contentLength > properties.requestLimit().maxKnownContentLengthBytes()) {
			response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
			response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
			response.getWriter().write("""
					{"type":"https://lookalike.local/problems/security-request-too-large","title":"Payload too large","status":413,"detail":"Request payload is too large.","code":"%s"}
					""".formatted(ErrorCode.SECURITY_REQUEST_TOO_LARGE.name()));
			return;
		}
		filterChain.doFilter(request, response);
	}

	private static Long contentLength(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.CONTENT_LENGTH);
		if (header == null || header.isBlank()) {
			return null;
		}
		try {
			return Long.parseLong(header);
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}
}
