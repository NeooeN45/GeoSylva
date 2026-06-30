package com.forestry.counter.domain.usecase.export

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Helper de formatage monétaire i18n.
 *
 * L'application tarife en euros : la devise EUR est donc conservée quel que soit
 * le langage de l'utilisateur, mais le formatage numérique (séparateurs de milliers,
 * symbole et son positionnement) s'adapte à la locale courante.
 */
internal object CurrencyFormatter {

    private val currencyFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        maximumFractionDigits = 0
        try { currency = Currency.getInstance("EUR") } catch (e: Throwable) { android.util.Log.w("CurrencyFormatter", "EUR not available", e) }
    }

    /** Symbole de la devise EUR pour la locale courante. */
    val symbol: String = try { currencyFormat.currency?.symbol ?: "€" } catch (_: Throwable) { "€" }

    /** Formate une valeur monétaire (0 décimale) selon la locale courante. */
    fun format(value: Double): String = try {
        currencyFormat.format(value)
    } catch (_: Throwable) {
        "%.0f €".format(value)
    }
}
