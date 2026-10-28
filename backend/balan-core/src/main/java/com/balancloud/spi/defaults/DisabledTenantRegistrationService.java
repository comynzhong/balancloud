package com.balancloud.spi.defaults;

import com.balancloud.common.exception.ServiceException;
import com.balancloud.spi.TenantRegistrationService;
import com.balancloud.spi.dto.RegistrationApplication;
import com.balancloud.spi.dto.RegistrationReceipt;

/**
 * 社区版不开放自助入驻，运营商账号由平台管理员在后台创建。
 */
public class DisabledTenantRegistrationService implements TenantRegistrationService {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public RegistrationReceipt apply(RegistrationApplication application) {
        throw new ServiceException(403, "自助入驻未开放，请联系平台管理员开通");
    }
}
