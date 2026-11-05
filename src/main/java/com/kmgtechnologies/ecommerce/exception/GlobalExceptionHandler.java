package com.kmgtechnologies.ecommerce.exception;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(DuplicateResourceException ex) {
        return problem(HttpStatus.CONFLICT, "Resource already exists", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Data integrity violation", "The request conflicts with existing data");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "An unexpected error occurred");
    }

    @ExceptionHandler(ResourceInUseException.class)
    public ProblemDetail handleInUse(ResourceInUseException ex) {
        return problem(HttpStatus.CONFLICT, "Resource in use", ex.getMessage());
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail handleInsufficientStock(InsufficientStockException ex) {
        return problem(HttpStatus.CONFLICT, "Insufficient stock", ex.getMessage());
    }

    @ExceptionHandler(EmptyCartException.class)
    public ProblemDetail handleEmptyCart(EmptyCartException ex) {
        return problem(HttpStatus.CONFLICT, "Empty cart", ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic locking failure: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "The resource was modified by another request, please retry");
    }

    @ExceptionHandler(ImageTooLargeException.class)
    public ProblemDetail handleImageTooLarge(ImageTooLargeException ex) {
        return imageProblem(HttpStatus.CONTENT_TOO_LARGE, "Image too large", ex.getMessage(), "IMAGE_TOO_LARGE");
    }

    @ExceptionHandler(UnsupportedImageException.class)
    public ProblemDetail handleUnsupportedImage(UnsupportedImageException ex) {
        return imageProblem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported image", ex.getMessage(), "UNSUPPORTED_IMAGE");
    }

    @ExceptionHandler(InvalidImageException.class)
    public ProblemDetail handleInvalidImage(InvalidImageException ex) {
        return imageProblem(HttpStatus.UNPROCESSABLE_CONTENT, "Invalid image", ex.getMessage(), "INVALID_IMAGE");
    }

    @ExceptionHandler(StorageUnavailableException.class)
    public ProblemDetail handleStorageUnavailable(StorageUnavailableException ex) {
        log.warn("Media storage unavailable: {}", ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
        return imageProblem(HttpStatus.SERVICE_UNAVAILABLE, "Storage unavailable", ex.getMessage(), "STORAGE_UNAVAILABLE");
    }

    /** Upload above the multipart limit (the largest image kind): refused before the file is read. */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            @NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        ProblemDetail problem = imageProblem(HttpStatus.CONTENT_TOO_LARGE, "Image too large",
                "The file exceeds the upload limit", "IMAGE_TOO_LARGE");
        return handleExceptionInternal(ex, problem, headers, HttpStatus.CONTENT_TOO_LARGE, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, @NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ex.getBody();
        problem.setTitle("Validation failed");
        problem.setDetail("One or more fields are invalid");
        problem.setProperty("errors", errors);

        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** Image errors carry a stable {@code code}: the front-end shows its own message for each. */
    private static ProblemDetail imageProblem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = problem(status, title, detail);
        problem.setProperty("code", code);
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}