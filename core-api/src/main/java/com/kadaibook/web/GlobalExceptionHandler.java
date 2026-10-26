package com.kadaibook.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(
                exception.status(),
                exception.code(),
                exception.getMessage(),
                request.getRequestURI());
        return ResponseEntity.status(exception.status())
                .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                .body(problem);
    }

    @ExceptionHandler(com.kadaibook.money.Money.InvalidMoneyException.class)
    public ResponseEntity<ProblemDetail> handleInvalidMoney(
            com.kadaibook.money.Money.InvalidMoneyException exception,
            HttpServletRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "invalid_amount",
                exception.getMessage(),
                request.getRequestURI());
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            @NonNull MaxUploadSizeExceededException exception,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "The image exceeds the upload limit.");
        problem.setTitle("Image too large");
        problem.setType(URI.create("https://kadaibook.local/errors/image_too_large"));
        problem.setProperty("code", "image_too_large");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                .body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleConflict(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        log.warn("database constraint rejected a request on {}", request.getRequestURI());
        ProblemDetail problem = problem(
                HttpStatus.CONFLICT,
                "conflict",
                "The request conflicts with existing data.",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("unhandled error on {}", request.getRequestURI(), exception);
        ProblemDetail problem = problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal_error",
                "Something went wrong. The action was not completed.",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException exception,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        exception.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.putIfAbsent("request", error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Request validation failed");
        problem.setDetail("One or more fields are invalid.");
        problem.setType(URI.create("https://kadaibook.local/errors/validation_failed"));
        problem.setProperty("code", "validation_failed");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest()
                .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                .body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull org.springframework.http.converter.HttpMessageNotReadableException exception,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Malformed request body");
        problem.setDetail("The request body could not be read. Check the field types and values.");
        problem.setType(URI.create("https://kadaibook.local/errors/malformed_body"));
        problem.setProperty("code", "malformed_body");
        return ResponseEntity.badRequest()
                .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                .body(problem);
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("https://kadaibook.local/errors/" + code));
        problem.setInstance(URI.create(path));
        problem.setProperty("code", code);
        return problem;
    }
}
