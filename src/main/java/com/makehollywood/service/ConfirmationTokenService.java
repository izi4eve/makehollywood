package com.makehollywood.service;

import com.makehollywood.model.ConfirmationToken;
import com.makehollywood.model.User;
import com.makehollywood.repository.ConfirmationTokenRepository;
import com.makehollywood.util.MessageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConfirmationTokenService {

    private final ConfirmationTokenRepository confirmationTokenRepository;
    private final MessageUtil messageUtil;

    public ConfirmationToken createToken(User user) {
        ConfirmationToken token = new ConfirmationToken();
        token.setToken(UUID.randomUUID().toString());
        token.setCreatedAt(LocalDateTime.now());
        token.setExpiresAt(LocalDateTime.now().plusHours(24));  // token is valid for 24 hours
        token.setUser(user);
        return confirmationTokenRepository.save(token);
    }

    public ConfirmationToken validateToken(String token, String lang) {
        return confirmationTokenRepository.findByToken(token)
                .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new IllegalArgumentException(messageUtil.getMessage("token.invalidOrExpired", lang)));
    }

    public void deleteToken(ConfirmationToken token) {
        confirmationTokenRepository.delete(token);
    }

    public void saveToken(ConfirmationToken token) {
        confirmationTokenRepository.save(token);
    }

    public void deleteAllByUser(User user) {
        confirmationTokenRepository.deleteAllByUser(user);
    }

    public Optional<ConfirmationToken> findByToken(String token) {
        return confirmationTokenRepository.findByToken(token);
    }
}