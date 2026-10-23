package com.stock.service;

import com.stock.model.AppUser;
import com.stock.model.AppUser.Role;
import com.stock.repo.AppUserRepository;
import com.stock.web.Dto.UserView;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class UserService implements SmartInitializingSingleton {

    /** Перший запуск: створюється адмін admin / admin — пароль треба змінити в «Користувачі». */
    public static final String DEFAULT_ADMIN = "admin";

    private static final int ITERATIONS = 120_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository users;

    public UserService(AppUserRepository users) { this.users = users; }

    /** Ще до старту веб-сервера, щоб перший вхід не випередив створення адміна. */
    @Override
    @Transactional
    public void afterSingletonsInstantiated() {
        if (users.count() == 0) users.save(new AppUser(DEFAULT_ADMIN, hash(DEFAULT_ADMIN), Role.ADMIN));
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> authenticate(String username, String password) {
        return users.findByUsernameIgnoreCase(username.trim())
                .filter(u -> matches(password, u.getPasswordHash()));
    }

    /** Для режиму без входу: дії записуються від імені адміна. */
    @Transactional(readOnly = true)
    public AppUser anyAdmin() {
        return users.findAllByOrderByUsernameAsc().stream().filter(AppUser::isAdmin).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> find(Long id) { return users.findById(id); }

    @Transactional(readOnly = true)
    public List<UserView> list() { return users.findAllByOrderByUsernameAsc().stream().map(UserView::of).toList(); }

    @Transactional(readOnly = true)
    public List<String> names() { return users.findAllByOrderByUsernameAsc().stream().map(AppUser::getUsername).toList(); }

    /** Адмін досі з паролем за замовчуванням — інтерфейс показує попередження. */
    @Transactional(readOnly = true)
    public boolean defaultPasswordInUse() {
        return users.findByUsernameIgnoreCase(DEFAULT_ADMIN).filter(u -> matches(DEFAULT_ADMIN, u.getPasswordHash())).isPresent();
    }

    @Transactional
    public UserView create(String username, String password, Role role) {
        String n = username.trim();
        if (users.existsByUsernameIgnoreCase(n)) throw new StockException.Conflict("User already exists: " + n);
        return UserView.of(users.save(new AppUser(n, hash(password), role)));
    }

    @Transactional
    public UserView update(Long id, String password, Role role, Long currentUserId) {
        AppUser u = users.findById(id).orElseThrow(() -> new StockException.NotFound("User not found"));
        if (role != null && role != u.getRole()) {
            if (u.isAdmin() && users.countByRole(Role.ADMIN) == 1)
                throw new StockException.Conflict("At least one admin must remain");
            if (id.equals(currentUserId)) throw new StockException.Conflict("You cannot change your own role");
            u.setRole(role);
        }
        if (password != null && !password.isBlank()) u.setPasswordHash(hash(password));
        return UserView.of(u);
    }

    /** Користувач сам задає собі пароль — так адмін замінює admin на свій. */
    @Transactional
    public void changeOwnPassword(Long id, String password) {
        AppUser u = users.findById(id).orElseThrow(() -> new StockException.NotFound("User not found"));
        if (u.getUsername().equalsIgnoreCase(DEFAULT_ADMIN) && password.equals(DEFAULT_ADMIN))
            throw new StockException.Conflict("Choose a password other than admin");
        u.setPasswordHash(hash(password));
    }

    @Transactional
    public void delete(Long id, Long currentUserId) {
        AppUser u = users.findById(id).orElseThrow(() -> new StockException.NotFound("User not found"));
        if (id.equals(currentUserId)) throw new StockException.Conflict("You cannot delete yourself");
        if (u.isAdmin() && users.countByRole(Role.ADMIN) == 1)
            throw new StockException.Conflict("At least one admin must remain");
        users.delete(u);
    }

    // ---------- Паролі ----------

    static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        Base64.Encoder b64 = Base64.getEncoder();
        return b64.encodeToString(salt) + ":" + b64.encodeToString(pbkdf2(password, salt));
    }

    static boolean matches(String password, String stored) {
        String[] parts = stored.split(":", 2);
        if (parts.length != 2) return false;
        Base64.Decoder b64 = Base64.getDecoder();
        return MessageDigest.isEqual(pbkdf2(password, b64.decode(parts[0])), b64.decode(parts[1]));
    }

    private static byte[] pbkdf2(String password, byte[] salt) {
        try {
            var spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
