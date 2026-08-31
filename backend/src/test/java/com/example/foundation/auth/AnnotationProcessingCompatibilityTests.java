package com.example.foundation.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.metamodel.SingularAttribute;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.junit.jupiter.api.Test;

class AnnotationProcessingCompatibilityTests {

	@Test
	void generatedMetamodelExposesUserAccountEmailAttribute() {
		SingularAttribute<UserAccount, String> emailAttribute = UserAccount_.email;

		assertThat(UserAccount_.EMAIL).isEqualTo("email");
		if (emailAttribute != null) {
			assertThat(emailAttribute.getJavaType()).isEqualTo(String.class);
		}
	}

	@Test
	void lombokGeneratesStableAccessorForTestFixture() {
		LombokFixture fixture = new LombokFixture("generated");

		assertThat(fixture.getValue()).isEqualTo("generated");
	}

	@Getter
	@RequiredArgsConstructor
	private static final class LombokFixture {

		private final String value;
	}
}
