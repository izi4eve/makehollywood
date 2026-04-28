package com.makehollywood.service;

import com.makehollywood.model.User;
import com.makehollywood.repository.UserRepository;
import com.makehollywood.util.LocaleContext;
import com.makehollywood.util.MessageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final MessageUtil messageUtil;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Get current locale
        String lang = LocaleContext.getLocale();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        messageUtil.getMessage("emailNotFound", lang)
                        + " " + email));

        if (!user.isEnabled()) {
            throw new DisabledException(messageUtil.getMessage("emailIsNotConfirmed", lang));
        }

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                List.of(user.getRole()) // use List.of for creating list of roles
        );
    }
}