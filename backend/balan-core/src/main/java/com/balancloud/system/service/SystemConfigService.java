package com.balancloud.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.common.utils.SecurityUtils;
import com.balancloud.framework.cluster.ClusterNode;
import com.balancloud.framework.cluster.ClusterProperties;
import com.balancloud.framework.tenant.TenantContext;
import com.balancloud.spi.TenantConfigSource;
import com.balancloud.system.domain.SysConfig;
import com.balancloud.system.domain.SysConfigGroup;
import com.balancloud.system.dto.ConfigItemRequest;
import com.balancloud.system.mapper.SysConfigGroupMapper;
import com.balancloud.system.mapper.SysConfigMapper;
import com.balancloud.system.vo.ConfigGroupVo;
import com.balancloud.system.vo.ConfigItemVo;
import com.balancloud.system.vo.ConfigOptionVo;
import com.balancloud.system.vo.PublicConfigVo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 平台级系统配置。所有键全局唯一、不参与租户隔离；改完即刷新内存缓存，接口无需重启。
 */
@Service
public class SystemConfigService {

    private static final Logger log = LoggerFactory.getLogger(SystemConfigService.class);

    public static final String KEY_MULTI_TENANT = "tenant.multiTenant.enabled";
    public static final String KEY_PAYMENT_ENABLED = "payment.enabled";
    public static final String KEY_PAYMENT_MODE = "payment.mode";
    public static final String KEY_CAPTCHA_ENABLED = "security.captcha.enabled";
    public static final String KEY_RATE_LIMIT_ENABLED = "security.rateLimit.enabled";
    public static final String KEY_RATE_LIMIT_WINDOW = "security.rateLimit.windowSeconds";
    public static final String KEY_RATE_LIMIT_MAX = "security.rateLimit.maxRequests";
    public static final String KEY_RATE_LIMIT_LOGIN_MAX = "security.rateLimit.loginMaxRequests";
    public static final String KEY_RATE_LIMIT_TRUST_FORWARDED = "security.rateLimit.trustForwarded";
    public static final String KEY_MASK_ENABLED = "security.mask.enabled";
    public static final String KEY_MASK_EXPORT = "security.mask.export";
    public static final String KEY_DEFAULT_DURATION = "miniapp.defaultMaxDurationMinutes";
    public static final String KEY_DEFAULT_AMOUNT = "miniapp.defaultChargeLimitAmount";
    public static final String KEY_MAX_ACTIVE_ORDERS = "miniapp.maxActiveOrdersPerUser";
    public static final String KEY_SIMULATOR_ENABLED = "operations.simulator.enabled";
    public static final String KEY_REFUND_ENABLED = "operations.refund.enabled";
    public static final String KEY_MAINTENANCE_ENABLED = "operations.maintenance.enabled";
    public static final String KEY_MAINTENANCE_MESSAGE = "operations.maintenance.message";
    public static final String KEY_OCCUPANCY_ENABLED = "operations.occupancy.enabled";
    public static final String KEY_OCCUPANCY_PRICE = "operations.occupancy.pricePerHour";
    public static final String KEY_OCCUPANCY_GRACE = "operations.occupancy.graceMinutes";
    public static final String KEY_OCCUPANCY_MAX_FEE = "operations.occupancy.maxFee";
    public static final String KEY_BRAND_NAME = "brand.platformName";
    public static final String KEY_BRAND_SUBTITLE = "brand.platformSubtitle";
    public static final String KEY_BRAND_PHONE = "brand.servicePhone";

    private static final String TYPE_BOOLEAN = "BOOLEAN";
    private static final String TYPE_INT = "INT";
    private static final String TYPE_DECIMAL = "DECIMAL";
    private static final String TYPE_STRING = "STRING";
    private static final String TYPE_ENUM = "ENUM";

    private static final Set<String> VALUE_TYPES =
            Set.of(TYPE_BOOLEAN, TYPE_INT, TYPE_DECIMAL, TYPE_STRING, TYPE_ENUM);

    private static final Pattern EXTENSION_KEY = Pattern.compile("^ext\\.[A-Za-z0-9_.]+$");

    private static final int MAX_VALUE_LENGTH = 500;

    private static final Map<String, String> DEFAULTS;

    static {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put(KEY_MULTI_TENANT, "false");
        defaults.put(KEY_PAYMENT_ENABLED, "true");
        defaults.put(KEY_PAYMENT_MODE, "mock");
        defaults.put(KEY_CAPTCHA_ENABLED, "true");
        defaults.put(KEY_RATE_LIMIT_ENABLED, "true");
        defaults.put(KEY_RATE_LIMIT_WINDOW, "60");
        defaults.put(KEY_RATE_LIMIT_MAX, "600");
        defaults.put(KEY_RATE_LIMIT_LOGIN_MAX, "20");
        defaults.put(KEY_RATE_LIMIT_TRUST_FORWARDED, "false");
        defaults.put(KEY_MASK_ENABLED, "true");
        defaults.put(KEY_MASK_EXPORT, "true");
        defaults.put(KEY_DEFAULT_DURATION, "480");
        defaults.put(KEY_DEFAULT_AMOUNT, "50");
        defaults.put(KEY_MAX_ACTIVE_ORDERS, "1");
        defaults.put(KEY_SIMULATOR_ENABLED, "true");
        defaults.put(KEY_REFUND_ENABLED, "true");
        defaults.put(KEY_MAINTENANCE_ENABLED, "false");
        defaults.put(KEY_MAINTENANCE_MESSAGE, "系统维护中，暂停新充电业务，请稍后再试。");
        defaults.put(KEY_OCCUPANCY_ENABLED, "false");
        defaults.put(KEY_OCCUPANCY_PRICE, "2.00");
        defaults.put(KEY_OCCUPANCY_GRACE, "15");
        defaults.put(KEY_OCCUPANCY_MAX_FEE, "20.00");
        defaults.put(KEY_BRAND_NAME, "衡电云控");
        defaults.put(KEY_BRAND_SUBTITLE, "充电桩场站智能运营平台");
        defaults.put(KEY_BRAND_PHONE, "400-000-0000");
        DEFAULTS = Collections.unmodifiableMap(defaults);
    }

    private volatile Map<String, SysConfig> definitions = Map.of();
    private volatile Map<String, String> values = Map.of();
    private volatile List<SysConfigGroup> groupDefinitions = List.of();

    private final SysConfigMapper configMapper;
    private final SysConfigGroupMapper groupMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final ClusterProperties clusterProperties;
    private final ClusterNode clusterNode;
    private final TenantConfigSource tenantConfigSource;
    private final String edition;

    public SystemConfigService(SysConfigMapper configMapper, SysConfigGroupMapper groupMapper,
                               ObjectMapper objectMapper, StringRedisTemplate redisTemplate,
                               ClusterProperties clusterProperties, ClusterNode clusterNode,
                               TenantConfigSource tenantConfigSource,
                               @Value("${balan.edition:community}") String edition) {
        this.configMapper = configMapper;
        this.groupMapper = groupMapper;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.clusterProperties = clusterProperties;
        this.clusterNode = clusterNode;
        this.tenantConfigSource = tenantConfigSource;
        this.edition = edition;
    }

    /**
     * 等 Flyway 完成后再加载，避免启动早期读到空表。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        reload();
    }

    public void reload() {
        List<SysConfig> rows = configMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                .orderByAsc(SysConfig::getSort)
                .orderByAsc(SysConfig::getConfigId));        Map<String, SysConfig> nextDefinitions = new LinkedHashMap<>();
        Map<String, String> nextValues = new LinkedHashMap<>();
        for (SysConfig row : rows) {
            nextDefinitions.put(row.getConfigKey(), row);
            if ("1".equals(row.getStatus())) {
                continue;
            }
            nextValues.put(row.getConfigKey(), row.getConfigValue());
        }
        List<SysConfigGroup> nextGroups = groupMapper.selectList(new LambdaQueryWrapper<SysConfigGroup>()
                .orderByAsc(SysConfigGroup::getSort)
                .orderByAsc(SysConfigGroup::getGroupKey));
        this.definitions = Collections.unmodifiableMap(nextDefinitions);
        this.values = Collections.unmodifiableMap(nextValues);
        this.groupDefinitions = Collections.unmodifiableList(nextGroups);
        log.info("系统配置已加载 {} 项 / {} 组", nextValues.size(), nextGroups.size());
    }

    /**
     * 写入路径用：刷新本机缓存并通知其他实例重载。
     * 缓存是 JVM 内的，不广播就会出现「同一个开关在不同实例上取值不同」——
     * 占位费、维护模式、支付模式这类按实例漂移的开关尤其危险。
     * 广播安排在事务提交之后，否则其他实例可能先重载读到旧值。
     */
    private void reloadAndNotify() {
        reload();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notifyPeers();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifyPeers();
            }
        });
    }

    private void notifyPeers() {
        try {
            redisTemplate.convertAndSend(clusterProperties.getConfigChannel(), clusterNode.id());
        } catch (Exception e) {
            log.warn("系统配置失效广播失败：{}", e.getMessage());
        }
    }

    /** 其他实例的写入通知；自己发的消息忽略，免得白读一次库。 */
    public void reloadFromPeer(String origin) {
        if (clusterNode.id().equals(origin)) {
            return;
        }
        reload();
    }

    public String getString(String key, String fallback) {
        if (!values.containsKey(key)) {
            return fallback;
        }
        return raw(key);
    }

    public boolean getBoolean(String key, boolean fallback) {
        String value = raw(key);
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim());
    }

    public int getInt(String key, int fallback) {
        String value = raw(key);
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("系统配置 {} 不是合法整数：{}，使用默认值 {}", key, value, fallback);
            return fallback;
        }
    }

    public BigDecimal getDecimal(String key, BigDecimal fallback) {
        String value = raw(key);
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            log.warn("系统配置 {} 不是合法数字：{}，使用默认值 {}", key, value, fallback);
            return fallback;
        }
    }

    /**
     * 取值出口：租户级覆盖（标准版按租户配置改写）只在这里生效，
     * 社区版的 {@link TenantConfigSource} 恒返回 null，读到的仍是全局值。
     */
    private String raw(String key) {
        String override = tenantConfigSource.overrideValue(TenantContext.get(), key);
        return StringUtils.hasText(override) ? override : values.get(key);
    }

    /**
     * 多租户隔离始终生效，该开关只影响产品表面（登录页标识等）。
     */
    public boolean isMultiTenantEnabled() {
        return getBoolean(KEY_MULTI_TENANT, false);
    }

    public boolean isPaymentEnabled() {
        return getBoolean(KEY_PAYMENT_ENABLED, true);
    }

    public String getPaymentMode() {
        return getString(KEY_PAYMENT_MODE, "mock");
    }

    public boolean isMockPayment() {
        return "mock".equals(getPaymentMode());
    }

    public boolean isCaptchaEnabled() {
        return getBoolean(KEY_CAPTCHA_ENABLED, true);
    }

    public boolean isRateLimitEnabled() {
        return getBoolean(KEY_RATE_LIMIT_ENABLED, true);
    }

    public int getRateLimitWindowSeconds() {
        return Math.max(1, getInt(KEY_RATE_LIMIT_WINDOW, 60));
    }

    public int getRateLimitMaxRequests() {
        return Math.max(1, getInt(KEY_RATE_LIMIT_MAX, 600));
    }

    public int getRateLimitLoginMaxRequests() {
        return Math.max(1, getInt(KEY_RATE_LIMIT_LOGIN_MAX, 20));
    }

    public boolean isRateLimitTrustForwarded() {
        return getBoolean(KEY_RATE_LIMIT_TRUST_FORWARDED, false);
    }

    public boolean isSensitiveMaskEnabled() {
        return getBoolean(KEY_MASK_ENABLED, true);
    }

    public boolean isSensitiveExportMasked() {
        return getBoolean(KEY_MASK_EXPORT, true);
    }

    public int getDefaultMaxDurationMinutes() {
        return getInt(KEY_DEFAULT_DURATION, 480);
    }

    public BigDecimal getDefaultChargeLimit() {
        return getDecimal(KEY_DEFAULT_AMOUNT, new BigDecimal("50"));
    }

    public int getMaxActiveOrders() {
        return getInt(KEY_MAX_ACTIVE_ORDERS, 1);
    }

    public boolean isSimulatorEnabled() {
        return getBoolean(KEY_SIMULATOR_ENABLED, true);
    }

    public boolean isRefundEnabled() {
        return getBoolean(KEY_REFUND_ENABLED, true);
    }

    public boolean isMaintenanceEnabled() {
        return getBoolean(KEY_MAINTENANCE_ENABLED, false);
    }

    public String getMaintenanceMessage() {
        return getString(KEY_MAINTENANCE_MESSAGE, DEFAULTS.get(KEY_MAINTENANCE_MESSAGE));
    }

    public boolean isOccupancyEnabled() {
        return getBoolean(KEY_OCCUPANCY_ENABLED, false);
    }

    public BigDecimal getOccupancyPricePerHour() {
        return getDecimal(KEY_OCCUPANCY_PRICE, new BigDecimal("2.00"));
    }

    public int getOccupancyGraceMinutes() {
        return getInt(KEY_OCCUPANCY_GRACE, 15);
    }

    public BigDecimal getOccupancyMaxFee() {
        return getDecimal(KEY_OCCUPANCY_MAX_FEE, new BigDecimal("20.00"));
    }

    public String getPlatformName() {
        return getString(KEY_BRAND_NAME, DEFAULTS.get(KEY_BRAND_NAME));
    }

    public String getPlatformSubtitle() {
        return getString(KEY_BRAND_SUBTITLE, DEFAULTS.get(KEY_BRAND_SUBTITLE));
    }

    public String getServicePhone() {
        return getString(KEY_BRAND_PHONE, DEFAULTS.get(KEY_BRAND_PHONE));
    }

    public List<ConfigGroupVo> groups() {
        Map<String, ConfigGroupVo> grouped = new LinkedHashMap<>();
        for (SysConfigGroup definition : groupDefinitions) {
            ConfigGroupVo group = new ConfigGroupVo();
            group.setGroup(definition.getGroupKey());
            group.setGroupName(definition.getGroupName());
            group.setItems(new ArrayList<>());
            grouped.put(definition.getGroupKey(), group);
        }
        for (SysConfig definition : definitions.values()) {
            ConfigGroupVo group = grouped.computeIfAbsent(definition.getConfigGroup(), key -> {
                ConfigGroupVo fallback = new ConfigGroupVo();
                fallback.setGroup(key);
                fallback.setGroupName(key);
                fallback.setItems(new ArrayList<>());
                return fallback;
            });
            group.getItems().add(toItem(definition));
        }
        return grouped.values().stream()
                .filter(group -> !group.getItems().isEmpty())
                .toList();
    }

    /**
     * 校验入口开放给平台侧的租户级覆盖（§36.2 登记的 core 追加项）：覆盖值必须按同一套类型/区间/枚举规则过，
     * 但只读定义、不写库——全局值仍是缺省值与校验依据。
     */
    public String validateValue(String configKey, Object raw) {
        SysConfig definition = definitions.get(configKey);
        if (definition == null) {
            throw new ServiceException(400, "未知的配置项：" + configKey);
        }
        if (!"1".equals(definition.getEditable())) {
            throw new ServiceException(400, "配置「" + definition.getConfigName() + "」不允许修改");
        }
        return validate(definition, raw);
    }

    /**
     * 扩展项统一读取入口：只返回界面新增的 ext.* 项，内置项请用各自的公开接口或业务常量。
     */
    public Map<String, String> extensionValues() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, SysConfig> entry : definitions.entrySet()) {
            if ("0".equals(entry.getValue().getIsSystem())) {
                result.put(entry.getKey(), effectiveValue(entry.getKey()));
            }
        }
        return result;
    }

    public PublicConfigVo publicConfig() {
        PublicConfigVo vo = new PublicConfigVo();
        PublicConfigVo.Brand brand = new PublicConfigVo.Brand();
        brand.setName(getPlatformName());
        brand.setSubtitle(getPlatformSubtitle());
        brand.setServicePhone(getServicePhone());
        vo.setBrand(brand);
        vo.setCaptchaEnabled(isCaptchaEnabled());
        vo.setMultiTenantEnabled(isMultiTenantEnabled());
        vo.setPaymentEnabled(isPaymentEnabled());
        vo.setPaymentMode(getPaymentMode());

        PublicConfigVo.Maintenance maintenance = new PublicConfigVo.Maintenance();
        maintenance.setEnabled(isMaintenanceEnabled());
        maintenance.setMessage(getMaintenanceMessage());
        vo.setMaintenance(maintenance);

        PublicConfigVo.Miniapp miniapp = new PublicConfigVo.Miniapp();
        miniapp.setDefaultMaxDurationMinutes(getDefaultMaxDurationMinutes());
        miniapp.setDefaultChargeLimitAmount(getDefaultChargeLimit().stripTrailingZeros().toPlainString());
        miniapp.setMaxActiveOrdersPerUser(getMaxActiveOrders());
        vo.setMiniapp(miniapp);

        // 标准版专属能力由平台模块提供；小程序读这份开关隐藏入口，避免社区版出现点了就 404 的按钮。
        boolean standard = "standard".equalsIgnoreCase(edition);
        vo.setEdition(standard ? "standard" : "community");
        PublicConfigVo.Features features = new PublicConfigVo.Features();
        features.setWallet(standard);
        features.setBenefits(standard);
        features.setReservation(standard);
        features.setPlatform(standard);
        vo.setFeatures(features);
        return vo;
    }

    @Transactional
    public void update(Map<String, Object> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            throw new ServiceException(400, "没有需要保存的配置");
        }
        Map<String, String> effective = new LinkedHashMap<>();
        for (String key : definitions.keySet()) {
            effective.put(key, effectiveValue(key));
        }
        Map<String, String> updates = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            SysConfig definition = definitions.get(entry.getKey());
            if (definition == null) {
                throw new ServiceException(400, "未知的配置项：" + entry.getKey());
            }
            if (!"1".equals(definition.getEditable())) {
                throw new ServiceException(400, "配置「" + definition.getConfigName() + "」不允许修改");
            }
            String value = validate(definition, entry.getValue());
            updates.put(entry.getKey(), value);
            effective.put(entry.getKey(), value);
        }

        if (asBoolean(effective.get(KEY_MAINTENANCE_ENABLED), false)
                && !StringUtils.hasText(effective.get(KEY_MAINTENANCE_MESSAGE))) {
            throw new ServiceException(400, "开启维护模式时必须填写维护提示");
        }

        String operator = SecurityUtils.getUsername();
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String key = entry.getKey();
            String newValue = entry.getValue();
            String oldValue = effectiveValue(key);
            if (Objects.equals(oldValue, newValue)) {
                continue;
            }
            SysConfig patch = new SysConfig();
            patch.setConfigId(definitions.get(key).getConfigId());
            patch.setConfigValue(newValue);
            patch.setUpdateBy(StringUtils.hasText(operator) ? operator : "system");
            patch.setUpdateTime(LocalDateTime.now());
            configMapper.updateById(patch);
            log.info("系统配置变更 {}：{} -> {}（操作人 {}）", key, oldValue, newValue, operator);
        }
        reloadAndNotify();
    }

    @Transactional
    public void createItem(ConfigItemRequest request) {
        String key = request.getConfigKey() == null ? "" : request.getConfigKey().trim();
        if (!EXTENSION_KEY.matcher(key).matches()) {
            throw new ServiceException(400, "扩展配置键必须以 ext. 开头，且只能包含字母、数字、下划线与点");
        }
        if (definitions.containsKey(key)) {
            throw new ServiceException(400, "配置键已存在：" + key);
        }
        String groupKey = requireGroup(request.getConfigGroup());
        String type = normalizeType(request.getValueType());
        String optionsJson = serializeOptions(type, request.getOptions());
        BigDecimal minValue = type.equals(TYPE_INT) || type.equals(TYPE_DECIMAL) ? request.getMinValue() : null;
        BigDecimal maxValue = type.equals(TYPE_INT) || type.equals(TYPE_DECIMAL) ? request.getMaxValue() : null;
        checkBounds(minValue, maxValue);
        Integer maxLength = type.equals(TYPE_STRING) ? normalizeMaxLength(request.getMaxLength()) : null;

        SysConfig definition = new SysConfig();
        definition.setConfigKey(key);
        definition.setConfigName(request.getConfigName().trim());
        definition.setValueType(type);
        definition.setConfigGroup(groupKey);
        definition.setOptionsJson(optionsJson);
        definition.setMinValue(minValue);
        definition.setMaxValue(maxValue);
        definition.setMaxLength(maxLength);

        String raw = StringUtils.hasText(request.getConfigValue())
                ? request.getConfigValue()
                : defaultRaw(type, optionsJson, minValue);
        definition.setConfigValue(validate(definition, raw));
        definition.setEditable("1");
        definition.setIsSystem("0");
        definition.setStatus("0");
        definition.setSort(nextSort(groupKey));
        definition.setRemark(request.getRemark());
        definition.setCreateBy(operator());
        definition.setCreateTime(LocalDateTime.now());
        definition.setUpdateBy(operator());
        definition.setUpdateTime(LocalDateTime.now());
        configMapper.insert(definition);
        log.info("新增扩展配置 {}（分组 {}，操作人 {}）", key, groupKey, operator());
        reloadAndNotify();
    }

    @Transactional
    public void updateItem(String configKey, ConfigItemRequest request) {
        SysConfig existing = definitions.get(configKey);
        if (existing == null) {
            throw new ServiceException(404, "配置项不存在：" + configKey);
        }
        if ("1".equals(existing.getIsSystem())) {
            throw new ServiceException(400, "内置配置项由版本预置，不能修改结构");
        }
        String groupKey = requireGroup(request.getConfigGroup());
        String type = normalizeType(request.getValueType());
        String optionsJson = serializeOptions(type, request.getOptions());
        BigDecimal minValue = type.equals(TYPE_INT) || type.equals(TYPE_DECIMAL) ? request.getMinValue() : null;
        BigDecimal maxValue = type.equals(TYPE_INT) || type.equals(TYPE_DECIMAL) ? request.getMaxValue() : null;
        checkBounds(minValue, maxValue);
        Integer maxLength = type.equals(TYPE_STRING) ? normalizeMaxLength(request.getMaxLength()) : null;

        SysConfig definition = new SysConfig();
        definition.setConfigKey(configKey);
        definition.setConfigName(request.getConfigName().trim());
        definition.setValueType(type);
        definition.setOptionsJson(optionsJson);
        definition.setMinValue(minValue);
        definition.setMaxValue(maxValue);
        definition.setMaxLength(maxLength);

        String raw = StringUtils.hasText(request.getConfigValue())
                ? request.getConfigValue()
                : defaultRaw(type, optionsJson, minValue);
        String value = validate(definition, raw);

        Integer sort = existing.getSort() == null ? nextSort(groupKey) : existing.getSort();
        if (!groupKey.equals(existing.getConfigGroup())) {
            sort = nextSort(groupKey);
        }
        LambdaUpdateWrapper<SysConfig> update = new LambdaUpdateWrapper<SysConfig>()
                .eq(SysConfig::getConfigId, existing.getConfigId())
                .set(SysConfig::getConfigName, definition.getConfigName())
                .set(SysConfig::getConfigValue, value)
                .set(SysConfig::getValueType, type)
                .set(SysConfig::getConfigGroup, groupKey)
                .set(SysConfig::getSort, sort)
                .set(SysConfig::getOptionsJson, optionsJson)
                .set(SysConfig::getMinValue, minValue)
                .set(SysConfig::getMaxValue, maxValue)
                .set(SysConfig::getMaxLength, maxLength)
                .set(SysConfig::getRemark, request.getRemark())
                .set(SysConfig::getUpdateBy, operator())
                .set(SysConfig::getUpdateTime, LocalDateTime.now());
        configMapper.update(null, update);
        log.info("修改扩展配置 {}（分组 {}，操作人 {}）", configKey, groupKey, operator());
        reloadAndNotify();
    }

    @Transactional
    public void deleteItem(String configKey) {
        SysConfig existing = definitions.get(configKey);
        if (existing == null) {
            throw new ServiceException(404, "配置项不存在：" + configKey);
        }
        if ("1".equals(existing.getIsSystem())) {
            throw new ServiceException(400, "内置配置项由版本预置，不能删除");
        }
        configMapper.deleteById(existing.getConfigId());
        log.info("删除扩展配置 {}（操作人 {}）", configKey, operator());
        reloadAndNotify();
    }

    private ConfigItemVo toItem(SysConfig definition) {
        ConfigItemVo item = new ConfigItemVo();
        item.setConfigKey(definition.getConfigKey());
        item.setConfigName(definition.getConfigName());
        item.setConfigValue(effectiveValue(definition.getConfigKey()));
        item.setValueType(definition.getValueType());
        item.setOptions(parseOptions(definition.getOptionsJson()));
        item.setMinValue(definition.getMinValue());
        item.setMaxValue(definition.getMaxValue());
        item.setMaxLength(definition.getMaxLength());
        item.setEditable("1".equals(definition.getEditable()));
        item.setBuiltin("1".equals(definition.getIsSystem()));
        item.setRemark(definition.getRemark());
        return item;
    }

    private String requireGroup(String groupKey) {
        String key = groupKey == null ? "" : groupKey.trim();
        boolean exists = groupDefinitions.stream().anyMatch(group -> group.getGroupKey().equals(key));
        if (!exists) {
            throw new ServiceException(400, "配置分组不存在：" + key);
        }
        return key;
    }

    private String normalizeType(String raw) {
        String type = raw == null ? "" : raw.trim().toUpperCase();
        if (!VALUE_TYPES.contains(type)) {
            throw new ServiceException(400, "不支持的配置类型：" + raw);
        }
        return type;
    }

    private String serializeOptions(String type, List<ConfigOptionVo> options) {
        if (!TYPE_ENUM.equals(type)) {
            return null;
        }
        if (options == null || options.isEmpty()) {
            throw new ServiceException(400, "枚举配置至少需要一个选项");
        }
        Set<String> seen = new HashSet<>();
        List<ConfigOptionVo> normalized = new ArrayList<>();
        for (ConfigOptionVo option : options) {
            if (option == null || !StringUtils.hasText(option.getLabel()) || !StringUtils.hasText(option.getValue())) {
                throw new ServiceException(400, "枚举选项的显示名称与取值都不能为空");
            }
            ConfigOptionVo item = new ConfigOptionVo();
            item.setLabel(option.getLabel().trim());
            item.setValue(option.getValue().trim());
            item.setDisabled(Boolean.TRUE.equals(option.getDisabled()));
            if (!seen.add(item.getValue())) {
                throw new ServiceException(400, "枚举取值不能重复：" + item.getValue());
            }
            normalized.add(item);
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new ServiceException(400, "枚举选项格式不正确");
        }
    }

    private void checkBounds(BigDecimal minValue, BigDecimal maxValue) {
        if (minValue != null && maxValue != null && minValue.compareTo(maxValue) > 0) {
            throw new ServiceException(400, "数值下限不能大于上限");
        }
    }

    private Integer normalizeMaxLength(Integer maxLength) {
        if (maxLength == null) {
            return null;
        }
        if (maxLength < 1 || maxLength > MAX_VALUE_LENGTH) {
            throw new ServiceException(400, "字符串长度上限应在 1-" + MAX_VALUE_LENGTH + " 之间");
        }
        return maxLength;
    }

    private String defaultRaw(String type, String optionsJson, BigDecimal minValue) {
        return switch (type) {
            case TYPE_BOOLEAN -> "false";
            case TYPE_INT, TYPE_DECIMAL -> minValue == null ? "0" : strip(minValue);
            case TYPE_ENUM -> {
                List<ConfigOptionVo> options = parseOptions(optionsJson);
                yield options.isEmpty() ? "" : options.get(0).getValue();
            }
            default -> "";
        };
    }

    private int nextSort(String groupKey) {
        int max = 0;
        for (SysConfig definition : definitions.values()) {
            if (groupKey.equals(definition.getConfigGroup()) && definition.getSort() != null) {
                max = Math.max(max, definition.getSort());
            }
        }
        return max + 1;
    }

    private String operator() {
        String username = SecurityUtils.getUsername();
        return StringUtils.hasText(username) ? username : "system";
    }

    private String effectiveValue(String key) {
        if (values.containsKey(key)) {
            return values.get(key);
        }
        return DEFAULTS.getOrDefault(key, "");
    }

    private String validate(SysConfig definition, Object raw) {
        if (raw == null) {
            throw new ServiceException(400, "配置「" + definition.getConfigName() + "」不能为空");
        }
        String value = normalize(raw);
        String type = definition.getValueType() == null ? TYPE_STRING : definition.getValueType();
        switch (type) {
            case TYPE_BOOLEAN -> {
                if (!"true".equals(value) && !"false".equals(value)) {
                    throw new ServiceException(400, "配置「" + definition.getConfigName() + "」只能为 true 或 false");
                }
            }
            case TYPE_INT -> {
                int number;
                try {
                    number = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new ServiceException(400, "配置「" + definition.getConfigName() + "」必须是整数");
                }
                checkRange(definition, BigDecimal.valueOf(number));
                value = String.valueOf(number);
            }
            case TYPE_DECIMAL -> {
                BigDecimal number;
                try {
                    number = new BigDecimal(value);
                } catch (NumberFormatException e) {
                    throw new ServiceException(400, "配置「" + definition.getConfigName() + "」必须是数字");
                }
                checkRange(definition, number);
                value = number.stripTrailingZeros().toPlainString();
            }
            case TYPE_ENUM -> {
                String enumValue = value;
                List<ConfigOptionVo> options = parseOptions(definition.getOptionsJson());
                ConfigOptionVo matched = options.stream()
                        .filter(option -> Objects.equals(option.getValue(), enumValue))
                        .findFirst()
                        .orElseThrow(() -> new ServiceException(400,
                                "配置「" + definition.getConfigName() + "」不支持取值：" + enumValue));
                if (Boolean.TRUE.equals(matched.getDisabled())) {
                    throw new ServiceException(400, "配置「" + definition.getConfigName()
                            + "」的选项「" + matched.getLabel() + "」暂不可用");
                }
            }
            default -> {
                if (value.length() > MAX_VALUE_LENGTH) {
                    throw new ServiceException(400, "配置「" + definition.getConfigName() + "」不能超过 "
                            + MAX_VALUE_LENGTH + " 个字符");
                }
                if (definition.getMaxLength() != null && value.length() > definition.getMaxLength()) {
                    throw new ServiceException(400, "配置「" + definition.getConfigName() + "」不能超过 "
                            + definition.getMaxLength() + " 个字符");
                }
            }
        }
        if (KEY_BRAND_NAME.equals(definition.getConfigKey()) && !StringUtils.hasText(value)) {
            throw new ServiceException(400, "平台名称不能为空");
        }
        return value;
    }

    private void checkRange(SysConfig definition, BigDecimal number) {
        if (definition.getMinValue() != null && number.compareTo(definition.getMinValue()) < 0) {
            throw new ServiceException(400, "配置「" + definition.getConfigName() + "」不能小于 "
                    + strip(definition.getMinValue()));
        }
        if (definition.getMaxValue() != null && number.compareTo(definition.getMaxValue()) > 0) {
            throw new ServiceException(400, "配置「" + definition.getConfigName() + "」不能大于 "
                    + strip(definition.getMaxValue()));
        }
    }

    private String normalize(Object raw) {
        if (raw instanceof Boolean bool) {
            return String.valueOf(bool);
        }
        if (raw instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (raw instanceof Number number) {
            return number.toString();
        }
        return String.valueOf(raw).trim();
    }

    private boolean asBoolean(String value, boolean fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim());
    }

    private String strip(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private List<ConfigOptionVo> parseOptions(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ConfigOptionVo>>() {
            });
        } catch (Exception e) {
            log.warn("配置选项 JSON 解析失败：{}", json, e);
            return List.of();
        }
    }
}
