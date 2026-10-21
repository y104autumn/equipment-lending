package com.example.lending.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 楽観ロック（テーブル定義書の version 列）を確かめる。
 *
 * WHY: 順番に 2 回押すテストは状態の表の検査で 409 になり、@Version を外しても通ってしまう。
 * 同時操作をスレッド無しで再現するため、トランザクションの外で古いコピーを持ち、別の操作の後で保存する。
 * そのためクラスに @Transactional を付けず、作った行は後始末で消す。
 */
@SpringBootTest
@AutoConfigureMockMvc
class OptimisticLockTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private LendingRequestRepository requestRepository;

    @Autowired
    private StatusHistoryRepository historyRepository;

    @AfterEach
    void cleanUp() {
        historyRepository.deleteAll();
        requestRepository.deleteAll();
    }

    @Test
    void 古い版のまま保存すると弾かれる() throws Exception {
        String res = mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content("""
                {"applicantName":"山田 太郎","equipmentId":1,"startDate":"2026-11-02","dueDate":"2026-11-06"}
                """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(res, "$.requestId")).longValue();

        LendingRequest stale = requestRepository.findById(id).orElseThrow();
        mvc.perform(post("/api/requests/" + id + "/approve")).andExpect(status().isOk());

        stale.apply(Action.REJECT);
        assertThatThrownBy(() -> requestRepository.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(requestRepository.findById(id).orElseThrow().getStatus()).isEqualTo(Status.APPROVED);
    }

    @Test
    void 楽観ロックの例外は409になる() {
        var res = new ApiExceptionHandler()
                .conflict(new ObjectOptimisticLockingFailureException(LendingRequest.class, 1L));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().status()).isEqualTo(409);
    }
}
