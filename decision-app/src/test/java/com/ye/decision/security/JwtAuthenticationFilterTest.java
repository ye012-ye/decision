package com.ye.decision.security;

import com.ye.decision.domain.entity.SysUser;
import com.ye.decision.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService =
        new JwtService(new JwtProperties("test-secret-test-secret-test-secret-123456", 120));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenStoresCurrentUserPrincipalAndRoleAuthority() throws Exception {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectOne(any())).thenReturn(activeAdmin());
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userMapper);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken("admin"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();

            assertThat(authentication).isNotNull();
            assertThat(authentication.getName()).isEqualTo("admin");
            assertThat(authentication.getPrincipal()).isInstanceOf(CurrentUser.class);
            assertThat((CurrentUser) authentication.getPrincipal())
                .extracting(CurrentUser::id, CurrentUser::username, CurrentUser::nickname, CurrentUser::role)
                .containsExactly(1L, "admin", "管理员", "ADMIN");
            assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
        });
    }

    private SysUser activeAdmin() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setNickname("管理员");
        user.setRole("ADMIN");
        user.setStatus(1);
        return user;
    }
}
