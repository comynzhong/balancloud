package com.balancloud.system.service;

import com.balancloud.common.exception.ServiceException;
import com.balancloud.framework.security.LoginUser;
import com.balancloud.framework.security.TokenService;
import com.balancloud.framework.tenant.TenantContext;
import com.balancloud.system.captcha.SliderCaptchaService;
import com.balancloud.system.domain.SysUser;
import com.balancloud.system.mapper.SysLogininforMapper;
import com.balancloud.system.mapper.SysMenuMapper;
import com.balancloud.system.mapper.SysRoleMapper;
import com.balancloud.system.mapper.SysUserMapper;
import com.balancloud.system.mapper.SysUserStationMapper;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 机器主体装载（§60.2）：三种情况一律拒绝、不降级放行。
 *
 * <p>「查不到」同时覆盖不存在、已删除与属于别的运营商——{@code sys_user} 的查询被 core 的
 * 租户拦截器限定，所以跨租户绑定在调用方先落定租户之后自然查不到，不需要另写一条租户比对。
 */
class MachineSubjectTest {

    private SysUserMapper userMapper;
    private SysRoleMapper roleMapper;
    private SysMenuMapper menuMapper;
    private SysUserStationMapper userStationMapper;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        roleMapper = mock(SysRoleMapper.class);
        menuMapper = mock(SysMenuMapper.class);
        userStationMapper = mock(SysUserStationMapper.class);
        service = new AuthService(userMapper, roleMapper, menuMapper, mock(SysLogininforMapper.class),
                userStationMapper, mock(SysMenuService.class), mock(PasswordEncoder.class),
                mock(TokenService.class), mock(SliderCaptchaService.class));
        // 过滤器在调用本方法前已经把密钥所属租户写进上下文，这里按既有口径模拟一次。
        TenantContext.use(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void nullUserIdRejectedAsUnbound() {
        assertReject(() -> service.machineSubject(null), "该密钥未绑定后台账号");
        verify(userMapper, never()).selectById(anyLong());
    }

    @Test
    void missingOrForeignTenantAccountRejected() {
        when(userMapper.selectById(88L)).thenReturn(null);
        assertReject(() -> service.machineSubject(88L), "绑定的后台账号不存在或不属于本运营商");
    }

    @Test
    void disabledAccountRejected() {
        when(userMapper.selectById(3L)).thenReturn(account("1", "0"));
        assertReject(() -> service.machineSubject(3L), "绑定的后台账号已停用，密钥随之失效");
    }

    @Test
    void accountStillOnInitialPasswordRejected() {
        // 与 §58 的 428 同源：身份未经本人确认的账号不拿去对外读数。
        when(userMapper.selectById(3L)).thenReturn(account("0", "1"));
        assertReject(() -> service.machineSubject(3L), "绑定的后台账号尚未修改初始密码");
    }

    @Test
    void enabledAccountLoadsItsOwnRolesAndStationScope() {
        SysUser user = account("0", "0");
        when(userMapper.selectById(3L)).thenReturn(user);
        when(roleMapper.selectRoleKeysByUserId(3L)).thenReturn(List.of("ops"));
        when(menuMapper.selectPermsByUserId(3L)).thenReturn(List.of("charger:list", "alert:list"));
        when(userStationMapper.selectStationIdsByUserId(3L)).thenReturn(List.of(11L, 12L));

        LoginUser loginUser = service.machineSubject(3L);

        assertThat(loginUser.getUserId()).isEqualTo(3L);
        assertThat(loginUser.getUsername()).isEqualTo("ops01");
        assertThat(loginUser.getTenantId()).isEqualTo(7L);
        assertThat(loginUser.getRoles()).containsExactly("ops");
        assertThat(loginUser.getPermissions()).containsExactly("charger:list", "alert:list");
        // 场站范围就是 Agent 的可见范围：这条等式成立，§55 红线 2「只有建议权」才有落点。
        assertThat(loginUser.getStationIds()).containsExactly(11L, 12L);
        assertThat(loginUser.isPwdResetRequired()).isFalse();
    }

    @Test
    void adminAccountGetsTheWildcardPermission() {
        when(userMapper.selectById(3L)).thenReturn(account("0", "0"));
        when(roleMapper.selectRoleKeysByUserId(3L)).thenReturn(List.of("admin"));
        when(userStationMapper.selectStationIdsByUserId(3L)).thenReturn(List.of());

        LoginUser loginUser = service.machineSubject(3L);

        assertThat(loginUser.getPermissions()).containsExactly("*:*:*");
        // 非 admin 才会去查 sys_user_menu；admin 直接给通配，与登录链路同一口径。
        verify(menuMapper, never()).selectPermsByUserId(anyLong());
    }

    private SysUser account(String status, String pwdResetRequired) {
        SysUser user = new SysUser();
        user.setUserId(3L);
        user.setUserName("ops01");
        user.setNickName("现场运维");
        user.setStatus(status);
        user.setPwdResetRequired(pwdResetRequired);
        return user;
    }

    private void assertReject(ThrowableAssert.ThrowingCallable call, String message) {
        assertThatThrownBy(call)
                .isInstanceOf(ServiceException.class)
                .hasMessage(message)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(4012);
    }
}
