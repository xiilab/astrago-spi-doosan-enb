package com.xiilab.astrago.keycloak.approval

import com.fasterxml.jackson.databind.JsonNode
import org.jboss.logging.Logger
import org.keycloak.util.JsonSerialization
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * 승인 상태 조회 결과. 백엔드 응답 {exists, approved, message} 와 매핑.
 */
data class ApprovalStatus(
    val exists: Boolean,
    val approved: Boolean,
    val message: String,
)

/**
 * Astrago 백엔드 승인 상태 조회 클라이언트.
 *
 * 계약: POST {baseUrl}/api/v1/user/approval/check  (요청 본문 { "email": String })
 *   - 이메일(PII)을 URL이 아닌 본문으로 전달해 프록시/액세스 로그 노출을 방지한다.
 * 응답: { "exists": Boolean, "approved": Boolean, "message": String }
 *
 * 외부 의존성 없이 JDK HttpClient + Keycloak 내장 Jackson(JsonSerialization) 만 사용 → shade 불필요.
 */
class BackendApiClient(baseUrl: String) {

    private val baseUrl: String = baseUrl.trimEnd('/')

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
        .build()

    fun checkApproval(email: String): ApprovalStatus {
        val masked = maskEmail(email)
        return try {
            val uri = URI.create("$baseUrl/api/v1/user/approval/check")
            val body = JsonSerialization.writeValueAsString(mapOf("email" to email))

            val request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() !in 200..299) {
                log.warnf("승인 조회 실패 %s: HTTP %d", masked, response.statusCode())
                return ApprovalStatus(exists = false, approved = false, message = "승인 상태 확인 실패")
            }

            val node: JsonNode = JsonSerialization.readValue(response.body(), JsonNode::class.java)
            val exists = node.path("exists").asBoolean(false)
            val approved = node.path("approved").asBoolean(false)
            val message = node.path("message").asText("승인 상태 확인 완료")

            log.infof("승인 조회 %s: exists=%s, approved=%s", masked, exists, approved)
            ApprovalStatus(exists, approved, message)
        } catch (e: Exception) {
            log.errorf(e, "승인 조회 중 오류 %s", masked)
            ApprovalStatus(exists = false, approved = false, message = "시스템 오류: ${e.message}")
        }
    }

    companion object {
        private val log: Logger = Logger.getLogger(BackendApiClient::class.java)
        private const val TIMEOUT_SECONDS = 10L

        /** 로그에 원본 이메일(PII)이 남지 않도록 로컬파트를 마스킹한다. (예: u***@doosan.com) */
        private fun maskEmail(email: String): String {
            val atIndex = email.indexOf('@')
            if (atIndex <= 0) return "***"
            return "${email.first()}***${email.substring(atIndex)}"
        }
    }
}
