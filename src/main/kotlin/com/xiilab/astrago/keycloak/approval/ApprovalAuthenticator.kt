package com.xiilab.astrago.keycloak.approval

import jakarta.ws.rs.core.Response
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
                deny(context, config[CONFIG_PENDING_MESSAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_PENDING_MESSAGE)
            }

            !status.approved -> {
                log.warnf("승인 대기/거부 사용자: %s", email)
                deny(context, config[CONFIG_ERROR_MESSAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_ERROR_MESSAGE)
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

        const val DEFAULT_PENDING_MESSAGE =
            "승인 신청이 필요합니다. Service Navigator를 통해 계정 승인을 신청해주세요."
        const val DEFAULT_ERROR_MESSAGE =
            "계정 승인 대기 중이거나 거부되었습니다. 관리자에게 문의하세요."
    }
}
