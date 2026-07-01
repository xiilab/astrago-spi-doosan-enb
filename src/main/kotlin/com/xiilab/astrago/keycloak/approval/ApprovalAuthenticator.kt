package com.xiilab.astrago.keycloak.approval

import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriBuilder
import org.jboss.logging.Logger
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.keycloak.authentication.Authenticator
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel

/**
 * Azure AD 로그인 성공 후, Astrago 백엔드의 승인 상태를 확인해 미승인 사용자를 차단하는 Authenticator.
 *
 * Post-Login Flow 에 Required 로 배치한다.
 * - exists=false  : 승인 미신청 → 차단(미신청 안내)
 * - approved=false: 승인 대기/거부 → 차단(대기·거부 안내)
 * - approved=true : 로그인 허용
 *
 * 차단 시 동작:
 * - 프론트 안내 URL(frontend.approval.url) 이 설정돼 있으면 해당 페이지로 리다이렉트한다
 *   ({url}?status=not-requested|pending). 미승인 안내 화면을 프론트(Next.js)가 담당한다.
 * - 미설정 시 기존 Keycloak 에러 페이지(로그인테마)로 fallback 한다.
 *
 * 게이트는 "순서"가 아니라 "현재 승인 상태"만 본다(idempotent). 승인 전에 먼저 로그인해도
 * 미승인 페이지만 보일 뿐 깨지지 않는다.
 */
class ApprovalAuthenticator : Authenticator {

    override fun authenticate(context: AuthenticationFlowContext) {
        val user = context.user
        if (user == null) {
            log.warn("인증 컨텍스트에 사용자가 없어 통과 처리")
            context.success()
            return
        }

        val email = user.email?.takeIf { it.isNotBlank() } ?: user.username
        val config = context.authenticatorConfig?.config ?: emptyMap()

        val backendUrl = config[CONFIG_BACKEND_URL]?.trim()
        if (backendUrl.isNullOrEmpty()) {
            log.error("Backend API URL 미설정 (config: $CONFIG_BACKEND_URL)")
            context.failure(AuthenticationFlowError.INTERNAL_ERROR)
            return
        }

        val status = BackendApiClient(backendUrl).checkApproval(email)

        when {
            !status.exists -> {
                log.warnf("승인 미신청 사용자: %s", email)
                handleDenied(
                    context,
                    config,
                    STATUS_NOT_REQUESTED,
                    config[CONFIG_PENDING_MESSAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_PENDING_MESSAGE,
                )
            }

            !status.approved -> {
                log.warnf("승인 대기/거부 사용자: %s", email)
                handleDenied(
                    context,
                    config,
                    STATUS_PENDING,
                    config[CONFIG_ERROR_MESSAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_ERROR_MESSAGE,
                )
            }

            else -> {
                log.infof("승인 확인됨: %s", email)
                user.setSingleAttribute("approval_status", "approved")
                context.success()
            }
        }
    }

    override fun action(context: AuthenticationFlowContext) {
        context.success()
    }

    /**
     * 차단 처리. 프론트 안내 URL 이 설정돼 있으면 리다이렉트, 아니면 에러 페이지로 fallback.
     */
    private fun handleDenied(
        context: AuthenticationFlowContext,
        config: Map<String, String>,
        statusKind: String,
        message: String,
    ) {
        val frontendUrl = config[CONFIG_FRONTEND_URL]?.trim()
        if (!frontendUrl.isNullOrEmpty()) {
            val uri = UriBuilder.fromUri(frontendUrl)
                .queryParam("status", statusKind)
                .build()
            context.failure(AuthenticationFlowError.ACCESS_DENIED, Response.seeOther(uri).build())
        } else {
            deny(context, message)
        }
    }

    /** 프론트 URL 미설정 시 fallback — Keycloak 로그인테마 에러 페이지에 메시지를 표시한다. */
    private fun deny(context: AuthenticationFlowContext, message: String) {
        val response: Response = context.form()
            .setError(message)
            .setAttribute("errorMessage", message)
            .createErrorPage(Response.Status.FORBIDDEN)
        context.failure(AuthenticationFlowError.ACCESS_DENIED, response)
    }

    override fun requiresUser(): Boolean = false

    override fun configuredFor(session: KeycloakSession, realm: RealmModel, user: UserModel): Boolean = true

    override fun setRequiredActions(session: KeycloakSession, realm: RealmModel, user: UserModel) {
        // 별도 required action 없음
    }

    override fun close() {
        // 정리할 리소스 없음
    }

    companion object {
        private val log: Logger = Logger.getLogger(ApprovalAuthenticator::class.java)

        const val CONFIG_BACKEND_URL = "backend.api.url"
        const val CONFIG_ERROR_MESSAGE = "error.message"
        const val CONFIG_PENDING_MESSAGE = "pending.message"
        const val CONFIG_FRONTEND_URL = "frontend.approval.url"

        // 프론트 안내 페이지로 전달하는 상태 값(이메일 등 PII 는 전달하지 않는다).
        const val STATUS_NOT_REQUESTED = "not-requested"
        const val STATUS_PENDING = "pending"

        const val DEFAULT_PENDING_MESSAGE =
            "승인 신청이 필요합니다. Service Navigator를 통해 계정 승인을 신청해주세요."
        const val DEFAULT_ERROR_MESSAGE =
            "계정 승인 대기 중이거나 거부되었습니다. 관리자에게 문의하세요."
    }
}
