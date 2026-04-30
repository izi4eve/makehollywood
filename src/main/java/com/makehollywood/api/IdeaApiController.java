package com.makehollywood.api;

import com.makehollywood.dto.IdeaDto;
import com.makehollywood.model.User;
import com.makehollywood.repository.UserRepository;
import com.makehollywood.service.GroqClient;
import com.makehollywood.service.IdeaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ideas")
@RequiredArgsConstructor
public class IdeaApiController {

    private final IdeaService ideaService;
    private final UserRepository userRepository;

    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @PostMapping("/extract")
    public ResponseEntity<?> extract(
            @RequestBody IdeaDto.ExtractRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            getUser(userDetails);
            return ResponseEntity.ok(ideaService.extract(req.getText(), req.getInputLang(), req.getOutputLang()));
        } catch (IdeaService.ContentModerationException e) {
            return ResponseEntity.status(422).body(Map.of("error", "moderation"));
        } catch (GroqClient.GroqRateLimitException e) {
            return ResponseEntity.status(429).body(Map.of("error", "rate_limit"));
        } catch (GroqClient.GroqTimeoutException e) {
            return ResponseEntity.status(504).body(Map.of("error", "timeout"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "extraction_failed"));
        }
    }

    @PostMapping
    public ResponseEntity<IdeaDto.Response> save(
            @RequestBody IdeaDto.SaveRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ideaService.save(req, getUser(userDetails)));
    }

    @GetMapping
    public ResponseEntity<List<IdeaDto.Response>> getAll(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ideaService.getAll(getUser(userDetails)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<IdeaDto.Response> update(
            @PathVariable Long id,
            @RequestBody IdeaDto.UpdateRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ideaService.update(id, req, getUser(userDetails)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        ideaService.delete(id, getUser(userDetails));
        return ResponseEntity.noContent().build();
    }
}