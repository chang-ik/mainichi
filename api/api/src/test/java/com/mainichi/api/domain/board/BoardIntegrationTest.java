package com.mainichi.api.domain.board;

import com.jayway.jsonpath.JsonPath;
import com.mainichi.api.domain.board.repository.CommentRepository;
import com.mainichi.api.domain.board.repository.PostRepository;
import com.mainichi.api.domain.user.entity.User;
import com.mainichi.api.domain.user.repository.UserRepository;
import com.mainichi.api.global.common.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 단계별 게시판: 글·댓글 쓰기, 단계 분리, 작성자 권한, 페이지, 탈퇴 정리 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BoardIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private User alice;
    private String aliceBearer;
    private String bobBearer;

    @BeforeEach
    void setUp() {
        // 테스트 DB 는 테스트끼리 공유되므로 게시판은 비우고 시작한다
        commentRepository.deleteAll();
        postRepository.deleteAll();
        alice = newUser("앨리스");
        aliceBearer = bearer(alice);
        bobBearer = bearer(newUser("밥"));
    }

    private User newUser(String nickname) {
        User user = userRepository.save(User.builder().provider("kakao").socialId(UUID.randomUUID().toString()).build());
        user.updateProfile(nickname, null);
        return userRepository.save(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
    }

    private ResultActions write(String bearer, int level, String title, String content) throws Exception {
        return mockMvc.perform(post("/board/{level}/posts", level)
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"" + title + "\", \"content\": \"" + content + "\"}"));
    }

    private ResultActions list(int level, int page) throws Exception {
        return mockMvc.perform(get("/board/{level}/posts", level).param("page", String.valueOf(page))
                .header("Authorization", aliceBearer));
    }

    private ResultActions comment(String bearer, String postId, String content) throws Exception {
        return mockMvc.perform(post("/board/posts/{id}/comments", postId)
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"" + content + "\"}"));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static String level(int level) {
        return String.valueOf(level);
    }

    @Test
    @DisplayName("글은 쓴 단계 게시판에만 보이고, 목록에 작성자·댓글 수가 나온다")
    void levelsAreSeparate() throws Exception {
        String n3 = id(write(aliceBearer, 3, "N3 질문", "〜ようにする 쓰임이 궁금해요")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.level").value(3))
                .andExpect(jsonPath("$.author").value("앨리스"))
                .andExpect(jsonPath("$.mine").value(true)));
        write(bobBearer, 5, "N5 인사", "안녕하세요").andExpect(status().isCreated());
        comment(bobBearer, n3, "목표를 위해 노력할 때 써요").andExpect(status().isCreated());

        list(3, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value(3))
                .andExpect(jsonPath("$.totalPosts").value(1))
                .andExpect(jsonPath("$.posts[0].title").value("N3 질문"))
                .andExpect(jsonPath("$.posts[0].author").value("앨리스"))
                .andExpect(jsonPath("$.posts[0].commentCount").value(1));
        list(5, 0).andExpect(jsonPath("$.posts[0].title").value("N5 인사"));
        list(0, 0).andExpect(jsonPath("$.totalPosts").value(0));
    }

    @Test
    @DisplayName("글 상세에 댓글이 오래된 순으로 오고, 보는 사람 기준으로 mine 이 정해진다")
    void detailWithComments() throws Exception {
        String postId = id(write(aliceBearer, 4, "제목", "본문"));
        comment(bobBearer, postId, "첫 댓글");
        comment(aliceBearer, postId, "두 번째")
                .andExpect(jsonPath("$.comments.length()").value(2));

        mockMvc.perform(get("/board/posts/{id}", postId).header("Authorization", bobBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("본문"))
                .andExpect(jsonPath("$.mine").value(false))
                .andExpect(jsonPath("$.comments[0].content").value("첫 댓글"))
                .andExpect(jsonPath("$.comments[0].author").value("밥"))
                .andExpect(jsonPath("$.comments[0].mine").value(true))
                .andExpect(jsonPath("$.comments[1].mine").value(false));
    }

    @Test
    @DisplayName("글·댓글은 쓴 사람만 고치거나 지울 수 있다")
    void onlyAuthor() throws Exception {
        String postId = id(write(aliceBearer, 2, "제목", "본문"));
        String commentId = JsonPath.read(comment(bobBearer, postId, "밥의 댓글")
                .andReturn().getResponse().getContentAsString(), "$.comments[0].id");

        mockMvc.perform(patch("/board/posts/{id}", postId)
                        .header("Authorization", bobBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"가로채기\", \"content\": \"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_AUTHOR"));
        mockMvc.perform(delete("/board/posts/{id}", postId).header("Authorization", bobBearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/board/comments/{id}", commentId).header("Authorization", aliceBearer))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/board/posts/{id}", postId)
                        .header("Authorization", aliceBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"고친 제목\", \"content\": \"고친 본문\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("고친 제목"));
        mockMvc.perform(delete("/board/comments/{id}", commentId).header("Authorization", bobBearer))
                .andExpect(status().isNoContent());

        // 댓글이 달린 글도 지울 수 있다
        comment(bobBearer, postId, "또 댓글");
        mockMvc.perform(delete("/board/posts/{id}", postId).header("Authorization", aliceBearer))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/board/posts/{id}", postId).header("Authorization", aliceBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("한 페이지에 20개, 최신 글이 먼저")
    void paging() throws Exception {
        for (int i = 1; i <= 21; i++) write(aliceBearer, 1, "글" + i, "본문");

        list(1, 0)
                .andExpect(jsonPath("$.posts.length()").value(20))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalPosts").value(21))
                .andExpect(jsonPath("$.posts[0].title").value("글21"));
        list(1, 1)
                .andExpect(jsonPath("$.posts.length()").value(1))
                .andExpect(jsonPath("$.posts[0].title").value("글1"));
    }

    @Test
    @DisplayName("빈 제목·너무 긴 글, 없는 단계는 거절한다")
    void validation() throws Exception {
        write(aliceBearer, 3, " ", "본문").andExpect(status().isBadRequest());
        write(aliceBearer, 3, "가".repeat(101), "본문").andExpect(status().isBadRequest());
        write(aliceBearer, 3, "제목", "가".repeat(5001)).andExpect(status().isBadRequest());
        write(aliceBearer, 6, "제목", "본문")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BOARD_LEVEL_NOT_FOUND"));
        list(-1, 0).andExpect(status().isNotFound());
        mockMvc.perform(get("/board/abc/posts").header("Authorization", aliceBearer))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/board/3/posts")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("탈퇴하면 그 사람의 글·댓글과, 그 사람 글에 달린 댓글까지 지워진다")
    void deleteAccount() throws Exception {
        String alicePost = id(write(aliceBearer, 3, "앨리스 글", "본문"));
        String bobPost = id(write(bobBearer, 3, "밥 글", "본문"));
        comment(bobBearer, alicePost, "밥이 앨리스 글에");
        comment(aliceBearer, bobPost, "앨리스가 밥 글에");

        mockMvc.perform(delete("/user/me").header("Authorization", aliceBearer))
                .andExpect(status().isNoContent());

        assertThat(postRepository.findById(UUID.fromString(alicePost))).isEmpty();
        mockMvc.perform(get("/board/posts/{id}", bobPost).header("Authorization", bobBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments.length()").value(0));
    }
}
