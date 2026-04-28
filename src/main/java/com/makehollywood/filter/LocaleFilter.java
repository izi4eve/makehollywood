package com.makehollywood.filter;

import com.makehollywood.util.LocaleContext;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;

public class LocaleFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // Extract language parameter from request
        String lang = request.getParameter("lang");

        // Set locale in LocaleContext
        LocaleContext.setLocale(lang != null ? lang : Locale.getDefault().getLanguage());

        try {
            // Continuation of the filter chain
            filterChain.doFilter(request, response);
        } finally {
            // Cleanup after request processing
            LocaleContext.clear();
        }
    }
}