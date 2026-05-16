package org.agty.mtx.controller;

import org.agty.mtx.dto.AuthRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthControllerTest {

    @Test
    void login_returnsUnauthorizedWhenAuthenticationFails() {
        AuthenticationManager authManager = authentication -> {
            throw new AuthenticationException("bad credentials") {
            };
        };

        AuthController controller = new AuthController(
                null,
                null,
                authManager,
                null,
                null
        );

        AuthRequest request = new AuthRequest();
        request.setUsername("demo");
        request.setPassword("wrongpass");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.login(request, new MockHttpServletRequest())
        );

        assertEquals(401, ex.getStatusCode().value());
        assertEquals("Неверный логин или пароль", ex.getReason());
    }

    @Test
    void register_rejectsTooShortUsername() {
        AuthenticationManager authManager = authentication -> authentication;

        AuthController controller = new AuthController(
                null,
                null,
                authManager,
                null,
                null
        );

        AuthRequest request = new AuthRequest();
        request.setUsername("ab");
        request.setPassword("valid123");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.register(request, new MockHttpServletRequest())
        );

        assertEquals(400, ex.getStatusCode().value());
        assertEquals("Логин должен быть длиной 3-64 символа", ex.getReason());
    }
}
