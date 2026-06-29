package com.xiilab.astrago.keycloak.approval

import org.keycloak.Config
import org.keycloak.authentication.Authenticator
import org.keycloak.authentication.AuthenticatorFactory
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.provider.ProviderConfigProperty
import org.keycloak.provider.ProviderConfigurationBuilder

/**
 * [ApprovalAuthenticator] 팩토리. Keycloak Admin Console 의 Authentication Flow 에 노출된다.
 */
class ApprovalAuthenticatorFactory : AuthenticatorFactory {

    override fun create(session: KeycloakSession): Authenticator = ApprovalAuthenticator()

    override fun init(config: Config.Scope?) {}

    override fun postInit(factory: KeycloakSessionFactory?) {}

    override fun close() {}

    override fun getId(): String = PROVIDER_ID

    override fun getDisplayType(): String = "Astrago Doosan-ENB 사용자 승인 확인"

    override fun getReferenceCategory(): String = "approval"

    override fun isConfigurable(): Boolean = true

    override fun getRequirementChoices(): Array<AuthenticationExecutionModel.Requirement> = arrayOf(
        AuthenticationExecutionModel.Requirement.REQUIRED,
        AuthenticationExecutionModel.Requirement.DISABLED,
    )

    override fun isUserSetupAllowed(): Boolean = false

    override fun getHelpText(): String =
        "Azure AD 로그인 후 Astrago 백엔드 승인 상태(POST /api/v1/user/approval/check)를 확인하여 미승인 사용자의 로그인을 차단합니다."

    override fun getConfigProperties(): List<ProviderConfigProperty> = CONFIG_PROPERTIES

    companion object {
        const val PROVIDER_ID = "astrago-doosan-approval-check"

        private val CONFIG_PROPERTIES: List<ProviderConfigProperty> = ProviderConfigurationBuilder.create()
            .property()
            .name(ApprovalAuthenticator.CONFIG_BACKEND_URL)
            .type(ProviderConfigProperty.STRING_TYPE)
            .label("Backend API URL")
            .helpText("승인 상태를 확인할 Astrago 백엔드 기본 URL (예: http://astrago-backend-core:8080)")
            .defaultValue("")
            .add()
            .property()
            .name(ApprovalAuthenticator.CONFIG_ERROR_MESSAGE)
            .type(ProviderConfigProperty.STRING_TYPE)
            .label("승인 대기/거부 메시지")
            .helpText("승인 대기 중이거나 거부된 사용자에게 표시할 메시지")
            .defaultValue(ApprovalAuthenticator.DEFAULT_ERROR_MESSAGE)
            .add()
            .property()
            .name(ApprovalAuthenticator.CONFIG_PENDING_MESSAGE)
            .type(ProviderConfigProperty.STRING_TYPE)
            .label("승인 미신청 메시지")
            .helpText("승인 신청을 하지 않은 사용자에게 표시할 메시지")
            .defaultValue(ApprovalAuthenticator.DEFAULT_PENDING_MESSAGE)
            .add()
            .build()
    }
}
