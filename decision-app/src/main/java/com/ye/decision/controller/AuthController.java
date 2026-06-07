package com.ye.decision.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ye.decision.common.Result;
import com.ye.decision.domain.dto.LoginReq;
import com.ye.decision.domain.dto.LoginResp;
import com.ye.decision.domain.dto.MeResp;
import com.ye.decision.domain.entity.SysUser;
import com.ye.decision.mapper.SysUserMapper;
import com.ye.decision.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录与当前用户接口。/api/auth/login 放行；/api/auth/me 需登录。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(SysUserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<Result<LoginResp>> login(@Valid @RequestBody LoginReq req) {
        if (req == null){
            return ResponseEntity.status(401).body(Result.error(401, "用户名或密码为空"));
        }
        SysUser user = findByUsername(req.username());
        boolean ok = user != null
            && user.getStatus() != null && user.getStatus() == 1
            && passwordEncoder.matches(req.password(), user.getPassword());
        if (!ok) {
            return ResponseEntity.status(401).body(Result.error(401, "用户名或密码错误"));
        }
        String token = jwtService.generateToken(user.getUsername());
        return ResponseEntity.ok(Result.ok(new LoginResp(token, user.getUsername(), user.getNickname())));
    }

    @GetMapping("/me")
    public Result<MeResp> me(Authentication authentication) {
        SysUser user = findByUsername(authentication.getName());
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        return Result.ok(new MeResp(user.getUsername(), user.getNickname(), user.getRole()));
    }

    private SysUser findByUsername(String username) {
        if (username.isBlank()){
            return null;
        }
        return userMapper.selectOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    }
}
