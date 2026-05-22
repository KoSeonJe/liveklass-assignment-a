package com.liveklass.assignment.common.web;

import com.liveklass.assignment.common.exception.BusinessException;
import com.liveklass.assignment.common.exception.ErrorCode;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        ErrorCode ec = ex.errorCode();
        return ResponseEntity.status(ec.status())
                .body(ErrorResponse.of(ec.code(), ex.getMessage()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        ErrorCode ec = ErrorCode.MISSING_HEADER;
        return ResponseEntity.status(ec.status())
                .body(ErrorResponse.of(ec.code(), "Missing header: " + ex.getHeaderName()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        ErrorCode ec = ErrorCode.INVALID_REQUEST;
        List<ErrorResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(ec.status())
                .body(ErrorResponse.of(ec.code(), "Validation failed", fields));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ErrorCode ec = ErrorCode.INVALID_REQUEST;
        String message = "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue();
        return ResponseEntity.status(ec.status())
                .body(ErrorResponse.of(ec.code(), message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorCode ec = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(ec.status())
                .body(ErrorResponse.of(ec.code(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception ex) {
        ErrorCode ec = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(ec.status())
                .body(ErrorResponse.of(ec.code(), ex.getMessage()));
    }
}
