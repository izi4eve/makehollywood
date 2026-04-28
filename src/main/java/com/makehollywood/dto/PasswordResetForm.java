package com.makehollywood.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PasswordResetForm {
    @NotBlank(message = "{password.notBlank}")
    @Size(min = 6, message = "{password.size}")
    private String password;

    @NotBlank(message = "{confirmedPassword.notBlank}")
    private String confirmPassword;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}