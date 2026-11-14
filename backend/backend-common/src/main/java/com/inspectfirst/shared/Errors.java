package com.inspectfirst.shared;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class Errors {
    @ExceptionHandler(ResponseStatusException.class) ProblemDetail status(ResponseStatusException e) {
        return ProblemDetail.forStatusAndDetail(e.getStatusCode(),e.getReason()==null?"Request failed.":e.getReason());
    }
    @ExceptionHandler({MethodArgumentNotValidException.class,ConstraintViolationException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,org.springframework.web.bind.MissingServletRequestParameterException.class,org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ProblemDetail invalid(Exception e) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Check required fields, lengths and permitted values."); }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(Exception e) { return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,"Image must be at most 5 MB."); }
    @ExceptionHandler(java.nio.file.NoSuchFileException.class)
    ProblemDetail missingFile(Exception e) { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,"Attachment is unavailable."); }
    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,org.springframework.web.HttpRequestMethodNotSupportedException.class,org.springframework.web.HttpMediaTypeNotSupportedException.class})
    ProblemDetail requestError(Exception e) { var error=(org.springframework.web.ErrorResponse)e;return ProblemDetail.forStatusAndDetail(error.getStatusCode(),"Resource, method or content type is not supported."); }
    @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class)
    ProblemDetail concurrent(Exception e) { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"Concurrent update detected. Refresh and retry."); }
    @ExceptionHandler(DataIntegrityViolationException.class) ProblemDetail conflict(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"Data changed or conflicts with an existing record. Refresh and retry.");
    }
    @ExceptionHandler(Exception.class) ProblemDetail unexpected(Exception e) {
        org.slf4j.LoggerFactory.getLogger(Errors.class).error("Request failed",e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,"The request could not be completed.");
    }
}
