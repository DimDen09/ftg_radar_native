package com.foodtruckgalaxy.ftg_radar_native

import java.net.URI

internal data class RadarConfig(
    val token: String,
    val endpoint: String,
) {
    companion object {
        fun from(token: String?, endpoint: String?): RadarConfig {
            val normalizedToken = token?.trim().orEmpty()
            require(normalizedToken.isNotEmpty()) { "token must not be empty" }

            val normalizedEndpoint = endpoint?.trim().orEmpty()
            val uri = runCatching { URI(normalizedEndpoint) }.getOrNull()
            require(
                uri != null &&
                    uri.isAbsolute &&
                    uri.scheme.equals("https", ignoreCase = true) &&
                    !uri.host.isNullOrBlank()
            ) { "endpoint must be an absolute HTTPS URL" }

            return RadarConfig(normalizedToken, normalizedEndpoint)
        }
    }
}
