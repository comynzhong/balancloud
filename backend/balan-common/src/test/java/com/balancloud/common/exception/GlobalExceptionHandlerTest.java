package com.balancloud.common.exception;

import com.balancloud.common.core.AjaxResult;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private static final NoResourceFoundException NOT_FOUND =
            new NoResourceFoundException(null, "/api/v1/platform/tenant/list");

    private AjaxResult handle(String edition, String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return new GlobalExceptionHandler(edition).handleNotFound(NOT_FOUND, request);
    }

    @Test
    void platformApiOnCommunityShellExplainsTheEdition() {
        AjaxResult result = handle("community", "/api/v1/platform/tenant/list");
        assertThat(result.get("code")).isEqualTo(404);
        assertThat(result.get("msg")).isEqualTo("该接口属标准版功能，当前社区版未包含");
    }

    @Test
    void otherUnknownPathsFallBackToPlainNotFound() {
        AjaxResult result = handle("community", "/api/v1/nope/xyz");
        assertThat(result.get("code")).isEqualTo(404);
        assertThat(result.get("msg")).isEqualTo("接口不存在：/api/v1/nope/xyz");
    }

    /** 标准版壳里平台接口是真实存在的，未命中只能按普通 404 报，不能误导成版本问题。 */
    @Test
    void standardShellNeverClaimsEditionDifference() {
        assertThat(handle("standard", "/api/v1/platform/tenant/nope").get("msg"))
                .isEqualTo("接口不存在：/api/v1/platform/tenant/nope");
    }
}
