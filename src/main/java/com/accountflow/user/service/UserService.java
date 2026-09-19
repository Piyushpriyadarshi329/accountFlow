package com.accountflow.user.service;

import com.accountflow.common.exception.DuplicatePhoneException;
import com.accountflow.common.exception.UserNotFoundException;
import com.accountflow.user.domain.User;
import com.accountflow.user.dto.UpdateProfileRequest;
import com.accountflow.user.dto.UserResponse;
import com.accountflow.user.mapper.UserMapper;
import com.accountflow.user.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public UserResponse getProfile(String userId) {
		return UserMapper.toResponse(requireUser(userId));
	}

	public UserResponse updateProfile(String userId, UpdateProfileRequest request) {
		User user = requireUser(userId);
		String phone = (request.phone() != null && !request.phone().isBlank()) ? request.phone().trim() : null;

		if (phone != null && !phone.equals(user.getPhone()) && this.userRepository.existsByPhone(phone)) {
			throw new DuplicatePhoneException();
		}

		user.setFirstName(request.firstName().trim());
		user.setLastName((request.lastName() != null) ? request.lastName().trim() : null);
		user.setPhone(phone);
		// Email and role are deliberately not updatable here: changing an email
		// is an identity change and needs its own verified flow.
		return UserMapper.toResponse(this.userRepository.save(user));
	}

	private User requireUser(String userId) {
		return this.userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
	}

}
