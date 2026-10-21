package com.example.lending.request;

/** 申請番号が無い（404）。 */
class RequestNotFoundException extends RuntimeException {

    RequestNotFoundException(Long id) {
        super("申請番号 " + id + " はありません");
    }
}

/** 状態の表（要件定義書 §3）に無い操作（409）。 */
class IllegalTransitionException extends RuntimeException {

    IllegalTransitionException(Long id, Status from, Action action) {
        super("申請番号 " + id + " は " + from + " のため " + action.path() + " できません");
    }
}

/** 入力の決まり（要件定義書 §5）に反する。Bean Validation では検査できないもの（400）。 */
class InvalidInputException extends RuntimeException {

    InvalidInputException(String message) {
        super(message);
    }
}
