package com.stock.web;

import com.stock.service.StockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(StockException.NotFound.class)
    ResponseEntity<Map<String, String>> notFound(StockException e) { return body(HttpStatus.NOT_FOUND, e.getMessage()); }

    @ExceptionHandler(StockException.Conflict.class)
    ResponseEntity<Map<String, String>> conflict(StockException e) { return body(HttpStatus.CONFLICT, e.getMessage()); }

    @ExceptionHandler(StockException.BadRequest.class)
    ResponseEntity<Map<String, String>> bad(StockException e) { return body(HttpStatus.BAD_REQUEST, e.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        var f = e.getBindingResult().getFieldError();
        return body(HttpStatus.BAD_REQUEST, f == null ? "Invalid data" : f.getField() + ": " + f.getDefaultMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> tooBig(MaxUploadSizeExceededException e) { return body(HttpStatus.BAD_REQUEST, "File too large (max 20 MB)"); }

    private static ResponseEntity<Map<String, String>> body(HttpStatus s, String msg) {
        return ResponseEntity.status(s).body(Map.of("error", msg));
    }
}
