package com.makehollywood.service;

import com.makehollywood.model.User;
import com.makehollywood.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserCleanupService {

    private final UserRepository userRepository;

    @Transactional
//    @Scheduled(cron = "0 0 * * * *")  // Run every hour
    @Scheduled(cron = "0 0 0 * * *")  // <<<<<<< Once a day at 0:00
//    @Scheduled(cron = "0 0 3 * * *")  // Once a day at 3 am
//    @Scheduled(cron = "0 0 0 * * MON")   // Once a week at midnight on Monday
//    @Scheduled(cron = "0 0 12 * * SUN")  // Once a week at noon on Sunday
    public void removeUnconfirmedUsers() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        List<User> usersToDelete = userRepository.findByEnabledFalseAndCreatedAtBefore(threshold);

        for (User user : usersToDelete) {
            userRepository.delete(user);
        }
    }

    @Transactional
    public void removeUnconfirmedUsersManually() {
        removeUnconfirmedUsers();
    }
}