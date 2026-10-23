package com.stock.web;

import com.stock.service.UserService;
import com.stock.web.Auth.AdminOnly;
import com.stock.web.Auth.Public;
import com.stock.web.Auth.SessionUser;
import com.stock.web.Dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserService users;
    private final boolean loginEnabled;

    public AuthController(UserService users, @Value("${stock.login.enabled:false}") boolean loginEnabled) {
        this.users = users;
        this.loginEnabled = loginEnabled;
    }

    /** Імена для екрана входу — на сенсорному екрані зручніше натиснути, ніж набирати. */
    @Public
    @GetMapping("/login-names")
    public List<String> loginNames() { return users.names(); }

    @Public
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody Login req, HttpServletRequest http) throws InterruptedException {
        var user = users.authenticate(req.username(), req.password());
        if (user.isEmpty()) {
            Thread.sleep(700); // уповільнюємо підбір пароля
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Wrong name or password"));
        }
        http.getSession(true);
        http.changeSessionId(); // нова сесія після входу
        SessionUser su = SessionUser.of(user.get());
        http.getSession().setAttribute(Auth.SESSION_KEY, su);
        return ResponseEntity.ok(toMe(su));
    }

    @Public
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest http) {
        var s = http.getSession(false);
        if (s != null) s.invalidate();
    }

    @GetMapping("/me")
    public Me me(@RequestAttribute(Auth.SESSION_KEY) SessionUser u) { return toMe(u); }

    @PutMapping("/me/password")
    public Me changeOwnPassword(@Valid @RequestBody ChangePassword req, @RequestAttribute(Auth.SESSION_KEY) SessionUser u) {
        users.changeOwnPassword(u.id(), req.password());
        return toMe(u);
    }

    private Me toMe(SessionUser u) {
        if (!loginEnabled) return new Me(u.username(), true, false, false);
        return new Me(u.username(), u.admin(), u.admin() && users.defaultPasswordInUse(), true);
    }

    // ---------- Керування користувачами (адмін) ----------

    @AdminOnly
    @GetMapping("/users")
    public List<UserView> list() { return users.list(); }

    @AdminOnly
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@Valid @RequestBody CreateUser req) {
        return users.create(req.username(), req.password(), req.role());
    }

    @AdminOnly
    @PutMapping("/users/{id}")
    public UserView update(@PathVariable Long id, @Valid @RequestBody UpdateUser req,
                           @RequestAttribute(Auth.SESSION_KEY) SessionUser me) {
        return users.update(id, req.password(), req.role(), me.id());
    }

    @AdminOnly
    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @RequestAttribute(Auth.SESSION_KEY) SessionUser me) {
        users.delete(id, me.id());
    }
}
