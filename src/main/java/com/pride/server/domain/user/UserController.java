package com.pride.server.domain.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "User", description = "사용자 관련 API")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @Operation(summary = "게스트 사용자 생성", description = "로그인 없이 익명 사용자를 생성합니다. 프론트에서 앱 최초 진입 시 자동 호출하는 용도입니다.")
    @PostMapping
    public User create() {
        User user = new User();
        return userRepository.save(user);
    }

    @Operation(summary = "사용자 목록 조회", description = "테스트/개발용 API입니다. 생성된 모든 사용자를 조회합니다.")
    @GetMapping
    public List<User> getAll() {
        return userRepository.findAll();
    }
}