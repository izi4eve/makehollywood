package com.makehollywood.api;

import com.makehollywood.model.Role;
import com.makehollywood.model.User;
import com.makehollywood.repository.UserRepository;
import com.makehollywood.service.UserCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminApiController {

    private final UserRepository userRepository;
    private final UserCleanupService userCleanupService;

    @PostMapping("/set-role-api")
    public ResponseEntity<?> setRole(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String role = body.get("role");
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        user.setRole(Role.valueOf(role.toUpperCase()));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Role updated for " + email));
    }

    @PostMapping("/cleanup-users")
    public ResponseEntity<?> cleanup() {
        userCleanupService.removeUnconfirmedUsersManually();
        return ResponseEntity.ok(Map.of("message", "Cleanup completed"));
    }
}