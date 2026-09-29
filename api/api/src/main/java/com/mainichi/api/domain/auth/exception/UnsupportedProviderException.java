package com.mainichi.api.domain.auth.exception;

public class UnsupportedProviderException extends RuntimeException {
    public UnsupportedProviderException() {
        super("UNSUPPORTED_PROVIDER");
    }
}