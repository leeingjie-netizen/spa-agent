package com.lee.spa.auth;

import com.lee.spa.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {
    private final AuthService auth;
    public record CreateStaff(String username, String password, Long staffId) {
        @Override public String toString(){return "CreateStaff[credentials=redacted]";}
    }
    @PostMapping("/staff") public Result<AuthService.User> create(@RequestBody CreateStaff input) {
        AuthContext.requireRole("ADMIN");
        return Result.success(auth.createStaff(input.username(), input.password(), input.staffId()));
    }
}
