package com.example.demo.exception;

public class BookingAccessDeniedException extends RuntimeException {
    public BookingAccessDeniedException(String message) { super(message); }
}
