package com.forestry.counter.domain.model

import java.util.Locale

/** Code de compte, distinct des identifiants et mots de passe jamais corrigés. */
object AccountActionCode {
    fun normalize(value: String): String? {
        val compact = value.replace("-", "").replace(" ", "")
        // Vérifier avant uppercase : une lettre Unicode telle que ß ne doit
        // jamais devenir un code ASCII accepté par expansion en « SS ».
        if (compact.length != 8 || compact.any {
                it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9'
            }
        ) return null
        return compact.uppercase(Locale.ROOT)
    }
}
