package com.inspectfirst.identity;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class Errors {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ProblemDetail invalid(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            "Enter a valid email, a name of 1–60 characters, and a password of 12–128 characters.");
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ProblemDetail status(org.springframework.web.server.ResponseStatusException e) { return ProblemDetail.forStatusAndDetail(e.getStatusCode(), e.getReason()); }
    @ExceptionHandler(Exception.class)
    ProblemDetail unavailable(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
            "The account service could not complete this request. Please try again.");
    }
}
