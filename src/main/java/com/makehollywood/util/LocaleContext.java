package com.makehollywood.util;

public class LocaleContext {

    // ThreadLocal for saving of locale
    private static final ThreadLocal<String> userLocale = new ThreadLocal<>();

    // Method for saving ofr locale
    public static void setLocale(String locale) {
        userLocale.set(locale);
    }

    // Method for getting of current locale
    public static String getLocale() {
        return userLocale.get();
    }

    // Method for cleaning of locale
    public static void clear() {
        userLocale.remove();
    }
}