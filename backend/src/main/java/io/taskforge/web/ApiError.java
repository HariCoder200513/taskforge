package io.taskforge.web;

/** Uniform error envelope: {@code { "error": { code, message, requestId } }}. */
public record ApiError(Body error) {

    public record Body(String code, String message, String requestId) {
    }

    public static ApiError of(String code, String message, String requestId) {
        return new ApiError(new Body(code, message, requestId));
    }
}
