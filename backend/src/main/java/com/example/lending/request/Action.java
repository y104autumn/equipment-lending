package com.example.lending.request;

/** 総務が申請に対して行う操作（要件定義書 §3 の「操作」列）。 */
public enum Action {
    APPROVE("approve"),
    REJECT("reject"),
    LEND("lend"),
    RETURN("return");

    /** URL の末尾（/api/requests/{id}/approve 等）。画面はこの値で操作ボタンを出す。 */
    private final String path;

    Action(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
