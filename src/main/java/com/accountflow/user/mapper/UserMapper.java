package com.accountflow.user.mapper;

import com.accountflow.user.domain.User;
import com.accountflow.user.dto.UserResponse;

public final class UserMapper {

	private UserMapper() {
	}

	public static UserResponse toResponse(User user) {
		return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
				user.getPhone(), user.getRole(), user.getStatus(), user.getCreatedAt(), user.getUpdatedAt());
	}

}
