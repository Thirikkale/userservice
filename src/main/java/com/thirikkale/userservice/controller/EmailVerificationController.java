package com.thirikkale.userservice.controller;

import com.thirikkale.userservice.dto.response.AdminRegistrationResponse;
import com.thirikkale.userservice.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for email verification page
 */
@Controller
@RequestMapping("/api/v1/auth/admin")
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationController {

    private final AdminService adminService;

    /**
     * Email verification page (returns HTML)
     */
    @GetMapping("/verify-email-page")
    public String verifyEmailPage(@RequestParam("token") String token, Model model) {
        log.info("Email verification page accessed");

        try {
            AdminRegistrationResponse response = adminService.verifyEmail(token);

            model.addAttribute("success", true);
            model.addAttribute("message", response.getMessage());
            model.addAttribute("adminName", response.getFirstName() + " " + response.getLastName());
            model.addAttribute("email", response.getEmail());

        } catch (Exception e) {
            log.error("Email verification failed", e);

            model.addAttribute("success", false);
            model.addAttribute("message", e.getMessage());
        }

        return "email-verification";
    }
}
