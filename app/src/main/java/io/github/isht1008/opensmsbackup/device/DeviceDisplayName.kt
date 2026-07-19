package io.github.isht1008.opensmsbackup.device

object DeviceDisplayName {
    fun initialBaseName(
        marketingName: String?,
        manufacturer: String?,
        model: String?
    ): String {
        marketingName.clean()?.let { return it }
        val cleanModel = model.clean()
        val cleanManufacturer = manufacturer.clean()
        if (cleanModel != null) {
            return if (
                cleanManufacturer != null &&
                !cleanModel.startsWith(cleanManufacturer, ignoreCase = true)
            ) "$cleanManufacturer $cleanModel" else cleanModel
        }
        return "Android Phone"
    }

    fun withPhoneSuffix(baseName: String, phoneNumber: String?): String {
        val suffix = phoneNumber.orEmpty().filter(Char::isDigit).takeLast(5)
        return if (suffix.isBlank()) baseName.trim() else "${baseName.trim()} ($suffix)"
    }

    fun gmailLabelSegment(
        displayName: String,
        primaryPhoneNumber: String?,
        deviceId: String,
        requireStableSuffix: Boolean
    ): String {
        val readable = withPhoneSuffix(displayName, primaryPhoneNumber)
        val withIdentity = if (requireStableSuffix) {
            "$readable • ${deviceId.replace("-", "").take(4).uppercase()}"
        } else readable
        return sanitizeLabelSegment(withIdentity)
    }

    fun sanitizeLabelSegment(value: String): String =
        value.replace(Regex("\\+?\\d[\\d\\s().-]{4,}\\d")) { match ->
                val digits = match.value.filter(Char::isDigit)
                if (digits.length > 5) "(${digits.takeLast(5)})" else match.value
            }
            .replace(Regex("[\\p{Cc}/\\\\]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim().take(80)
            .ifBlank { "Android Phone" }

    fun maskedNumber(number: String?): String? {
        val digits = number.orEmpty().filter(Char::isDigit)
        return digits.takeIf { it.isNotBlank() }
            ?.let { "•••••${it.takeLast(5)}" }
    }

    private fun String?.clean(): String? =
        this?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotBlank() }
}
