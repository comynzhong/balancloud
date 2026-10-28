package com.balancloud.system.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.common.utils.IpUtils;
import com.balancloud.common.utils.SecurityUtils;
import com.balancloud.framework.security.LoginUser;
import com.balancloud.framework.security.TokenService;
import com.balancloud.framework.tenant.TenantContext;
import com.balancloud.system.captcha.SliderCaptchaService;
import com.balancloud.system.domain.SysLogininfor;
import com.balancloud.system.domain.SysUser;
import com.balancloud.system.dto.LoginBody;
import com.balancloud.system.mapper.SysLogininforMapper;
import com.balancloud.system.mapper.SysMenuMapper;
import com.balancloud.system.mapper.SysRoleMapper;
import com.balancloud.system.mapper.SysUserMapper;
import com.balancloud.system.mapper.SysUserStationMapper;
import com.balancloud.system.vo.UserVo;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String ALL_PERMISSION = "*:*:*";
    private static final String ADMIN_ROLE = "admin";

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SysLogininforMapper logininforMapper;
    private final SysUserStationMapper userStationMapper;
    private final SysMenuService menuService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final SliderCaptchaService sliderCaptchaService;

    public AuthService(SysUserMapper userMapper,
                       SysRoleMapper roleMapper,
                       SysMenuMapper menuMapper,
                       SysLogininforMapper logininforMapper,
                       SysUserStationMapper userStationMapper,
                       SysMenuService menuService,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       SliderCaptchaService sliderCaptchaService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.logininforMapper = logininforMapper;
        this.userStationMapper = userStationMapper;
        this.menuService = menuService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.sliderCaptchaService = sliderCaptchaService;
    }

    public String login(LoginBody body, HttpServletRequest request) {
        String username = body.getUsername().trim();
        // 先校验验证码：未通过时不做账号密码比对，避免被直接暴力尝试
        sliderCaptchaService.consume(body.getCaptchaToken());
        SysUser user = userMapper.selectByUserName(username);
        if (user == null || !passwordEncoder.matches(body.getPassword(), user.getPassword())) {
            recordLogin(username, request, "1", "用户名或密码错误");
            throw new ServiceException("用户名或密码错误");
        }
        if (!"0".equals(user.getStatus())) {
            recordLogin(username, request, "1", "账号已停用");
            throw new ServiceException("账号已停用，请联系管理员");
        }

        List<String> roleKeys = roleMapper.selectRoleKeysByUserId(user.getUserId());
        LoginUser loginUser = buildLoginUser(user, roleKeys);
        String token = tokenService.createToken(loginUser);
        updateLoginInfo(user.getUserId(), IpUtils.getClientIp(request));
        recordLogin(username, request, "0", "登录成功");
        return token;
    }

    /**
     * 机器请求的主体装载（§60.2）。调用前必须先把租户写进 {@link TenantContext}：
     * {@code sys_user} 的查询被 core 的租户拦截器限定，「查不到人」本身就是跨租户绑定的拒绝，
     * 不需要另写一条租户比对。
     *
     * <p>三种情况一律拒绝、不降级放行：账号已删除（逻辑删除让 {@code selectById} 直接查不到）、
     * 已停用、仍处初密未改。最后一条与 §58 的 428 同源——身份未经本人确认的账号不拿去对外读数。
     */
    public LoginUser machineSubject(Long userId) {
        if (userId == null) {
            throw new ServiceException(4012, "该密钥未绑定后台账号");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException(4012, "绑定的后台账号不存在或不属于本运营商");
        }
        if (!"0".equals(user.getStatus())) {
            throw new ServiceException(4012, "绑定的后台账号已停用，密钥随之失效");
        }
        if ("1".equals(user.getPwdResetRequired())) {
            throw new ServiceException(4012, "绑定的后台账号尚未修改初始密码");
        }
        return buildLoginUser(user, roleMapper.selectRoleKeysByUserId(user.getUserId()));
    }

    private LoginUser buildLoginUser(SysUser user, List<String> roleKeys) {
        LoginUser loginUser = new LoginUser();
        // 账号查询本身已被租户拦截器限定，登录成功的租户就是查询所在租户。
        loginUser.setTenantId(TenantContext.get());
        loginUser.setUserId(user.getUserId());
        loginUser.setUsername(user.getUserName());
        loginUser.setNickName(user.getNickName());
        loginUser.setRoles(new LinkedHashSet<>(roleKeys));
        if (roleKeys.contains(ADMIN_ROLE)) {
            loginUser.setPermissions(new LinkedHashSet<>(Set.of(ALL_PERMISSION)));
        } else {
            loginUser.setPermissions(new LinkedHashSet<>(menuMapper.selectPermsByUserId(user.getUserId())));
        }
        loginUser.setStationIds(new LinkedHashSet<>(userStationMapper.selectStationIdsByUserId(user.getUserId())));
        loginUser.setPwdResetRequired("1".equals(user.getPwdResetRequired()));
        return loginUser;
    }

    /**
     * 用户自助改密：校验旧密码与强度后更新，并重建当前会话（其它设备上的旧会话一并失效）。
     */
    public String changePassword(String oldPassword, String newPassword) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null) {
            throw new ServiceException(401, "认证失败，无法访问系统资源");
        }
        SysUser user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new ServiceException(401, "用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new ServiceException("旧密码不正确");
        }
        validateNewPassword(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new ServiceException("新密码不能与旧密码相同");
        }

        SysUser update = new SysUser();
        update.setUserId(user.getUserId());
        update.setPassword(passwordEncoder.encode(newPassword));
        update.setPwdUpdateDate(LocalDateTime.now());
        update.setPwdResetRequired("0");
        userMapper.updateById(update);

        tokenService.evictUser(user.getUserId());
        user.setPwdResetRequired("0");
        List<String> roleKeys = roleMapper.selectRoleKeysByUserId(user.getUserId());
        return tokenService.createToken(buildLoginUser(user, roleKeys));
    }

    private void validateNewPassword(String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new ServiceException("新密码不能为空");
        }
        if (newPassword.length() < 8 || newPassword.length() > 30) {
            throw new ServiceException("新密码长度需为 8-30 位");
        }
        if (newPassword.chars().anyMatch(Character::isWhitespace)) {
            throw new ServiceException("新密码不能包含空格");
        }
        boolean hasLetter = newPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = newPassword.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new ServiceException("新密码需同时包含字母和数字");
        }
    }

    public AjaxResult getInfo() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null) {
            throw new ServiceException(401, "认证失败，无法访问系统资源");
        }
        SysUser user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new ServiceException(401, "用户不存在");
        }
        UserVo userVo = new UserVo();
        userVo.setUserId(user.getUserId());
        userVo.setUserName(user.getUserName());
        userVo.setNickName(user.getNickName());
        userVo.setEmail(user.getEmail());
        userVo.setPhoneNumber(user.getPhoneNumber());
        userVo.setAvatar(user.getAvatar());
        userVo.setStatus(user.getStatus());
        userVo.setCreateTime(user.getCreateTime());
        return AjaxResult.success()
                .put("user", userVo)
                .put("roles", loginUser.getRoles())
                .put("permissions", loginUser.getPermissions())
                .put("pwdResetRequired", loginUser.isPwdResetRequired());
    }

    public List<?> getRouters() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null) {
            throw new ServiceException(401, "认证失败，无法访问系统资源");
        }
        boolean admin = loginUser.getRoles().contains(ADMIN_ROLE);
        return menuService.getRouters(loginUser.getUserId(), admin);
    }

    public void logout(String token) {
        tokenService.deleteToken(token);
    }

    private void updateLoginInfo(Long userId, String ip) {
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getUserId, userId)
                .set(SysUser::getLoginIp, ip)
                .set(SysUser::getLoginDate, LocalDateTime.now()));
    }

    private void recordLogin(String username, HttpServletRequest request, String status, String message) {
        try {
            SysLogininfor info = new SysLogininfor();
            info.setUserName(username);
            info.setIpaddr(IpUtils.getClientIp(request));
            info.setLoginLocation("内网");
            info.setBrowser(IpUtils.getBrowser(request.getHeader("User-Agent")));
            info.setOs(IpUtils.getOs(request.getHeader("User-Agent")));
            info.setStatus(status);
            info.setMsg(message);
            info.setLoginTime(LocalDateTime.now());
            logininforMapper.insert(info);
        } catch (Exception e) {
            log.warn("Failed to record login audit: {}", e.getMessage());
        }
    }
}
