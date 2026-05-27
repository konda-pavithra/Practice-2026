package com.practice.demo.controller;

import com.practice.demo.dto.LoginRequest;
import com.practice.demo.dto.LoginResponse;
import com.practice.demo.dto.RegistrationRequest;
import com.practice.demo.dto.RegistrationResponse;
import com.practice.demo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints — no JWT required.
 *
 * <p>After a successful login, copy the {@code token} value from the response,
 * click <b>Authorize 🔒</b> at the top of the Swagger UI, paste the token
 * (without the {@code Bearer } prefix), and all subsequent "Try it out" calls
 * will include it automatically.
 */
@Tag(
    name        = "Authentication",
    description = "User registration and login. These endpoints are public — no JWT required."
)
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // -----------------------------------------------------------------------
    // POST /api/users/register
    // -----------------------------------------------------------------------

    @Operation(
        summary     = "Register a new user",
        description = "Creates a new account. Username and email must be unique. "
                    + "The password is stored as a BCrypt hash."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description  = "Account created successfully",
            content      = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema    = @Schema(implementation = RegistrationResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description  = "Validation failure — invalid username, email, or password format",
            content      = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(
            responseCode = "409",
            description  = "Username or email already registered",
            content      = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        )
    })
    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@RequestBody RegistrationRequest request) {
        logger.info("POST /api/users/register — received registration request for username: '{}'",
                request.getUsername());
        RegistrationResponse response = userService.register(request);
        logger.info("POST /api/users/register — registration completed for username: '{}'",
                request.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // -----------------------------------------------------------------------
    // POST /api/users/login
    // -----------------------------------------------------------------------

    @Operation(
        summary     = "Login and obtain a JWT",
        description = "Validates credentials and returns a signed JWT (24-hour validity). "
                    + "Copy the `token` value and use it in the **Authorize 🔒** dialog above."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description  = "Login successful — JWT returned",
            content      = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema    = @Schema(implementation = LoginResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description  = "Invalid username or password",
            content      = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        )
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        logger.info("POST /api/users/login — received login request for username: '{}'",
                request.getUsername());
        LoginResponse response = userService.login(request);
        logger.info("POST /api/users/login — login successful for username: '{}'",
                request.getUsername());
        return ResponseEntity.ok(response);
    }
}
