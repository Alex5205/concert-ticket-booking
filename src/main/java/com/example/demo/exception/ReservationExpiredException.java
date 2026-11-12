package com.example.demo.exception;

public class ReservationExpiredException extends RuntimeException {
    public ReservationExpiredException(String message) { super(message); }
}
