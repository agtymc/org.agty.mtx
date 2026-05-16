package org.agty.mtx.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.agty.mtx.dto.ChangePasswordRequest;
import org.agty.mtx.dto.AuthRequest;
import org.agty.mtx.dto.AuthResponse;
import org.agty.mtx.dto.ProfileUpdateRequest;
import org.agty.mtx.dto.UserProfileDto;
import org.agty.mtx.entity.UserAccount;
import org.agty.mtx.repository.UserAccountRepository;
import org.agty.mtx.service.ChatAppService;
import org.agty.mtx.service.CurrentUserService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final ChatAppService chatAppService;
    private final CurrentUserService currentUserService;

    public AuthController(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            ChatAppService chatAppService,
            CurrentUserService currentUserService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.chatAppService = chatAppService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        String username = normalizeUsername(request.getUsername());
        String password = normalizePassword(request.getPassword());

        if (userAccountRepository.existsByUsername(username)) {
            throw new ResponseStatusException(BAD_REQUEST, "Пользователь уже существует");
        }

        UserAccount user = new UserAccount();
        user.setUsername(username);
        if (username.contains("@")) {
            user.setEmail(username);
        }
        user.setPasswordHash(passwordEncoder.encode(password));
        user = userAccountRepository.save(user);
        chatAppService.ensureDefaults(user);

        authenticateAndStoreInSession(username, password, httpRequest);
        return new AuthResponse(true, username, "Регистрация выполнена");
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        String username = normalizeUsername(request.getUsername());
        String password = normalizePassword(request.getPassword());

        authenticateAndStoreInSession(username, password, httpRequest);
        return new AuthResponse(true, username, "Вход выполнен");
    }

    @PostMapping("/logout")
    public AuthResponse logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return new AuthResponse(true, null, "Вы вышли из аккаунта");
    }

    @GetMapping("/me")
    public AuthResponse me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return new AuthResponse(false, null, "Не авторизован");
        }
        return new AuthResponse(true, authentication.getName(), "OK");
    }

    @GetMapping("/profile")
    public UserProfileDto profile() {
        UserAccount user = currentUserService.requireCurrentUser();
        return new UserProfileDto(user.getUsername(), resolveDisplayName(user), resolveEmail(user));
    }

    @PutMapping("/profile")
    public UserProfileDto updateProfile(@RequestBody ProfileUpdateRequest request) {
        UserAccount user = currentUserService.requireCurrentUser();
        user.setDisplayName(normalizeDisplayName(request == null ? null : request.getDisplayName()));
        user.setEmail(normalizeEmail(request == null ? null : request.getEmail()));
        UserAccount saved = userAccountRepository.save(user);
        return new UserProfileDto(saved.getUsername(), resolveDisplayName(saved), resolveEmail(saved));
    }

    @PostMapping("/change-password")
    public AuthResponse changePassword(@RequestBody ChangePasswordRequest request) {
        UserAccount user = currentUserService.requireCurrentUser();
        String current = normalizePasswordOrThrow(request == null ? null : request.getCurrentPassword(), "Текущий пароль обязателен");
        String next = normalizePassword(request == null ? null : request.getNewPassword());

        if (!passwordEncoder.matches(current, user.getPasswordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Текущий пароль неверный");
        }
        if (current.equals(next)) {
            throw new ResponseStatusException(BAD_REQUEST, "Новый пароль должен отличаться от текущего");
        }

        user.setPasswordHash(passwordEncoder.encode(next));
        userAccountRepository.save(user);
        return new AuthResponse(true, user.getUsername(), "Пароль успешно обновлен");
    }

    private void authenticateAndStoreInSession(String username, String password, HttpServletRequest request) {
        try {
            Authentication authenticated = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            // Store only a stable serializable principal in session.
            // This avoids deserialization issues after restart/devtools reload.
            Authentication sessionAuthentication = new UsernamePasswordAuthenticationToken(
                    authenticated.getName(),
                    null,
                    authenticated.getAuthorities()
            );

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(sessionAuthentication);
            SecurityContextHolder.setContext(context);
            request.getSession(true).setAttribute(SPRING_SECURITY_CONTEXT_KEY, context);
        } catch (Exception ex) {
            throw new ResponseStatusException(UNAUTHORIZED, "Неверный логин или пароль");
        }
    }

    private String normalizeUsername(String username) {
        String value = username == null ? "" : username.trim();
        if (value.length() < 3 || value.length() > 64) {
            throw new ResponseStatusException(BAD_REQUEST, "Логин должен быть длиной 3-64 символа");
        }
        return value;
    }

    private String normalizePassword(String password) {
        String value = password == null ? "" : password.trim();
        if (value.length() < 6 || value.length() > 128) {
            throw new ResponseStatusException(BAD_REQUEST, "Пароль должен быть длиной 6-128 символов");
        }
        return value;
    }

    private String normalizePasswordOrThrow(String password, String errorMessage) {
        String value = password == null ? "" : password.trim();
        if (value.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, errorMessage);
        }
        return value;
    }

    private String normalizeDisplayName(String displayName) {
        String value = displayName == null ? "" : displayName.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > 120) {
            value = value.substring(0, 120);
        }
        return value;
    }

    private String resolveDisplayName(UserAccount user) {
        String configuredName = user.getDisplayName() == null ? "" : user.getDisplayName().trim();
        if (!configuredName.isBlank()) {
            return configuredName;
        }

        String username = user.getUsername() == null ? "" : user.getUsername().trim();
        int atIndex = username.indexOf('@');
        if (atIndex > 0) {
            return username.substring(0, atIndex);
        }
        return username;
    }

    private String normalizeEmail(String email) {
        String value = email == null ? "" : email.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > 190) {
            value = value.substring(0, 190);
        }
        return value;
    }

    private String resolveEmail(UserAccount user) {
        String configured = user.getEmail() == null ? "" : user.getEmail().trim();
        if (!configured.isBlank()) {
            return configured;
        }
        String username = user.getUsername() == null ? "" : user.getUsername().trim();
        return username.contains("@") ? username : "";
    }
}
