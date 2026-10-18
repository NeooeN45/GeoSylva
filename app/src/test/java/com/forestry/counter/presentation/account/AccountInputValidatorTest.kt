package com.forestry.counter.presentation.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import com.forestry.counter.domain.model.AccountActionCode

class AccountInputValidatorTest {

    @Test
    fun `verification code accepts normalized eight character value`() {
        assertTrue(AccountInputValidator.isValidActionCode("ABCD-EFGH"))
        assertTrue(AccountInputValidator.isValidActionCode("abcd efgh"))
        assertFalse(AccountInputValidator.isValidActionCode("1234"))
        assertEquals("ABCDEFGH", AccountActionCode.normalize(" abcd - efgh "))
    }

    @Test
    fun `account action codes reject unicode and unsupported separators`() {
        assertFalse(AccountInputValidator.isValidActionCode("abcdéfgh"))
        assertFalse(AccountInputValidator.isValidActionCode("abcßefg"))
        assertFalse(AccountInputValidator.isValidActionCode("ABCD.EFGH"))
        assertFalse(AccountInputValidator.isValidActionCode("ABCD\tEFGH"))
    }

    @Test
    fun `password reset requires matching strong values`() {
        assertTrue(
            AccountInputValidator.isValidPasswordReset(
                "mot-de-passe-vraiment-solide",
                "mot-de-passe-vraiment-solide",
            )
        )
        assertFalse(AccountInputValidator.isValidPasswordReset("trop-court", "trop-court"))
        assertFalse(
            AccountInputValidator.isValidPasswordReset(
                "mot-de-passe-vraiment-solide",
                "mot-de-passe-different",
            )
        )
    }
}
