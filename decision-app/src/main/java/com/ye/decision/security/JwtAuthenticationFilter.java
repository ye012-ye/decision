package com.ye.decision.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ye.decision.domain.entity.SysUser;
import com.ye.decision.mapper.SysUserMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 解析 Authorization: Bearer <jwt>，校验通过则写入 SecurityContext。
 * 无 token 或 token 非法时不抛异常，留空上下文交由 AuthenticationEntryPoint 处理。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final SysUserMapper userMapper;

    public JwtAuthenticationFilter(JwtService jwtService, SysUserMapper userMapper) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(PREFIX.length());
            try {
                String username = jwtService.parseUsername(token);
                SysUser user = findActiveUser(username);
                if (user == null) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
                CurrentUser principal = new CurrentUser(
                    user.getId(), user.getUsername(), user.getNickname(), user.getRole());
                var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority(toRoleAuthority(user.getRole()))));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private SysUser findActiveUser(String username) {
        SysUser user = userMapper.selectOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            return null;
        }
        return user;
    }

    private String toRoleAuthority(String role) {
        if (role == null || role.isBlank()) {
            return "ROLE_USER";
        }
        return role.startsWith("ROLE_") ? role : "ROLE_" + role;
    }
}
