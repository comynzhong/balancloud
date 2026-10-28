package com.balancloud.system.service;

import com.balancloud.framework.security.LoginUser;
import com.balancloud.framework.security.TokenService;
import com.balancloud.framework.tenant.mapper.TenantDirectoryMapper;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.system.domain.SysUser;
import com.balancloud.system.dto.ProfileUpdateRequest;
import com.balancloud.system.mapper.SysRoleMapper;
import com.balancloud.system.mapper.SysUserMapper;
import com.balancloud.system.mapper.SysUserStationMapper;
import com.balancloud.system.mapper.SysUserRoleMapper;
import com.balancloud.system.vo.ProfileVo;
import com.balancloud.system.vo.UserRoleRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 个人中心只作用于当前会话自己的账号：读出来带归属运营商，写回去只碰 sys_user 真正有的三列。
 */
class SysUserProfileTest {

    private SysUserMapper userMapper;
    private SysUserStationMapper userStationMapper;
    private TenantDirectoryMapper tenantDirectoryMapper;
    private TokenService tokenService;
    private SysUserService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        userStationMapper = mock(SysUserStationMapper.class);
        tenantDirectoryMapper = mock(TenantDirectoryMapper.class);
        tokenService = mock(TokenService.class);
        service = new SysUserService(userMapper, mock(SysUserRoleMapper.class), userStationMapper,
                mock(SysRoleMapper.class), mock(StationMapper.class), mock(PasswordEncoder.class),
                tokenService, tenantDirectoryMapper);

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(7L);
        loginUser.setUsername("ops01");
        loginUser.setTenantId(3L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void profileReturnsOwnAccountWithTenantName() {
        SysUser user = new SysUser();
        user.setUserId(7L);
        user.setUserName("ops01");
        user.setNickName("场站运营");
        user.setPhoneNumber("13800000000");
        when(userMapper.selectById(7L)).thenReturn(user);

        UserRoleRow role = new UserRoleRow();
        role.setRoleId(2L);
        role.setRoleName("场站运营");
        when(userStationMapper.selectStationIdsByUserId(7L)).thenReturn(List.of(11L));
        when(tenantDirectoryMapper.selectTenantNameById(3L)).thenReturn("华东运营商");

        ProfileVo vo = service.profile();

        assertThat(vo.getUser().getUserId()).isEqualTo(7L);
        assertThat(vo.getUser().getUserName()).isEqualTo("ops01");
        assertThat(vo.getUser().getStationIds()).containsExactly(11L);
        assertThat(vo.getTenantName()).isEqualTo("华东运营商");
        verify(userMapper).selectById(7L);
    }

    @Test
    void updateProfileOnlyWritesSelfEditableColumns() {
        when(userMapper.selectById(7L)).thenReturn(account());

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setNickName("  张三  ");
        request.setEmail(null);
        request.setPhoneNumber("");
        service.updateProfile(request);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        SysUser saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getNickName()).isEqualTo("张三");
        assertThat(saved.getEmail()).isEmpty();
        assertThat(saved.getPhoneNumber()).isEmpty();
        assertThat(saved.getStatus()).isNull();
        assertThat(saved.getPassword()).isNull();
        // 只改资料不动授权范围，不能把人踢下线
        verify(tokenService, never()).evictUser(7L);
    }

    private SysUser account() {
        SysUser user = new SysUser();
        user.setUserId(7L);
        user.setUserName("ops01");
        user.setStatus("0");
        return user;
    }
}
