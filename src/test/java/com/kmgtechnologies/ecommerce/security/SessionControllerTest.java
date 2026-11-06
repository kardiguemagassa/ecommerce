package com.kmgtechnologies.ecommerce.security;

import com.kmgtechnologies.ecommerce.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionController.class)
@Import({SecurityConfig.class, BffTestConfiguration.class})
@ActiveProfiles("test")
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private CustomerService customerService;

    @Test
    void anonymousBrowserIsToldItIsNotSignedIn_andGetsTheCsrfCookie() throws Exception {
        mockMvc.perform(get("/api/me/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.roles").isEmpty())
                .andExpect(jsonPath("$.accountUrl").doesNotExist())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));

        verifyNoInteractions(customerService);
    }

    @Test
    void signedInBrowserGetsItsNameEmailRolesAndPhoto_neverAToken() throws Exception {
        when(customerService.findAvatarUrl("alice")).thenReturn("http://media.test/avatars/alice.jpg");

        mockMvc.perform(get("/api/me/session").with(oidcLogin()
                        .idToken(token -> token.subject("alice")
                                .claim("name", "Alice Martin")
                                .claim("email", "alice@example.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                                new SimpleGrantedAuthority("SCOPE_openid"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.name").value("Alice Martin"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.avatarUrl").value("http://media.test/avatars/alice.jpg"))
                .andExpect(jsonPath("$.accountUrl").value("http://localhost:8180/realms/ecommerce/account"));
    }
}
