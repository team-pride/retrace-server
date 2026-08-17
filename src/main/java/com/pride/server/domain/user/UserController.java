package com.pride.server.domain.user;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @PostMapping
    public User create() {
        User user = new User();
        return userRepository.save(user);
    }

    @GetMapping
    public List<User> getAll() {
        return userRepository.findAll();
    }
}