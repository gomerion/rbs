package ru.ssau.rbs.controller;

import java.net.InetAddress;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import ru.ssau.rbs.domain.enums.UserRole;
import ru.ssau.rbs.dto.security.LoginRequest;
import ru.ssau.rbs.dto.security.RegisterRequest;
import ru.ssau.rbs.dto.security.TokenResponse;
import ru.ssau.rbs.dto.security.UserResponse;
import ru.ssau.rbs.exception.ApiException;
import ru.ssau.rbs.exception.ApiExceptionHandler;
import ru.ssau.rbs.security.UserPrincipal;
import ru.ssau.rbs.service.AuthService;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Юнит-тесты AuthController: сервис замокан, Spring-контекст и база не поднимаются.
 */
class AuthControllerTest {

    private static final String VALID_REGISTER_BODY = """
            {"email":"ivanov@ssau.ru","password":"correct horse battery"}
            """;

    private static final String VALID_LOGIN_BODY = """
            {"email":"ivanov@ssau.ru","password":"correct horse battery"}
            """;

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registerReturnsCreatedUser() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new UserResponse(42L, "ivanov@ssau.ru", UserRole.EMPLOYEE));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.email").value("ivanov@ssau.ru"))
                .andExpect(jsonPath("$.role").value("employee"));

        ArgumentCaptor<RegisterRequest> captor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(authService).register(captor.capture());
        assertEquals("ivanov@ssau.ru", captor.getValue().getEmail());
        assertEquals("correct horse battery", captor.getValue().getPassword());
    }

    @Test
    void registerRejectsTooShortPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ivanov@ssau.ru\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.request_id").exists());

        verify(authService, never()).register(any());
    }

    @Test
    void registerRejectsMalformedEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"correct horse battery\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));

        verify(authService, never()).register(any());
    }

    @Test
    void registerPropagatesEmailConflict() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(ApiException.emailTaken("ivanov@ssau.ru"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_taken"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void loginReturnsAccessToken() throws Exception {
        when(authService.login(any(LoginRequest.class), any(InetAddress.class)))
                .thenReturn(new TokenResponse("jwt-token", "Bearer", 3600L));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("jwt-token"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600));
    }

    @Test
    void loginPassesRemoteAddressAsClientIp() throws Exception {
        when(authService.login(any(LoginRequest.class), any(InetAddress.class)))
                .thenReturn(new TokenResponse("jwt-token", "Bearer", 3600L));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.10");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LOGIN_BODY))
                .andExpect(status().isOk());

        verify(authService).login(any(LoginRequest.class), eq(InetAddress.getByName("192.0.2.10")));
    }

    @Test
    void loginPrefersXForwardedForHeader() throws Exception {
        when(authService.login(any(LoginRequest.class), any(InetAddress.class)))
                .thenReturn(new TokenResponse("jwt-token", "Bearer", 3600L));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LOGIN_BODY))
                .andExpect(status().isOk());

        verify(authService).login(any(LoginRequest.class), eq(InetAddress.getByName("203.0.113.7")));
    }

    @Test
    void loginReturnsUnauthenticatedProblemForBadCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class), any(InetAddress.class)))
                .thenThrow(ApiException.unauthenticated("Неверный email или пароль"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LOGIN_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"))
                .andExpect(jsonPath("$.detail").value("Неверный email или пароль"));
    }

    @Test
    void loginReturnsTooManyRequestsWithRetryAfter() throws Exception {
        when(authService.login(any(LoginRequest.class), any(InetAddress.class)))
                .thenThrow(ApiException.rateLimited(Duration.ofSeconds(90)));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LOGIN_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "90"))
                .andExpect(jsonPath("$.code").value("rate_limited"));
    }

    @Test
    void meReturnsAuthenticatedUser() throws Exception {
        authenticate(new UserPrincipal(7L, "ivanov@ssau.ru", "hash", UserRole.MANAGER, 3L));

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.email").value("ivanov@ssau.ru"))
                .andExpect(jsonPath("$.role").value("manager"))
                .andExpect(jsonPath("$.department_id").value(3));
    }

    @Test
    void meReturnsNullDepartmentWhenUserHasNoDepartment() throws Exception {
        authenticate(new UserPrincipal(8L, "no-department@ssau.ru", "hash", UserRole.EMPLOYEE, null));

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department_id").value(nullValue()));
    }

    @Test
    void logoutRevokesTokenFromBearerHeader() throws Exception {
        authenticate(new UserPrincipal(7L, "ivanov@ssau.ru", "hash", UserRole.EMPLOYEE, null));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                .andExpect(status().isNoContent());

        verify(authService).logout("abc.def.ghi");
    }

    @Test
    void logoutRequiresAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).logout(any());
    }

    private static void authenticate(UserPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
