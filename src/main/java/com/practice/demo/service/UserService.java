package com.practice.demo.service;

import com.practice.demo.dto.RegistrationRequest;
import com.practice.demo.dto.RegistrationResponse;
import com.practice.demo.entity.User;
import com.practice.demo.exception.InvalidEmailException;
import com.practice.demo.exception.InvalidPasswordException;
import com.practice.demo.exception.UserAlreadyExistsException;
import com.practice.demo.repository.UserRepository;
import com.practice.demo.validator.InputValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        logger.info("Registration attempt for username: '{}'", request.getUsername());

        if (!InputValidator.IS_NOT_BLANK.test(request.getUsername())) {
            logger.warn("Registration rejected: username is blank or null");
            throw new IllegalArgumentException("Username must not be blank");
        }

        if (!InputValidator.IS_VALID_EMAIL.test(request.getEmail())) {
            logger.warn("Registration rejected: invalid email format '{}'", request.getEmail());
            throw new InvalidEmailException("Invalid email format: '" + request.getEmail() + "'");
        }

        if (!InputValidator.IS_VALID_PASSWORD.test(request.getPassword())) {
            logger.warn("Registration rejected: password does not meet requirements for username '{}'",
                    request.getUsername());
            throw new InvalidPasswordException(
                    "Password must be at least 8 characters long and contain: " +
                    "an uppercase letter, a lowercase letter, a digit, " +
                    "and a special character from [@#$%^*-_]"
            );
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            logger.warn("Registration rejected: username '{}' already exists", request.getUsername());
            throw new UserAlreadyExistsException(
                    "Username '" + request.getUsername() + "' is already taken");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            logger.warn("Registration rejected: email '{}' already registered", request.getEmail());
            throw new UserAlreadyExistsException(
                    "Email '" + request.getEmail() + "' is already registered");
        }

        String encryptedPassword = passwordEncoder.encode(request.getPassword());
        logger.debug("Password encrypted successfully for username: '{}'", request.getUsername());

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(encryptedPassword)
                .build();

        User saved = userRepository.save(user);
        logger.info("User registered successfully — id: {}, username: '{}'", saved.getId(), saved.getUsername());

        return RegistrationResponse.builder()
                .id(saved.getId())
                .username(saved.getUsername())
                .email(saved.getEmail())
                .message("User registered successfully")
                .build();
    }
}
