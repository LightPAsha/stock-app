package com.stock.web;

import com.stock.service.UserService;
import com.stock.web.Auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/** Перевіряє вхід і роль перед кожним викликом /api/**. */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** Фронтенд додає цей заголовок; чужий сайт у браузері не зможе його підробити без CORS. */
    static final String CSRF_HEADER = "X-Stock";

    private final UserService users;
    private final boolean loginEnabled;

    public AuthInterceptor(UserService users, @Value("${stock.login.enabled:false}") boolean loginEnabled) {
        this.users = users;
        this.loginEnabled = loginEnabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod hm)) return true;

        boolean mutating = !"GET".equals(req.getMethod()) && !"HEAD".equals(req.getMethod());
        if (mutating && req.getHeader(CSRF_HEADER) == null) return deny(res, HttpStatus.FORBIDDEN, "Request rejected");

        if (hm.hasMethodAnnotation(Auth.Public.class)) return true;

        // Вхід вимкнено — усі працюють як адмін, без пароля
        if (!loginEnabled) {
            req.setAttribute(Auth.SESSION_KEY, SessionUser.of(users.anyAdmin()));
            return true;
        }

        SessionUser u = Auth.current(req.getSession(false));
        // Користувача могли видалити або змінити йому роль, поки він був у системі
        var fresh = u == null ? null : users.find(u.id()).orElse(null);
        if (fresh == null) {
            if (u != null) req.getSession().invalidate();
            return deny(res, HttpStatus.UNAUTHORIZED, "Please sign in");
        }
        if (fresh.isAdmin() != u.admin()) req.getSession().setAttribute(Auth.SESSION_KEY, SessionUser.of(fresh));
        req.setAttribute(Auth.SESSION_KEY, SessionUser.of(fresh));

        if (hm.hasMethodAnnotation(Auth.AdminOnly.class) && !fresh.isAdmin())
            return deny(res, HttpStatus.FORBIDDEN, "Only an admin can do this");
        return true;
    }

    private static boolean deny(HttpServletResponse res, HttpStatus status, String msg) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"error\":\"" + msg + "\"}");
        return false;
    }
}
