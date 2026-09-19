package com.accountflow.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	private static final String[] PUBLIC_PATHS = { "/api/v1/auth/register", "/api/v1/auth/login",
			"/api/v1/auth/refresh", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health",
			"/health" };

	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	private final RestAuthenticationEntryPoint authenticationEntryPoint;

	private final RestAccessDeniedHandler accessDeniedHandler;

	public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
			RestAuthenticationEntryPoint authenticationEntryPoint, RestAccessDeniedHandler accessDeniedHandler) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.authenticationEntryPoint = authenticationEntryPoint;
		this.accessDeniedHandler = accessDeniedHandler;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
			// No cookies or sessions are used, so CSRF has nothing to protect.
			.csrf((csrf) -> csrf.disable())
			// Enabled for the Expo web build; see CorsConfig for the allow-list.
			.cors(org.springframework.security.config.Customizer.withDefaults())
			.httpBasic((basic) -> basic.disable())
			.formLogin((form) -> form.disable())
			.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests((auth) -> auth.requestMatchers(PUBLIC_PATHS)
				.permitAll()
				.requestMatchers(HttpMethod.OPTIONS, "/**")
				.permitAll()
				.anyRequest()
				.authenticated())
			.exceptionHandling((ex) -> ex.authenticationEntryPoint(this.authenticationEntryPoint)
				.accessDeniedHandler(this.accessDeniedHandler))
			.addFilterBefore(this.jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
			.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		// Cost 12: ~250ms per hash on current hardware, which is the point.
		return new BCryptPasswordEncoder(12);
	}

}
