package com.balancloud.framework.tenant;

import com.balancloud.framework.tenant.mapper.TenantDirectoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 当前有哪些租户需要被照顾。定时任务与启动自愈按这里的列表循环，所以它必须便宜、不能抛。
 *
 * <p>查不到任何租户时返回默认租户：单租户部署即使 sys_tenant 没种子也照常工作，
 * 这也让「锁键是否带租户段」的判断有一个稳定的依据。
 */
@Component
public class TenantRegistry {

    private static final Logger log = LoggerFactory.getLogger(TenantRegistry.class);
    private static final long CACHE_TTL_MS = 30_000L;

    private static final List<Long> DEFAULT_ONLY = List.of(TenantContext.DEFAULT_TENANT_ID);

    private final TenantDirectoryMapper directoryMapper;

    private volatile List<Long> cached = DEFAULT_ONLY;
    private volatile long loadedAt;

    public TenantRegistry(TenantDirectoryMapper directoryMapper) {
        this.directoryMapper = directoryMapper;
    }

    public List<Long> activeTenantIds() {
        long now = System.currentTimeMillis();
        if (now - loadedAt < CACHE_TTL_MS) {
            return cached;
        }
        cached = load();
        loadedAt = now;
        return cached;
    }

    /** 新建租户后立刻让它进入循环，不等缓存过期。 */
    public void refresh() {
        cached = load();
        loadedAt = System.currentTimeMillis();
    }

    /**
     * 租户编码换租户号，解析不到返回 {@code null}。
     *
     * <p>调用方（登录入口）必须把它当错误处理，不能落回默认租户：
     * 落回去就是「用租户 2 的编码登进了租户 1」。
     */
    public Long resolveTenantId(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            return null;
        }
        return directoryMapper.selectTenantIdByCode(tenantCode.trim());
    }

    public int activeCount() {
        return activeTenantIds().size();
    }

    private List<Long> load() {
        try {
            return normalize(directoryMapper.selectActiveTenantIds());
        } catch (Exception e) {
            log.warn("Tenant directory unavailable, using default tenant only: {}", e.getMessage());
            return DEFAULT_ONLY;
        }
    }

    private List<Long> normalize(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return DEFAULT_ONLY;
        }
        Set<Long> distinct = new LinkedHashSet<>(ids);
        distinct.remove(null);
        return distinct.isEmpty() ? DEFAULT_ONLY : List.copyOf(distinct);
    }
}
