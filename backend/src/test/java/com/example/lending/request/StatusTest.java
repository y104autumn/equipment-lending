package com.example.lending.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 状態の表（要件定義書 §3）を 5 状態 × 4 操作の全組み合わせで確かめる。 */
class StatusTest {

    /** 表で許される遷移。ここに無い組み合わせはすべて不可。 */
    private static final Map<Status, Map<Action, Status>> EXPECTED = Map.of(
            Status.REQUESTED, Map.of(Action.APPROVE, Status.APPROVED, Action.REJECT, Status.REJECTED),
            Status.APPROVED, Map.of(Action.LEND, Status.LENT),
            Status.LENT, Map.of(Action.RETURN, Status.RETURNED));

    @Test
    void 全組み合わせが表どおり() {
        int allowed = 0;
        int denied = 0;
        for (Status from : Status.values()) {
            for (Action action : Action.values()) {
                Status expected = EXPECTED.getOrDefault(from, Map.of()).get(action);
                assertThat(from.next(action)).as("%s + %s", from, action).isEqualTo(Optional.ofNullable(expected));
                if (expected == null) {
                    denied++;
                } else {
                    allowed++;
                }
            }
        }
        // WHY: 表の行を書き漏らしてもループは通ってしまうため、件数でも押さえる。
        assertThat(allowed).isEqualTo(4);
        assertThat(denied).isEqualTo(16);
    }

    @Test
    void 押せる操作は表の行と一致する() {
        for (Status s : Status.values()) {
            assertThat(s.allowedActions()).as("%s", s)
                    .containsExactlyInAnyOrderElementsOf(EXPECTED.getOrDefault(s, Map.of()).keySet());
        }
    }

    @Test
    void 終了状態では何も押せない() {
        assertThat(Status.REJECTED.allowedActions()).isEmpty();
        assertThat(Status.RETURNED.allowedActions()).isEmpty();
    }
}
