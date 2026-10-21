package com.example.lending.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * API（要件定義書 §7）を H2 の上で通しで確かめる。PostgreSQL での確認は WBS #11。
 *
 * WHY: @Transactional でテストごとに巻き戻し、テスト同士が申請の件数で干渉しないようにする。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RequestApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StatusHistoryRepository historyRepository;

    private static final String VALID = """
            {"applicantName":"山田 太郎","equipmentId":1,"startDate":"2026-11-02","dueDate":"2026-11-06","reason":"出張で使う"}
            """;

    private long create(String body) throws Exception {
        String res = mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.requestId")).longValue();
    }

    private void act(long id, String action, int expectedStatus) throws Exception {
        mvc.perform(post("/api/requests/" + id + "/" + action)).andExpect(status().is(expectedStatus));
    }

    // ---- 正常系 ----

    @Test
    void 備品の一覧が返る() throws Exception {
        mvc.perform(get("/api/equipment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].name", is("ノート PC")));
    }

    @Test
    void 申請すると申請中になり承認と却下が押せる() throws Exception {
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("REQUESTED")))
                .andExpect(jsonPath("$.equipmentName", is("ノート PC")))
                .andExpect(jsonPath("$.actions", contains("approve", "reject")));
    }

    @Test
    void 申請から返却まで進み履歴が4件残る() throws Exception {
        long id = create(VALID);
        mvc.perform(post("/api/requests/" + id + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.actions", contains("lend")));
        mvc.perform(post("/api/requests/" + id + "/lend"))
                .andExpect(jsonPath("$.status", is("LENT")))
                .andExpect(jsonPath("$.actions", contains("return")));
        mvc.perform(post("/api/requests/" + id + "/return"))
                .andExpect(jsonPath("$.status", is("RETURNED")))
                .andExpect(jsonPath("$.actions", hasSize(0)));

        var history = historyRepository.findByRequestIdOrderByIdAsc(id);
        assertThat(history).extracting(StatusHistory::getToStatus)
                .containsExactly(Status.REQUESTED, Status.APPROVED, Status.LENT, Status.RETURNED);
        assertThat(history.get(0).getFromStatus()).isNull();
    }

    @Test
    void 却下すると終了になる() throws Exception {
        long id = create(VALID);
        mvc.perform(post("/api/requests/" + id + "/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REJECTED")))
                .andExpect(jsonPath("$.actions", hasSize(0)));
    }

    @Test
    void 状態で絞り込める() throws Exception {
        long a = create(VALID);
        long b = create(VALID);
        act(a, "approve", 200);
        mvc.perform(get("/api/requests").param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].requestId", is((int) a)));
        // WHY: 全件数で比べると、巻き戻さない他のテストの行が残った時に落ちる。作った 2 件が含まれるかで見る。
        mvc.perform(get("/api/requests"))
                .andExpect(jsonPath("$[*].requestId", hasItems((int) a, (int) b)));
    }

    @Test
    void 理由は空白だけなら記入なしになり_200字ちょうどは通る() throws Exception {
        String blank = VALID.replace("出張で使う", "   ");
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(blank))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").doesNotExist());
        String max = VALID.replace("出張で使う", "あ".repeat(200));
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(max))
                .andExpect(status().isCreated());
    }

    @Test
    void 開始日と返却予定日が同じ日は通る() throws Exception {
        String sameDay = VALID.replace("2026-11-06", "2026-11-02");
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(sameDay))
                .andExpect(status().isCreated());
    }

    // ---- 異常系 400 ----

    @Test
    void 申請者名が空白だと400() throws Exception {
        String body = VALID.replace("山田 太郎", "  ");
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem("申請者名は必須です")));
    }

    @Test
    void 申請者名51字と理由201字は400() throws Exception {
        String body = VALID.replace("山田 太郎", "名".repeat(51)).replace("出張で使う", "あ".repeat(201));
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", containsInAnyOrder("申請者名は 50 字までです", "理由は 200 字までです")));
    }

    @Test
    void 返却予定日が開始日より前だと400() throws Exception {
        String body = VALID.replace("2026-11-06", "2026-11-01");
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem("返却予定日は開始日以降にしてください")));
    }

    @Test
    void 必須項目が欠けると400() throws Exception {
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasSize(4)));
    }

    @Test
    void 存在しない備品は400() throws Exception {
        String body = VALID.replace("\"equipmentId\":1", "\"equipmentId\":999");
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 日付の書式が壊れていると400() throws Exception {
        String body = VALID.replace("2026-11-02", "2026/11/02");
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void JSONが壊れていると400() throws Exception {
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content("{\"applicantName\":"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 表に無い状態で絞り込むと400() throws Exception {
        mvc.perform(get("/api/requests").param("status", "DONE"))
                .andExpect(status().isBadRequest());
    }

    // ---- 異常系 404 / 409 ----

    @Test
    void 存在しない申請番号は404() throws Exception {
        act(999999, "approve", 404);
    }

    @Test
    void 申請中から返却や貸出はできず409() throws Exception {
        long id = create(VALID);
        act(id, "return", 409);
        act(id, "lend", 409);
    }

    @Test
    void 二重承認は409で履歴は増えない() throws Exception {
        long id = create(VALID);
        act(id, "approve", 200);
        act(id, "approve", 409);
        assertThat(historyRepository.findByRequestIdOrderByIdAsc(id)).hasSize(2);
    }

    @Test
    void 終了した申請はどの操作も409() throws Exception {
        long id = create(VALID);
        act(id, "reject", 200);
        for (String a : new String[] {"approve", "reject", "lend", "return"}) {
            act(id, a, 409);
        }
        // WHY: everyItem は空でも通るので、作った申請が含まれることも見る。
        mvc.perform(get("/api/requests").param("status", "REJECTED"))
                .andExpect(jsonPath("$[*].requestId", hasItem((int) id)))
                .andExpect(jsonPath("$[*].status", everyItem(is("REJECTED"))));
    }
}
