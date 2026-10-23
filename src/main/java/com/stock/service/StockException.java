package com.stock.service;

public abstract class StockException extends RuntimeException {
    protected StockException(String msg) { super(msg); }

    public static class NotFound extends StockException { public NotFound(String m) { super(m); } }
    public static class Conflict extends StockException { public Conflict(String m) { super(m); } }
    public static class BadRequest extends StockException { public BadRequest(String m) { super(m); } }
}
