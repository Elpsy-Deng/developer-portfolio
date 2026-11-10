package dev.jackteng.portfolio.catalog;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CatalogErrors {
    public record Error(String code, String message) {}

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Error> invalidBody(Exception error) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(new Error("INVALID_PROJECT", "作品欄位格式不正確"));
    }
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public ResponseEntity<Error> conflict(Exception error) {
        return ResponseEntity.status(409).cacheControl(CacheControl.noStore()).body(new Error("CATALOG_CONFLICT", "資料發生衝突，請重新核對"));
    }
    @ExceptionHandler(dev.jackteng.portfolio.media.ImageStorageProblem.class)
    public ResponseEntity<Error> storage(Exception error) {
        return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(new Error("IMAGE_STORAGE_UNAVAILABLE", "圖片儲存暫時無法使用"));
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Error> tooLarge(Exception error) {
        return ResponseEntity.status(413).cacheControl(CacheControl.noStore()).body(new Error("IMAGE_TOO_LARGE", "圖片或請求大小超過限制"));
    }
    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class, org.springframework.web.multipart.support.MissingServletRequestPartException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Error> invalidMedia(Exception error) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(new Error("INVALID_MEDIA", "圖片欄位不完整或格式不正確"));
    }
    @ExceptionHandler(CatalogProblem.class)
    public ResponseEntity<Error> problem(CatalogProblem error) {
        return ResponseEntity.status(error.status()).cacheControl(CacheControl.noStore())
                .body(new Error(error.code(), error.getMessage()));
    }

    @ExceptionHandler({org.springframework.dao.DataAccessException.class, org.springframework.transaction.TransactionException.class})
    public ResponseEntity<Error> databaseUnavailable(Exception error, jakarta.servlet.http.HttpServletRequest request) {
        return ResponseEntity.status(503).cacheControl(CacheControl.noStore())
                .body(request.getRequestURI().startsWith("/api/admin/projects") || request.getRequestURI().equals("/api/admin/catalog-options")
                        ? new Error("CATALOG_UNAVAILABLE", "作品資料暫時無法讀取")
                        : request.getRequestURI().startsWith("/api/admin/")
                        ? new Error("AUTH_UNAVAILABLE", "登入服務暫時無法使用")
                        : new Error("CATALOG_UNAVAILABLE", "作品資料暫時無法讀取"));
    }
}
