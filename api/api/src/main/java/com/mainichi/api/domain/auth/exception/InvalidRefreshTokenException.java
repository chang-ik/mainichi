package com.mainichi.api.domain.auth.exception;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("INVALID_REFRESH_TOKEN");
    }
}
