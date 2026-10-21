package com.example.lending.request;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 応答の決まり（要件定義書 §7）: 400＝入力／404＝申請番号が無い／409＝状態の表に無い操作、または同じ申請を同時に操作した時の後の方。 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getAllErrors().stream()
                .map(err -> err.getDefaultMessage())
                .sorted()
                .toList();
        return error(HttpStatus.BAD_REQUEST, "入力に誤りがあります", details);
    }

    /** JSON の形・日付の書式が壊れている、または状態の値が表に無い（?status=XXX）。 */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> unreadable(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "入力の形式が正しくありません", List.of());
    }

    @ExceptionHandler(InvalidInputException.class)
    ResponseEntity<ApiError> invalidInput(InvalidInputException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage(), List.of());
    }

    @ExceptionHandler(RequestNotFoundException.class)
    ResponseEntity<ApiError> notFound(RequestNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage(), List.of());
    }

    @ExceptionHandler(IllegalTransitionException.class)
    ResponseEntity<ApiError> illegalTransition(IllegalTransitionException e) {
        return error(HttpStatus.CONFLICT, e.getMessage(), List.of());
    }

    /** WHY: 同じ申請を 2 人が同時に操作した時の後の方。状態の表に照らすと既に別の状態なので 409 にそろえる。 */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> conflict(ObjectOptimisticLockingFailureException e) {
        return error(HttpStatus.CONFLICT, "他の操作で申請が更新されました。一覧を読み直してください", List.of());
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message, details));
    }
}
