package com.balancloud.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.balancloud.app.domain.AppUser;
import com.balancloud.app.mapper.AppUserMapper;
import com.balancloud.app.security.AppLoginUser;
import com.balancloud.app.security.AppSecurityUtils;
import com.balancloud.app.security.AppTokenService;
import com.balancloud.app.vo.AppLoginVo;
import com.balancloud.app.vo.AppUserVo;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.framework.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class AppAuthService {

    private final AppUserMapper userMapper;
    private final AppTokenService tokenService;
    private final WechatClient wechatClient;
    private final boolean devLoginEnabled;

    public AppAuthService(AppUserMapper userMapper,
                          AppTokenService tokenService,
                          WechatClient wechatClient,
                          @Value("${balan.miniapp.dev-login-enabled:false}") boolean devLoginEnabled) {
        this.userMapper = userMapper;
        this.tokenService = tokenService;
        this.wechatClient = wechatClient;
        this.devLoginEnabled = devLoginEnabled;
    }

    @Transactional
    public AppLoginVo loginByCode(String code, String nickname, String avatar) {
        WechatClient.WechatSession session = wechatClient.exchange(code);
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getOpenid, session.openid())
                .last("LIMIT 1"));
        if (user == null) {
            user = new AppUser();
            user.setOpenid(session.openid());
            user.setUnionid(session.unionid());
            user.setNickname(StringUtils.hasText(nickname) ? nickname.trim() : "微信用户");
            user.setAvatar(avatar);
            user.setStatus("0");
            userMapper.insert(user);
        }
        return issueToken(user, nickname, avatar);
    }

    @Transactional
    public AppLoginVo devLogin(String phone) {
        if (!devLoginEnabled) {
            throw new ServiceException(403, "开发登录未开启");
        }
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getPhone, phone)
                .last("LIMIT 1"));
        if (user == null) {
            user = new AppUser();
            user.setPhone(phone);
            user.setNickname("用户" + phone.substring(phone.length() - 4));
            user.setStatus("0");
            userMapper.insert(user);
        }
        return issueToken(user, null, null);
    }

    public AppUserVo me() {
        AppUser user = userMapper.selectById(AppSecurityUtils.currentUserId());
        if (user == null) {
            throw new ServiceException(401, "用户不存在");
        }
        return AppUserVo.of(user);
    }

    public void logout(String token) {
        tokenService.deleteToken(token);
    }

    private AppLoginVo issueToken(AppUser user, String nickname, String avatar) {
        if (!"0".equals(user.getStatus())) {
            throw new ServiceException(403, "账号已停用，请联系管理员");
        }
        if (StringUtils.hasText(nickname) && !nickname.trim().equals(user.getNickname())) {
            user.setNickname(nickname.trim());
        }
        if (StringUtils.hasText(avatar) && !avatar.equals(user.getAvatar())) {
            user.setAvatar(avatar);
        }
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        AppLoginUser loginUser = new AppLoginUser();
        loginUser.setTenantId(TenantContext.get());
        loginUser.setUserId(user.getId());
        loginUser.setNickname(user.getNickname());
        loginUser.setPhone(user.getPhone());
        loginUser.setAvatar(user.getAvatar());
        String token = tokenService.createToken(loginUser);
        return new AppLoginVo(token, AppUserVo.of(user));
    }
}
