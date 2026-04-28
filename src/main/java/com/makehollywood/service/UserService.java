package com.makehollywood.service;

import com.makehollywood.model.ConfirmationToken;
import com.makehollywood.model.User;
import com.makehollywood.repository.UserRepository;
import com.makehollywood.util.MessageUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ConfirmationTokenService tokenService;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final UserCleanupService userCleanupService;
    private final MessageUtil messageUtil;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional
    public User save(User user, String lang) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user = userRepository.save(user);
        ConfirmationToken token = tokenService.createToken(user);
        sendConfirmationEmail(user.getEmail(), token.getToken(), lang);
        return user;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public void updatePassword(String email, String newPassword, String lang) {
        User user = findByEmail(email).orElseThrow(() -> new UsernameNotFoundException(
                messageUtil.getMessage("emailNotFound", lang)
                        + " " + email));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private void sendConfirmationEmail(String email, String token, String lang) {
        String confirmationUrl = frontendUrl + "/confirm?token=" + token;
        sendEmail(email,
                messageUtil.getMessage("registrationConfirm", lang),
                messageUtil.getMessage("followLinkForRegistration", lang)
                        + " " + confirmationUrl);
    }

    @Transactional
    public void confirmUser(String token, String lang) {
        // Сначала ищем токен
        Optional<ConfirmationToken> optToken = tokenService.findByToken(token);

        if (optToken.isEmpty()) {
            // Токена нет — возможно уже подтверждён, проверяем
            throw new IllegalStateException("Token already used or expired");
        }

        ConfirmationToken confirmationToken = optToken.get();
        if (confirmationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(messageUtil.getMessage("token.invalidOrExpired", lang));
        }

        User user = confirmationToken.getUser();
        if (user.isEnabled()) {
            throw new IllegalStateException(messageUtil.getMessage("accountAlreadyConfirmed", lang));
        }
        user.setEnabled(true);
        userRepository.save(user);
        tokenService.deleteToken(confirmationToken);
    }

    public void cleanupUnconfirmedUsers() {
        userCleanupService.removeUnconfirmedUsersManually();
    }

    public void sendPasswordResetEmail(User user, String lang) {
        String token = UUID.randomUUID().toString();
        savePasswordResetToken(user, token);
        String resetUrl = frontendUrl + "/reset-password?token=" + token;
        sendEmail(user.getEmail(),
                messageUtil.getMessage("passwordReset", lang),
                messageUtil.getMessage("followLinkForPasswordReset", lang)
                        + " " + resetUrl);
    }

    @Transactional
    public void resetPassword(String token, String newPassword, String lang) {
        ConfirmationToken confirmationToken = tokenService.validateToken(token, lang);
        User user = confirmationToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenService.deleteToken(confirmationToken);
    }

    public boolean isPasswordResetTokenValid(String token, String lang) {
        return tokenService.validateToken(token, lang) != null;
    }

    private void savePasswordResetToken(User user, String token) {
        ConfirmationToken confirmationToken = new ConfirmationToken(token, user);
        tokenService.saveToken(confirmationToken);
    }

    private void sendEmail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    @Transactional
    public void deleteUserByEmail(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            tokenService.deleteAllByUser(user);
            userRepository.delete(user);
        });
    }
}