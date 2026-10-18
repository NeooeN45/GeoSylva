package com.forestry.counter.presentation.account

import com.forestry.counter.domain.model.AccountActionCode

/** Validations pures partagées par les écrans sensibles du compte. */
object AccountInputValidator {
    // Les identifiants ne sont jamais corrigés automatiquement : le serveur
    // reste autoritaire sur la validité de l'adresse.
    fun isValidEmail(value: String): Boolean =
        value.trim().length <= 320 && EMAIL_PATTERN.matches(value.trim())

    fun isValidDisplayName(value: String): Boolean = value.trim().length <= 200

    fun isValidActionCode(value: String): Boolean = AccountActionCode.normalize(value) != null

    fun isValidPasswordReset(password: String, confirmation: String): Boolean =
        password.length in 12..128 && password == confirmation

    private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
}
