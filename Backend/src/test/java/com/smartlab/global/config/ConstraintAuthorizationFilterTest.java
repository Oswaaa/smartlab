package com.smartlab.global.config;

import com.smartlab.management.service.db.user.CurrentUserPermissionService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ConstraintAuthorizationFilterTest {

    @Test
    void taskModelUsesTaskViewPermission() throws Exception {
        CurrentUserPermissionService permissions = mock(CurrentUserPermissionService.class);
        FilterChain chain = mock(FilterChain.class);
        ConstraintAuthorizationFilter filter = new ConstraintAuthorizationFilter(permissions);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/constraint/model/task/12");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(permissions).require("task", "view");
        verify(chain).doFilter(request, response);
    }

    @Test
    void violationLogsAreExternallyReadOnly() throws Exception {
        CurrentUserPermissionService permissions = mock(CurrentUserPermissionService.class);
        FilterChain chain = mock(FilterChain.class);
        ConstraintAuthorizationFilter filter = new ConstraintAuthorizationFilter(permissions);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/constraint/violation");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(405, response.getStatus());
        verify(permissions, never()).require("violation_log", "view");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void corsPreflightPassesThroughWithoutPermissionLookup() throws Exception {
        CurrentUserPermissionService permissions = mock(CurrentUserPermissionService.class);
        FilterChain chain = mock(FilterChain.class);
        ConstraintAuthorizationFilter filter = new ConstraintAuthorizationFilter(permissions);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "OPTIONS", "/api/constraint/rule");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(permissions, never()).require("constraint_rule", "view");
    }
}
