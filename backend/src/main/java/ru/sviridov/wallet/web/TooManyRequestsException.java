package ru.sviridov.wallet.web;

/** Thrown when a login (or other rate-limited action) exceeds its allowed attempts. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
