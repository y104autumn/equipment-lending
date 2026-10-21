package com.example.lending.request;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 申請の状態と、状態の表（要件定義書 §3）。
 *
 * WHY: 遷移の規則はここ 1 箇所にだけ書く（開発規約 §3）。サービスも画面向けの「押せる操作」も、
 *      この表から引く。規則が散らばると表と食い違うため。
 */
public enum Status {
    REQUESTED,
    APPROVED,
    REJECTED,
    LENT,
    RETURNED;

    private static final Map<Status, Map<Action, Status>> TABLE = new EnumMap<>(Status.class);

    static {
        TABLE.put(REQUESTED, Map.of(Action.APPROVE, APPROVED, Action.REJECT, REJECTED));
        TABLE.put(APPROVED, Map.of(Action.LEND, LENT));
        TABLE.put(LENT, Map.of(Action.RETURN, RETURNED));
        // REJECTED・RETURNED は終了＝次に進める操作が無い。
    }

    /** この状態から action を行った後の状態。表に無い操作なら空。 */
    public Optional<Status> next(Action action) {
        return Optional.ofNullable(TABLE.getOrDefault(this, Map.of()).get(action));
    }

    /** この状態から行える操作。 */
    public Set<Action> allowedActions() {
        return TABLE.getOrDefault(this, Map.of()).keySet();
    }
}
