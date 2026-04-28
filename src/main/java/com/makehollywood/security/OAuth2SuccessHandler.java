package com.makehollywood.security;

import com.makehollywood.model.User;
import com.makehollywood.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        User user = userRepository.findByEmail(email).orElseThrow();

        // Собираем Authentication-like объект для генерации токена
        var authToken = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                email,
                null,
                List.of(new SimpleGrantedAuthority(user.getRole().getAuthority()))
        );

        String accessToken = tokenProvider.generateAccessToken(authToken);
        String refreshToken = tokenProvider.generateRefreshToken(email);

        // Редиректим на React с токенами в параметрах
        String redirectUrl = frontendUrl + "/oauth/callback"
                + "?accessToken=" + accessToken
                + "&refreshToken=" + refreshToken
                + "&email=" + email
                + "&role=" + user.getRole().getAuthority()
                + "&provider=" + user.getProvider();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}