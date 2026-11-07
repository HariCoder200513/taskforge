package io.taskforge.web;

/** A uniqueness/state conflict → HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
