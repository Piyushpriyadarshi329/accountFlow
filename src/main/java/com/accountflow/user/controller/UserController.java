package com.accountflow.user.controller;

import jakarta.validation.Valid;

import com.accountflow.common.api.ApiResponse;
import com.accountflow.security.AuthenticatedUser;
import com.accountflow.user.dto.UpdateProfileRequest;
import com.accountflow.user.dto.UserResponse;
import com.accountflow.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Profile of the signed-in user")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/me")
	@Operation(summary = "Get the signed-in user's profile")
	public ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
		// The id comes from the token, never from the request.
		return ApiResponse.of(this.userService.getProfile(currentUser.userId()));
	}

	@PutMapping("/me")
	@Operation(summary = "Update the signed-in user's profile")
	public ApiResponse<UserResponse> updateMe(@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Valid @RequestBody UpdateProfileRequest request) {
		return ApiResponse.of(this.userService.updateProfile(currentUser.userId(), request), "Profile updated");
	}

}
