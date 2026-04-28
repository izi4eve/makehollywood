package com.makehollywood.repository;

import com.makehollywood.model.ConfirmationToken;
import com.makehollywood.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConfirmationTokenRepository extends JpaRepository<ConfirmationToken, Long> {
    Optional<ConfirmationToken> findByToken(String token);
    void deleteAllByUser(User user);
}