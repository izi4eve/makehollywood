package com.makehollywood.controller;

import com.makehollywood.dto.PasswordChangeForm;
import com.makehollywood.dto.PasswordResetForm;
import com.makehollywood.model.Role;
import com.makehollywood.model.User;
import com.makehollywood.service.UserService;
import com.makehollywood.util.MessageUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MessageUtil messageUtil;

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute("user") User user, BindingResult result, Model model, RedirectAttributes redirectAttributes,
                               @RequestParam(name = "lang", required = false) String lang) {
        if (result.hasErrors()) {
            return "register";
        }

        if (userService.findByEmail(user.getEmail()).isPresent()) {
            model.addAttribute("emailError",
                    messageUtil.getMessage("isSuchEmail", lang));
            return "register";
        }

        user.setRole(Role.REGISTERED);
        userService.save(user, lang);

        redirectAttributes.addFlashAttribute("registrationSuccess",
                messageUtil.getMessage("thanksForRegisterCheckYourEmail", lang));
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String showLoginForm(@RequestParam(value = "error", required = false) String error, Model model, Authentication authentication,
                                @RequestParam(name = "lang", required = false) String lang) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/account";
        }

        if (error != null) {
            String email = (String) model.asMap().get("username");
            Optional<User> user = userService.findByEmail(email);
            if (user.isPresent() && !user.get().isEnabled()) {
                model.addAttribute("loginError",
                        messageUtil.getMessage("emailIsNotConfirmed", lang));
            } else {
                model.addAttribute("loginError",
                        messageUtil.getMessage("loginError", lang));
            }
        }
        return "login";
    }

    @GetMapping("/account")
    public String account(Model model, Principal principal,
                          @RequestParam(name = "lang", required = false) String lang) {
        String email = principal.getName();
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        messageUtil.getMessage("emailNotFound", lang) + " " + email));
        model.addAttribute("user", user);
        return "account";
    }

    @GetMapping("/logout")
    public String logout() {
        return "redirect:/"; // Login for logout is already configured in SecurityConfig
    }

    @GetMapping("/confirm")
    public String confirmUser(@RequestParam("token") String token, Model model,
                              @RequestParam(name = "lang", required = false) String lang) {
        try {
            userService.confirmUser(token, lang);
            model.addAttribute("confirmationMessage",
                    messageUtil.getMessage("emailConfirmed", lang));
            return "login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("confirmationMessage",
                    messageUtil.getMessage("tokenIsWrong", lang));
            return "login";
        } catch (IllegalStateException e) {
            model.addAttribute("confirmationMessage",
                    messageUtil.getMessage("accountAlreadyConfirmed", lang));
            return "login";
        }
    }

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String handleForgotPassword(@RequestParam("email") String email, Model model,
                                       @RequestParam(name = "lang", required = false) String lang) {
        Optional<User> userOptional = userService.findByEmail(email);

        if (userOptional.isPresent()) {
            userService.sendPasswordResetEmail(userOptional.get(), lang);
            model.addAttribute("message",
                    messageUtil.getMessage("recoveryEmailIsSend", lang));
        } else {
            model.addAttribute("error",
                    messageUtil.getMessage("userNotFound", lang));
        }

        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam("token") String token, Model model,
                                        @RequestParam(name = "lang", required = false) String lang) {
        if (!userService.isPasswordResetTokenValid(token, lang)) {
            model.addAttribute("error",
                    messageUtil.getMessage("oldOrWrongToken", lang));
            return "forgot-password";
        }
        model.addAttribute("token", token);
        model.addAttribute("passwordResetForm", new PasswordResetForm()); // add a form to the model
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String handleResetPassword(@Valid @ModelAttribute("passwordResetForm") PasswordResetForm form,
                                      BindingResult bindingResult,
                                      @RequestParam("token") String token,
                                      Model model,
                                      RedirectAttributes redirectAttributes,
                                      @RequestParam(name = "lang", required = false) String lang) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("token", token);
            return "reset-password";
        }

        if (!form.getPassword().equals(form.getConfirmPassword())) {
            model.addAttribute("error",
                    messageUtil.getMessage("passwordsNotMatch", lang));
            return "reset-password";
        }

        try {
            userService.resetPassword(token, form.getPassword(), lang);
            redirectAttributes.addFlashAttribute("message",
                    messageUtil.getMessage("passwordChangedSuccess", lang));
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error",
                    messageUtil.getMessage("oldOrWrongToken", lang));
            return "reset-password";
        }
    }

    @GetMapping("/account/delete")
    public String showDeleteAccountPage() {
        return "delete-account";
    }

    @PostMapping("/account/delete")
    public String deleteAccount(Principal principal, HttpServletRequest request, HttpServletResponse response) {
        String email = principal.getName();
        userService.deleteUserByEmail(email);

        SecurityContextHolder.getContext().setAuthentication(null);
        request.getSession().invalidate();

        return "redirect:/login?accountDeleted";
    }

    @GetMapping("/account/change-password")
    public String showChangePasswordForm(Model model) {
        model.addAttribute("passwordChangeForm", new PasswordChangeForm());
        return "change-password";
    }

    @PostMapping("/account/change-password")
    public String changePassword(@Valid @ModelAttribute("passwordChangeForm") PasswordChangeForm form, BindingResult bindingResult, Authentication authentication, RedirectAttributes redirectAttributes,
                                 @RequestParam(name = "lang", required = false) String lang) {
        if (bindingResult.hasErrors()) {
            return "change-password"; // go back to the password change page if there are errors
        }

        String username = authentication.getName();
        User user = userService.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException(messageUtil.getMessage("emailNotFound", lang) + " " + username));

        if (!passwordEncoder.matches(form.getCurrentPassword(), user.getPassword())) {
            redirectAttributes.addFlashAttribute("error",
                    messageUtil.getMessage("incorrectCurrentPassword", lang));
            return "redirect:/account/change-password";
        }
        if (!form.getNewPassword().equals(form.getConfirmNewPassword())) {
            redirectAttributes.addFlashAttribute("error",
                    messageUtil.getMessage("newPasswordsNotMatch", lang));
            return "redirect:/account/change-password";
        }

        userService.updatePassword(username, form.getNewPassword(), lang);
        redirectAttributes.addFlashAttribute("success",
                messageUtil.getMessage("passwordChangedSuccess", lang));
        return "redirect:/account";
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body,
                                            Authentication authentication) {
        String email = authentication.getName();
        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");

        User user = userService.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect"));
        }

        userService.updatePassword(email, newPassword, "en");
        return ResponseEntity.ok(Map.of("message", "Password changed"));
    }

    @DeleteMapping("/delete-account")
    public ResponseEntity<?> deleteAccount(Authentication authentication) {
        String email = authentication.getName();
        userService.deleteUserByEmail(email);
        return ResponseEntity.ok(Map.of("message", "Account deleted"));
    }

}