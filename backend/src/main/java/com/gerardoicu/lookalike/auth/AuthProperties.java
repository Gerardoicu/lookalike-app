package com.gerardoicu.lookalike.auth;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

	private String jwtSecret = "";

	private Duration accessTokenTtl = Duration.ofMinutes(15);

	private Duration refreshFamilyTtl = Duration.ofDays(7);

	private Duration clockSkew = Duration.ofSeconds(60);

	private String jwtIssuer = "lookalike-backend";

	private String jwtAudience = "lookalike-api";

	private final RefreshCookie refreshCookie = new RefreshCookie();

	private final XsrfCookie xsrfCookie = new XsrfCookie();

	private final LocalBootstrap localBootstrap = new LocalBootstrap();

	public String jwtSecret() {
		return jwtSecret;
	}

	public String getJwtSecret() {
		return jwtSecret;
	}

	public void setJwtSecret(String jwtSecret) {
		this.jwtSecret = jwtSecret;
	}

	public Duration accessTokenTtl() {
		return accessTokenTtl;
	}

	public Duration getAccessTokenTtl() {
		return accessTokenTtl;
	}

	public void setAccessTokenTtl(Duration accessTokenTtl) {
		this.accessTokenTtl = accessTokenTtl;
	}

	public Duration refreshFamilyTtl() {
		return refreshFamilyTtl;
	}

	public Duration getRefreshFamilyTtl() {
		return refreshFamilyTtl;
	}

	public void setRefreshFamilyTtl(Duration refreshFamilyTtl) {
		this.refreshFamilyTtl = refreshFamilyTtl;
	}

	public Duration clockSkew() {
		return clockSkew;
	}

	public Duration getClockSkew() {
		return clockSkew;
	}

	public void setClockSkew(Duration clockSkew) {
		this.clockSkew = clockSkew;
	}

	public String jwtIssuer() {
		return jwtIssuer;
	}

	public String getJwtIssuer() {
		return jwtIssuer;
	}

	public void setJwtIssuer(String jwtIssuer) {
		this.jwtIssuer = jwtIssuer;
	}

	public String jwtAudience() {
		return jwtAudience;
	}

	public String getJwtAudience() {
		return jwtAudience;
	}

	public void setJwtAudience(String jwtAudience) {
		this.jwtAudience = jwtAudience;
	}

	public RefreshCookie refreshCookie() {
		return refreshCookie;
	}

	public RefreshCookie getRefreshCookie() {
		return refreshCookie;
	}

	public XsrfCookie xsrfCookie() {
		return xsrfCookie;
	}

	public XsrfCookie getXsrfCookie() {
		return xsrfCookie;
	}

	public LocalBootstrap localBootstrap() {
		return localBootstrap;
	}

	public LocalBootstrap getLocalBootstrap() {
		return localBootstrap;
	}

	public static class LocalBootstrap {

		private boolean enabled;

		private String email = "";

		private String password = "";

		private Set<ApplicationRole> roles = new LinkedHashSet<>();

		public boolean enabled() {
			return enabled;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String email() {
			return email;
		}

		public String getEmail() {
			return email;
		}

		public void setEmail(String email) {
			this.email = email;
		}

		public String password() {
			return password;
		}

		public String getPassword() {
			return password;
		}

		public void setPassword(String password) {
			this.password = password;
		}

		public Set<ApplicationRole> roles() {
			return Set.copyOf(roles);
		}

		public Set<ApplicationRole> getRoles() {
			return roles;
		}

		public void setRoles(Set<ApplicationRole> roles) {
			this.roles = new LinkedHashSet<>(roles);
		}
	}

	public static class RefreshCookie {

		private String name = "__Host-refresh_token";

		private boolean secure = true;

		public String name() {
			return name;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public boolean secure() {
			return secure;
		}

		public boolean isSecure() {
			return secure;
		}

		public void setSecure(boolean secure) {
			this.secure = secure;
		}
	}

	public static class XsrfCookie {

		private boolean secure = true;

		public boolean secure() {
			return secure;
		}

		public boolean isSecure() {
			return secure;
		}

		public void setSecure(boolean secure) {
			this.secure = secure;
		}
	}
}
