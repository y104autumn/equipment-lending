package com.example.lending.request;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Dify のワークフロー「備品貸出 申請理由の確認」を呼ぶ（契約は docs/04_契約/Dify連携.md）。
 *
 * WHY: Dify は承認の補助であって業務の必須経路ではない（契約 §3）。キーが無い・Dify が落ちている・遅いなど
 *      どの失敗でも例外を外へ出さず空を返し、申請・承認の操作は止めない。
 */
@Component
class DifyClient {

    private static final Logger log = LoggerFactory.getLogger(DifyClient.class);

    private final RestClient http;
    private final String apiKey;

    DifyClient(@Value("${dify.base-url:https://api.dify.ai/v1}") String baseUrl,
            @Value("${dify.api-key:}") String apiKey,
            // WHY: blocking の上限は 100 秒（契約 §1）。それより待っても Dify 側で切られる。
            @Value("${dify.timeout-seconds:100}") int timeoutSeconds) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.apiKey = apiKey;
    }

    /** 確認ポイント（文章）。取れなかった時は空。 */
    Optional<String> checkPoints(Long requestId, String equipmentName, LocalDate startDate, LocalDate dueDate,
            String reason) {
        if (apiKey.isBlank()) {
            log.warn("dify.api-key が未設定のため Dify を呼ばない");
            return Optional.empty();
        }
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("equipment_name", equipmentName);
        inputs.put("start_date", startDate.toString());
        inputs.put("due_date", dueDate.toString());
        // WHY: 理由は任意（テーブル定義書: NULL＝記入なし）。Dify の入力は文字列なので空文字で渡す。
        inputs.put("reason", reason == null ? "" : reason);
        Map<String, Object> body = Map.of(
                "inputs", inputs,
                "response_mode", "blocking",
                // WHY: user は呼び出し元の識別子（契約 §1）。申請者名は個人情報なので送らず、申請番号で識別する。
                "user", "request-" + requestId);
        try {
            Map<?, ?> res = http.post().uri("/workflows/run")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (res != null && res.get("data") instanceof Map<?, ?> data
                    && data.get("outputs") instanceof Map<?, ?> outputs
                    && outputs.get("check_points") instanceof String text && !text.isBlank()) {
                return Optional.of(text.strip());
            }
            // WHY: 200 でもワークフローが失敗していると outputs が空で返る（契約 §1: Output ノードが無い時も同じ）。
            log.warn("Dify の応答に check_points が無い: status={}",
                    res != null && res.get("data") instanceof Map<?, ?> d ? d.get("status") : null);
            return Optional.empty();
        } catch (RuntimeException e) {
            // WHY: 4xx/5xx は RestClientResponseException、タイムアウト・接続失敗は ResourceAccessException。扱いは同じ（契約 §3）。
            log.warn("Dify の呼び出しに失敗: {}", e.toString());
            return Optional.empty();
        }
    }
}
