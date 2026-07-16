package com.courseqa.exception;

// TODO: Use this exception when a resource is not found.
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
