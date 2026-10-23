package com.stock.web;

import com.stock.model.AppUser;
import jakarta.servlet.http.HttpSession;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Права доступу до API. За замовчуванням кожен запит вимагає входу;
 * {@link Public} — без входу, {@link AdminOnly} — лише для адміна.
 */
public final class Auth {
    private Auth() {}

    @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
    public @interface Public {}

    @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
    public @interface AdminOnly {}

    static final String SESSION_KEY = "stock.user";

    /** Те, що лежить у сесії після входу. */
    public record SessionUser(Long id, String username, boolean admin) {
        static SessionUser of(AppUser u) { return new SessionUser(u.getId(), u.getUsername(), u.isAdmin()); }
    }

    static SessionUser current(HttpSession session) {
        return session == null ? null : (SessionUser) session.getAttribute(SESSION_KEY);
    }
}
