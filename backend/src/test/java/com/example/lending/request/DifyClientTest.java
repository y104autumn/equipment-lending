package com.example.lending.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Dify の応答を模したローカルの HTTP サーバーに向けて DifyClient を確かめる。
 *
 * WHY: 本物の Dify は無料枠のクレジットが補充されない（Dify連携.md §4）。異常系は本物では起こしにくいので、
 *      ここで応答を作って全部の分岐を通す。本物への呼び出しは WBS 8c で 1 回だけ行い、記録に残す。
 */
class DifyClientTest {

    private HttpServer server;
    private String baseUrl;
    private int status;
    private String responseBody;
    private long delayMillis;
    private final List<String> receivedAuth = new ArrayList<>();
    private final List<String> receivedBody = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/workflows/run", ex -> {
            receivedAuth.add(ex.getRequestHeaders().getFirst("Authorization"));
            receivedBody.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private DifyClient client(String key, int timeoutSeconds) {
        return new DifyClient(baseUrl, key, timeoutSeconds);
    }

    private java.util.Optional<String> call(DifyClient c, String reason) {
        return c.checkPoints(7L, "ノート PC", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6), reason);
    }

    // ---- 正常系 ----

    @Test
    void 成功すると確認ポイントが返り_キーと入力が契約どおり送られる() {
        status = 200;
        responseBody = """
                {"workflow_run_id":"x","data":{"status":"succeeded","outputs":{"check_points":"  ・期間は5日\\n・理由は具体的  "}}}
                """;
        assertThat(call(client("app-test", 5), "出張で使う")).contains("・期間は5日\n・理由は具体的");
        assertThat(receivedAuth).containsExactly("Bearer app-test");
        String sent = receivedBody.get(0);
        assertThat(sent).contains("\"equipment_name\":\"ノート PC\"", "\"start_date\":\"2026-11-02\"",
                "\"due_date\":\"2026-11-06\"", "\"reason\":\"出張で使う\"", "\"response_mode\":\"blocking\"",
                "\"user\":\"request-7\"");
    }

    @Test
    void 理由が無い時は空文字で送る() {
        status = 200;
        responseBody = "{\"data\":{\"outputs\":{\"check_points\":\"理由の記入が無い\"}}}";
        assertThat(call(client("app-test", 5), null)).contains("理由の記入が無い");
        assertThat(receivedBody.get(0)).contains("\"reason\":\"\"");
    }

    // ---- 異常系（どれも例外を出さず空・Dify連携.md §3） ----

    @Test
    void キーが未設定なら呼ばずに空() {
        assertThat(call(client("  ", 5), "x")).isEmpty();
        assertThat(receivedBody).isEmpty();
    }

    @Test
    void 認証エラー401は空() {
        status = 401;
        responseBody = "{\"code\":\"unauthorized\",\"message\":\"Access token is invalid\",\"status\":401}";
        assertThat(call(client("app-wrong", 5), "x")).isEmpty();
    }

    @Test
    void 実行枠切れ429は空() {
        status = 429;
        responseBody = "{\"code\":\"rate_limit_error\",\"message\":\"limit\",\"status\":429}";
        assertThat(call(client("app-test", 5), "x")).isEmpty();
    }

    @Test
    void サーバーエラー500は空() {
        status = 500;
        responseBody = "{\"code\":\"internal_server_error\",\"message\":\"boom\",\"status\":500}";
        assertThat(call(client("app-test", 5), "x")).isEmpty();
    }

    @Test
    void ワークフローが失敗して出力が空なら空() {
        status = 200;
        responseBody = "{\"data\":{\"status\":\"failed\",\"error\":\"LLM error\",\"outputs\":{}}}";
        assertThat(call(client("app-test", 5), "x")).isEmpty();
    }

    @Test
    void 出力が空白だけなら空() {
        status = 200;
        responseBody = "{\"data\":{\"outputs\":{\"check_points\":\"   \"}}}";
        assertThat(call(client("app-test", 5), "x")).isEmpty();
    }

    @Test
    void 応答がJSONでなければ空() {
        status = 200;
        responseBody = "<html>gateway</html>";
        assertThat(call(client("app-test", 5), "x")).isEmpty();
    }

    @Test
    void タイムアウトは空() {
        status = 200;
        responseBody = "{\"data\":{\"outputs\":{\"check_points\":\"遅すぎた\"}}}";
        delayMillis = 2500;
        long t0 = System.nanoTime();
        assertThat(call(client("app-test", 1), "x")).isEmpty();
        // WHY: 空が返っただけでは「待ち切ってから JSON を読み損ねた」のと区別できない。待った時間で切れたことを見る。
        assertThat((System.nanoTime() - t0) / 1_000_000).isLessThan(2400);
    }

    @Test
    void 接続できなければ空() {
        server.stop(0);
        assertThat(call(client("app-test", 2), "x")).isEmpty();
    }
}
