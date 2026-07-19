package io.github.isht1008.opensmsbackup.sms

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

sealed interface NormalizedSmsAddress {
    val canonical: String
    val original: String?
    data class PhoneNumber(override val canonical: String, override val original: String) : NormalizedSmsAddress
    data class SenderId(override val canonical: String, override val original: String) : NormalizedSmsAddress
    data class ShortCode(override val canonical: String, override val original: String) : NormalizedSmsAddress
    data class Unknown(override val canonical: String, override val original: String?) : NormalizedSmsAddress
}

class SmsAddressNormalizer(
    private val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()
) {
    fun normalize(address: String?, defaultRegion: String): NormalizedSmsAddress {
        val original = address?.trim().orEmpty()
        if (original.isBlank()) return NormalizedSmsAddress.Unknown("<unknown-address>", address)
        val collapsed = original.replace(Regex("\\s+"), " ")
        if (collapsed.any(Char::isLetter)) {
            return NormalizedSmsAddress.SenderId(collapsed.uppercase(Locale.ROOT), original)
        }
        val digits = collapsed.filter(Char::isDigit)
        if (digits.length in 1..6) {
            return NormalizedSmsAddress.ShortCode(digits, original)
        }
        return try {
            val parsed = phoneUtil.parse(collapsed, defaultRegion.uppercase(Locale.ROOT))
            if (phoneUtil.isValidNumber(parsed)) {
                NormalizedSmsAddress.PhoneNumber(
                    phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164),
                    original
                )
            } else {
                NormalizedSmsAddress.Unknown(collapsed.uppercase(Locale.ROOT), original)
            }
        } catch (_: Exception) {
            NormalizedSmsAddress.Unknown(collapsed.uppercase(Locale.ROOT), original)
        }
    }

    fun isSupportedRegion(region: String): Boolean =
        PhoneNumberUtil.getInstance().supportedRegions.contains(region.uppercase(Locale.ROOT))
}
