package ru.sviridov.wallet.parser;

public class StatementParseException extends Exception {

    public StatementParseException(String message) {
        super(message);
    }

    public StatementParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
