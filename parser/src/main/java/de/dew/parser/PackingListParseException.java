package de.dew.parser;

public class PackingListParseException extends Exception {

    public PackingListParseException(String message) {
        super(message);
    }

    public PackingListParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
