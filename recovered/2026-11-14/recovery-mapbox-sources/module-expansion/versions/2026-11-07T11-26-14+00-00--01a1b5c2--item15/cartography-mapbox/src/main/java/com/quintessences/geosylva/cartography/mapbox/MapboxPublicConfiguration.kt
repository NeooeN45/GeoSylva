package com.quintessences.geosylva.cartography.mapbox

/** Keeps SDK configuration behind the shared engine, including its public-token guard. */
fun configureMapboxPublicToken(token: String?): Boolean {
    if (token.isNullOrBlank() || !token.startsWith("pk.") || token.any(Char::isWhitespace)) return false
    com.mapbox.common.MapboxOptions.accessToken = token
    return true
}
