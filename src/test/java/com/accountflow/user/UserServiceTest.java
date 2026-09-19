package com.accountflow.user;

import java.util.Optional;

import com.accountflow.common.exception.DuplicatePhoneException;
import com.accountflow.common.exception.UserNotFoundException;
import com.accountflow.user.domain.Role;
import com.accountflow.user.domain.User;
import com.accountflow.user.domain.UserStatus;
import com.accountflow.user.dto.UpdateProfileRequest;
import com.accountflow.user.repository.UserRepository;
import com.accountflow.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private UserService userService;

	private static User user() {
		return User.builder()
			.id("user-1")
			.firstName("Piyush")
			.email("piyush@example.com")
			.phone("+919876543210")
			.passwordHash("$2a$12$hash")
			.role(Role.USER)
			.status(UserStatus.ACTIVE)
			.build();
	}

	@Test
	@DisplayName("profile response carries no password field")
	void profileExcludesPassword() {
		given(this.userRepository.findById("user-1")).willReturn(Optional.of(user()));

		var response = this.userService.getProfile("user-1");

		assertThat(response.email()).isEqualTo("piyush@example.com");
		assertThat(response.toString()).doesNotContain("hash").doesNotContain("$2a$");
	}

	@Test
	@DisplayName("unknown user is reported, not returned as empty")
	void unknownUserThrows() {
		given(this.userRepository.findById("ghost")).willReturn(Optional.empty());

		assertThatThrownBy(() -> this.userService.getProfile("ghost")).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	@DisplayName("updating the profile leaves email and role untouched")
	void updateLeavesIdentityFieldsAlone() {
		User existing = user();
		given(this.userRepository.findById("user-1")).willReturn(Optional.of(existing));
		given(this.userRepository.save(any(User.class))).willAnswer((i) -> i.getArgument(0));

		var response = this.userService.updateProfile("user-1",
				new UpdateProfileRequest("Updated", "Name", "+919876543210"));

		assertThat(response.firstName()).isEqualTo("Updated");
		assertThat(existing.getEmail()).isEqualTo("piyush@example.com");
		assertThat(existing.getRole()).isEqualTo(Role.USER);
	}

	@Test
	@DisplayName("rejects a phone number already used by someone else")
	void rejectsDuplicatePhone() {
		given(this.userRepository.findById("user-1")).willReturn(Optional.of(user()));
		given(this.userRepository.existsByPhone("+911111111111")).willReturn(true);

		assertThatThrownBy(() -> this.userService.updateProfile("user-1",
				new UpdateProfileRequest("Piyush", null, "+911111111111")))
			.isInstanceOf(DuplicatePhoneException.class);
	}

}
