package com.makehollywood.api;

import com.makehollywood.dto.ScriptDto;
import com.makehollywood.model.User;
import com.makehollywood.repository.UserRepository;
import com.makehollywood.service.GroqClient;
import com.makehollywood.service.IdeaService;
import com.makehollywood.service.ScriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scripts")
@RequiredArgsConstructor
public class ScriptApiController {

    private final ScriptService scriptService;
    private final UserRepository userRepository;

    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate(
            @RequestBody ScriptDto.GenerateRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            getUser(userDetails);
            return ResponseEntity.ok(scriptService.generate(req));
        } catch (IdeaService.ContentModerationException e) {
            return ResponseEntity.status(422).body(Map.of("error", "moderation"));
        } catch (GroqClient.GroqRateLimitException e) {
            return ResponseEntity.status(429).body(Map.of("error", "rate_limit"));
        } catch (GroqClient.GroqTimeoutException e) {
            return ResponseEntity.status(504).body(Map.of("error", "timeout"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "generation_failed"));
        }
    }

    @PostMapping("/refine")
    public ResponseEntity<?> refine(
            @RequestBody ScriptDto.RefineRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            getUser(userDetails);
            return ResponseEntity.ok(scriptService.refine(req));
        } catch (IdeaService.ContentModerationException e) {
            return ResponseEntity.status(422).body(Map.of("error", "moderation"));
        } catch (GroqClient.GroqRateLimitException e) {
            return ResponseEntity.status(429).body(Map.of("error", "rate_limit"));
        } catch (GroqClient.GroqTimeoutException e) {
            return ResponseEntity.status(504).body(Map.of("error", "timeout"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "refinement_failed"));
        }
    }

    @PostMapping
    public ResponseEntity<ScriptDto.Response> save(
            @RequestBody ScriptDto.SaveRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(scriptService.save(req, getUser(userDetails)));
    }

    @GetMapping
    public ResponseEntity<List<ScriptDto.Response>> getAll(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(scriptService.getAll(getUser(userDetails)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ScriptDto.Response> update(
            @PathVariable Long id,
            @RequestBody ScriptDto.UpdateRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(scriptService.update(id, req, getUser(userDetails)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        scriptService.delete(id, getUser(userDetails));
        return ResponseEntity.noContent().build();
    }
}
