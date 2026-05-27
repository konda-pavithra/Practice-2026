package com.practice.demo.validator;

import java.util.function.Predicate;

public final class InputValidator {

    private InputValidator() {}

    private static final Predicate<String> notNull =
            s -> s != null && !s.isBlank();

    private static final Predicate<String> minLength =
            s -> s.length() >= 8;

    private static final Predicate<String> hasUppercase =
            s -> s.chars().anyMatch(Character::isUpperCase);

    private static final Predicate<String> hasLowercase =
            s -> s.chars().anyMatch(Character::isLowerCase);

    private static final Predicate<String> hasDigit =
            s -> s.chars().anyMatch(Character::isDigit);

    // Allowed special characters: @, #, $, %, ^, *, -, _
    private static final Predicate<String> hasSpecialChar =
            s -> s.chars().anyMatch(c -> "@#$%^*-_".indexOf(c) >= 0);

    private static final Predicate<String> validEmailFormat =
            s -> s.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    // Composed password predicate: all rules must pass
    public static final Predicate<String> IS_VALID_PASSWORD = notNull
            .and(minLength)
            .and(hasUppercase)
            .and(hasLowercase)
            .and(hasDigit)
            .and(hasSpecialChar);

    public static final Predicate<String> IS_VALID_EMAIL = notNull.and(validEmailFormat);

    public static final Predicate<String> IS_NOT_BLANK = notNull;
}
