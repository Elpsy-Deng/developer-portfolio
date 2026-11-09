package dev.elpsy.portfolio.auth;

import java.io.IOException;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class AuthResponses {
    private AuthResponses() {}
    public static void error(ObjectMapper json, HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        json.writeValue(response.getWriter(), Map.of("code", code, "message", switch (code) {
            case "AUTH_UNAVAILABLE" -> "登入服務暫時無法使用";
            case "LOGIN_RATE_LIMITED" -> "登入嘗試過於頻繁，請稍後再試";
            case "SESSION_RATE_LIMITED" -> "工作階段建立過於頻繁，請稍後再試";
            case "INVALID_CREDENTIALS" -> "帳號或密碼不正確";
            case "CSRF_INVALID" -> "驗證已失效，請重新載入後操作";
            case "FORBIDDEN" -> "你沒有執行此操作的權限";
            default -> "請重新登入";
        }));
    }
}
