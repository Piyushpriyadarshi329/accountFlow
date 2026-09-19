package com.accountflow.config;

import com.accountflow.common.api.ErrorResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	static final String BEARER_SCHEME = "bearerAuth";

	@Bean
	OpenAPI accountFlowOpenApi() {
		Components components = new Components().addSecuritySchemes(BEARER_SCHEME,
				new SecurityScheme().name(BEARER_SCHEME)
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")
					.description("""
							Paste the accessToken returned by /api/v1/auth/register or \
							/api/v1/auth/login. Tokens expire after 15 minutes; use \
							/api/v1/auth/refresh to rotate."""));

		// Register the error schema explicitly so every documented failure can
		// reference it, including on operations that never return it directly.
		ModelConverters.getInstance()
			.readAll(new AnnotatedType(ErrorResponse.class))
			.forEach(components::addSchemas);

		// No hard-coded server: springdoc then derives it from the incoming
		// request, so "Try it out" always targets whatever port this instance is
		// actually listening on rather than a guess baked in at build time.
		return new OpenAPI().info(info())
			.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
			.components(components);
	}

	private static Info info() {
		return new Info().title("AccountFlow API")
			.version("v1")
			.description("""
					Personal financial account management: bank accounts, cards, \
					transactions, transfers and physical cash.

					### Authentication
					Every endpoint except `/api/v1/auth/register`, `/api/v1/auth/login` \
					and `/api/v1/auth/refresh` requires a bearer token. The signed-in \
					user is taken from the token, never from a path or body, and one \
					user can never read or modify another's data - a foreign resource \
					answers `404`, not `403`, so the API cannot be used to discover ids.

					### Money values
					All monetary amounts are JSON **strings** at the currency's exact \
					scale, for example `"10000.00"`. A JSON number would be parsed as an \
					IEEE-754 double by most clients and lose precision. Amounts must be \
					positive, and an amount with more decimal places than the currency \
					allows is rejected rather than rounded.

					### Idempotency
					Every money-moving endpoint requires an `Idempotency-Key` header. \
					Retrying with the same key returns the original result instead of \
					moving money twice. Reusing a key with a different body is rejected \
					with `IDEMPOTENCY_KEY_REUSED`.

					### Errors
					Failures share one shape: `success: false` plus a stable `code` your \
					client can branch on, a human-readable `message`, the `path`, and the \
					`requestId` also returned in the `X-Request-Id` response header.""")
			.contact(new Contact().name("AccountFlow"));
	}

}
