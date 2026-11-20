package com.lee.spa.auth;

import com.lee.spa.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public record Credentials(@NotBlank @Size(max=80) String username, @NotBlank @Size(max=128) String password) {
        @Override public String toString(){return "Credentials[redacted]";}
    }
    public record Wechat(@NotBlank @Size(max=256) String code) {
        @Override public String toString(){return "Wechat[redacted]";}
    }
    public record Development(@NotBlank @Size(max=40) String identity) { }
    @PostMapping("/admin-login") public Result<AuthService.Login> admin(@Valid @RequestBody Credentials input) { return Result.success(auth.adminLogin(input.username(), input.password())); }
    @PostMapping("/staff-login") public Result<AuthService.Login> staff(@Valid @RequestBody Credentials input) { return Result.success(auth.staffLogin(input.username(), input.password())); }
    @PostMapping("/wechat-login") public Result<AuthService.Login> wechat(@Valid @RequestBody Wechat input) { return Result.success(auth.wechatLogin(input.code())); }
    @PostMapping("/dev-login") public Result<AuthService.Login> dev(@Valid @RequestBody Development input) { return Result.success(auth.devLogin(input.identity())); }
    @GetMapping("/me") public Result<AuthService.User> me() { return Result.success(auth.me()); }
    @PostMapping("/logout") public Result<Void> logout(@RequestHeader("Authorization") String header) {
        AuthContext.current(); auth.logout(header.substring(7)); return Result.success();
    }
}
