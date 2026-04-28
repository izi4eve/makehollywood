package com.makehollywood.api;

import com.makehollywood.dto.AuthRequest;
import com.makehollywood.dto.AuthResponse;
import com.makehollywood.dto.RegisterRequest;
import com.makehollywood.model.Role;
import com.makehollywood.model.User;
import com.makehollywood.security.JwtTokenProvider;
import com.makehollywood.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final AuthenticationManager authManager;
    private final JwtTokenProvider tokenProvider;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        String accessToken = tokenProvider.generateAccessToken(auth);
        String refreshToken = tokenProvider.generateRefreshToken(auth.getName());
        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst().orElse("ROLE_USER");

        String provider = auth.getPrincipal() instanceof UserDetails ud
                ? userService.findByEmail(ud.getUsername())
                .map(User::getProvider)
                .orElse(null)
                : null;

        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken, auth.getName(), role, provider));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        Optional<User> existing = userService.findByEmail(request.getEmail());
        if (existing.isPresent()) {
            if (existing.get().isEnabled()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email already in use"));
            }
            // Пользователь есть но не подтверждён — удаляем и даём зарегистрироваться заново
            userService.deleteUserByEmail(request.getEmail());
        }
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword()); // UserService.save() закодирует
        user.setRole(Role.REGISTERED);
        userService.save(user, "en");

        return ResponseEntity.ok(Map.of("message", "Check your email to confirm registration"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        // Всегда возвращаем 200 — не раскрываем, есть ли такой email
        userService.findByEmail(email)
                .ifPresent(user -> userService.sendPasswordResetEmail(user, "en"));
        return ResponseEntity.ok(Map.of("message", "If this email exists, a reset link was sent"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (!tokenProvider.validateToken(refreshToken)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid refresh token"));
        }
        String username = tokenProvider.getUsernameFromToken(refreshToken);
        String newAccess = tokenProvider.generateAccessTokenFromUsername(username);
        String newRefresh = tokenProvider.generateRefreshToken(username);
        return ResponseEntity.ok(Map.of("accessToken", newAccess, "refreshToken", newRefresh));
    }

    @GetMapping("/confirm")
    public ResponseEntity<?> confirm(@RequestParam String token) {
        try {
            userService.confirmUser(token, "en");
            return ResponseEntity.ok(Map.of("message", "Email confirmed"));
        } catch (IllegalStateException e) {
            // Уже подтверждён — возвращаем 200
            return ResponseEntity.ok(Map.of("message", "Email already confirmed"));
        } catch (IllegalArgumentException e) {
            // Токен не найден или истёк — это реальная ошибка
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body,
                                            Authentication authentication) {
        String email = authentication.getName();
        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");

        User user = userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect"));
        }

        userService.updatePassword(email, newPassword, "en");
        return ResponseEntity.ok(Map.of("message", "Password changed"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String newPassword = body.get("newPassword");
        try {
            userService.resetPassword(token, newPassword, "en");
            return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/delete-account")
    public ResponseEntity<?> deleteAccount(Authentication authentication) {
        String email = authentication.getName();
        userService.deleteUserByEmail(email);
        return ResponseEntity.ok(Map.of("message", "Account deleted"));
    }
}