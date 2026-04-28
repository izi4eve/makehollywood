package com.makehollywood.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class MessageUtil {

    @Autowired
    private MessageSource messageSource;

    public String getMessage(String key, String lang, Object[] params) {
        Locale locale = (lang != null && !lang.isEmpty()) ? Locale.of(lang) : Locale.getDefault();
        return messageSource.getMessage(key, params, locale);
    }

    public String getMessage(String key, String lang) {
        Locale locale = (lang != null && !lang.isEmpty()) ? Locale.of(lang) : Locale.getDefault();
        return messageSource.getMessage(key, null, locale);
    }

    public String getMessage(String key) {
        return getMessage(key, null);
    }
}