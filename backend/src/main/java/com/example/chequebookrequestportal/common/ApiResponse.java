package com.example.chequebookrequestportal.common;

public record ApiResponse<T>(String code, String message, T data) {
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>("200", message, data);
    }

    public static <T> ApiResponse<T> created(String message, T data) {
        return new ApiResponse<>("201", message, data);
    }

    public static <T> ApiResponse<T> failed(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
