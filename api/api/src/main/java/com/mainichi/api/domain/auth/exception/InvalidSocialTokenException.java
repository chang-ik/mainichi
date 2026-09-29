package com.mainichi.api.domain.auth.exception;

public class InvalidSocialTokenException extends RuntimeException {
    public InvalidSocialTokenException() {
        super("INVALID_SOCIAL_TOKEN");
    }
}
