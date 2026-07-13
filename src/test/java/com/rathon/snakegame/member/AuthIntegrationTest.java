package com.rathon.snakegame.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증·게스트 제한 통합 테스트 — 게스트는 정적 리소스·카탈로그만 접근 가능하고
 * 회원 API(내 정보·구매·장착)는 401로 차단되는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("게스트: 게임 클라이언트 정적 리소스는 로그인 없이 접근 가능")
    void guest_canAccessStaticResources() throws Exception {
        mockMvc.perform(get("/index.html")).andExpect(status().isOk());
        mockMvc.perform(get("/game.js")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("게스트: 스킨 카탈로그는 공개 — 타 플레이어 스킨 렌더링에 필요")
    void guest_canReadSkinCatalog() throws Exception {
        mockMvc.perform(get("/api/skins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("default"));
    }

    @Test
    @DisplayName("게스트 제한: 내 정보·구매·장착은 401로 거부")
    void guest_isRejectedFromMemberApis() throws Exception {
        mockMvc.perform(get("/api/members/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/skins/crimson/purchase")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/skins/crimson/equip")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("가입→로그인→내 정보: 기본 스킨 보유·크레딧 0으로 시작한다")
    void signupLoginAndMe() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester1\",\"password\":\"password123\",\"nickname\":\"테스터\"}"))
                .andExpect(status().isCreated());

        MockHttpSession session = login("tester1", "password123");

        mockMvc.perform(get("/api/members/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("tester1"))
                .andExpect(jsonPath("$.credit").value(0))
                .andExpect(jsonPath("$.equippedSkinId").value("default"))
                .andExpect(jsonPath("$.ownedSkinIds[0]").value("default"));
    }

    @Test
    @DisplayName("가입 거부: 중복 아이디 409, 8자 미만 비밀번호 400")
    void signup_rejectsDuplicateAndWeakPassword() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dupuser\",\"password\":\"password123\",\"nickname\":\"먼저\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dupuser\",\"password\":\"password123\",\"nickname\":\"나중\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"weakpass\",\"password\":\"short\",\"nickname\":\"약함\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("가입 거부: 잘못된 JSON 본문은 400과 오류 메시지로 응답한다 (500·401 마스킹 방지)")
    void signup_rejectsMalformedJsonWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("로그인 거부: 잘못된 비밀번호는 401 단일 메시지")
    void login_rejectsBadCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester2\",\"password\":\"password123\",\"nickname\":\"테스터2\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester2\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** 로그인 후 인증된 세션을 반환한다 */
    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }
}
