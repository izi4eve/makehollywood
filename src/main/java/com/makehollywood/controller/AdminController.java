package com.makehollywood.controller;

import com.makehollywood.model.Role;
import com.makehollywood.model.User;
import com.makehollywood.repository.UserRepository;
import com.makehollywood.service.UserCleanupService;
import com.makehollywood.util.MessageUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    @Autowired
    private UserCleanupService userCleanupService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageUtil messageUtil;

    @GetMapping("/set-role")
    public String showRoleForm(Model model) {
        model.addAttribute("roles", Role.values());  // pass the list of roles to the model
        return "set-role";
    }

    @PostMapping("/set-role")
    public String setRole(@RequestParam String email, @RequestParam String role, Model model,
                          @RequestParam(name = "lang", required = false) String lang) {
        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException(messageUtil.getMessage("noSuchEmail", lang)));

            user.setRole(Role.valueOf(role.toUpperCase()));
            userRepository.save(user);

            model.addAttribute("message",
                    messageUtil.getMessage("changeRoleResult", lang, new Object[]{email, role}));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
        }

        model.addAttribute("roles", Role.values());  // pass the list of roles back to the model
        return "set-role";
    }

    @GetMapping("/cleanup-users")
    public String cleanupUsers(RedirectAttributes redirectAttributes,
                               @RequestParam(name = "lang", required = false) String lang) {
        userCleanupService.removeUnconfirmedUsersManually();
        redirectAttributes.addFlashAttribute("message", messageUtil.getMessage("unconfirmedEmailCleanedResult", lang));
        return "redirect:/account";
    }
}