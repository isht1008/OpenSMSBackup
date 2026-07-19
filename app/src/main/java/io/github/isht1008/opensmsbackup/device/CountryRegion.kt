package io.github.isht1008.opensmsbackup.device

import io.github.isht1008.opensmsbackup.sms.SmsAddressNormalizer
import java.util.Locale

object CountryRegion {
    fun initial(locale: Locale = Locale.getDefault()): String {
        val candidate = locale.country.uppercase(Locale.ROOT)
        return candidate.takeIf { SmsAddressNormalizer().isSupportedRegion(it) } ?: "US"
    }

    fun validated(value: String): String? {
        val normalized = value.trim().uppercase(Locale.ROOT)
        return normalized.takeIf {
            it.length == 2 && SmsAddressNormalizer().isSupportedRegion(it)
        }
    }
}
