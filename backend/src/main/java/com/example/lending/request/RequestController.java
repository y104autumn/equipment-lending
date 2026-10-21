package com.example.lending.request;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 要件定義書 §7 の API。 */
@RestController
@RequestMapping("/api")
class RequestController {

    private final LendingService service;

    private final DifyClient dify;

    RequestController(LendingService service, DifyClient dify) {
        this.service = service;
        this.dify = dify;
    }

    /**
     * 承認前の確認ポイントを Dify に出させる（D-14）。押された時だけ呼ぶ。
     * WHY: Dify が失敗しても 200 で available=false を返す＝画面は業務を止めずに文言だけ出す（Dify連携.md §3）。
     *      申請番号が無い時だけは 404（Dify ではなくこのアプリの入力の誤り）。
     */
    @PostMapping("/requests/{id}/ai-check")
    AiCheckView aiCheck(@PathVariable Long id) {
        AiCheckInput in = service.aiCheckInput(id);
        return dify.checkPoints(in.requestId(), in.equipmentName(), in.startDate(), in.dueDate(), in.reason())
                .map(text -> new AiCheckView(id, true, text, null))
                .orElseGet(() -> new AiCheckView(id, false, null, "AI の確認を取得できませんでした"));
    }

    /** 画面の備品の選択肢。WHY: 選択肢を画面に直書きすると DB の備品と食い違うため、DB から返す。 */
    @GetMapping("/equipment")
    List<EquipmentView> equipment() {
        return service.listEquipment();
    }

    @GetMapping("/requests")
    List<RequestView> list(@RequestParam(required = false) Status status) {
        return service.list(status);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    RequestView create(@Valid @RequestBody CreateRequestBody body) {
        return service.create(body);
    }

    @PostMapping("/requests/{id}/approve")
    RequestView approve(@PathVariable Long id) {
        return service.transition(id, Action.APPROVE);
    }

    @PostMapping("/requests/{id}/reject")
    RequestView reject(@PathVariable Long id) {
        return service.transition(id, Action.REJECT);
    }

    @PostMapping("/requests/{id}/lend")
    RequestView lend(@PathVariable Long id) {
        return service.transition(id, Action.LEND);
    }

    @PostMapping("/requests/{id}/return")
    RequestView giveBack(@PathVariable Long id) {
        return service.transition(id, Action.RETURN);
    }
}
