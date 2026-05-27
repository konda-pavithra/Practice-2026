package com.practice.demo.controller;

import com.practice.demo.dto.RegistrationRequest;
import com.practice.demo.dto.RegistrationResponse;
import com.practice.demo.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@RequestBody RegistrationRequest request) {
        logger.info("POST /api/users/register — incoming registration request for username: '{}'",
                request.getUsername());
        RegistrationResponse response = userService.register(request);
        logger.info("POST /api/users/register — registration completed for username: '{}'",
                request.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
