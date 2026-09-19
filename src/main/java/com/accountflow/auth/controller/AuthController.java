package com.accountflow.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.accountflow.auth.dto.LoginRequest;
import com.accountflow.auth.dto.RefreshRequest;
import com.accountflow.auth.dto.RegisterRequest;
import com.accountflow.auth.dto.TokenResponse;
import com.accountflow.auth.service.AuthService;
import com.accountflow.common.api.ApiResponse;
import com.accountflow.common.web.ClientInfo;
import com.accountflow.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, sign-in and token lifecycle")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@SecurityRequirements
	@Operation(summary = "Register a new user and return an initial token pair")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Registered; tokens returned")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email or phone already registered")
	public ResponseEntity<ApiResponse<TokenResponse>> register(@Valid @RequestBody RegisterRequest request,
			HttpServletRequest httpRequest) {
		TokenResponse tokens = this.authService.register(request, ClientInfo.ipAddress(httpRequest),
				ClientInfo.userAgent(httpRequest));
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.of(tokens, "Registration successful"));
	}

	@PostMapping("/login")
	@SecurityRequirements
	@Operation(summary = "Exchange credentials for an access and refresh token")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Signed in")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials - identical for an unknown account")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "Locked after five failed attempts")
	public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest) {
		return ApiResponse.of(this.authService.login(request, ClientInfo.ipAddress(httpRequest),
				ClientInfo.userAgent(httpRequest)), "Login successful");
	}

	@PostMapping("/refresh")
	@SecurityRequirements
	@Operation(summary = "Rotate a refresh token for a new token pair")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Rotated; the presented token is now revoked")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid, expired, or already-used token (the family is revoked)")
	public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request,
			HttpServletRequest httpRequest) {
		return ApiResponse.of(this.authService.refresh(request.refreshToken(), ClientInfo.ipAddress(httpRequest),
				ClientInfo.userAgent(httpRequest)), "Token refreshed");
	}

	@PostMapping("/logout")
	@Operation(summary = "Revoke every refresh token and invalidate outstanding access tokens")
	public ApiResponse<Void> logout(@AuthenticationPrincipal AuthenticatedUser currentUser) {
		this.authService.logout(currentUser.userId());
		return ApiResponse.of(null, "Logged out");
	}

}
