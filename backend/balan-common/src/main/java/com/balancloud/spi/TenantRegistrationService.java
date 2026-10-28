package com.balancloud.spi;

import com.balancloud.spi.dto.RegistrationApplication;
import com.balancloud.spi.dto.RegistrationReceipt;

/**
 * 运营商入驻扩展点：社区版关闭，标准版提供自助入驻与审核。
 */
public interface TenantRegistrationService {

    boolean isEnabled();

    RegistrationReceipt apply(RegistrationApplication application);
}
