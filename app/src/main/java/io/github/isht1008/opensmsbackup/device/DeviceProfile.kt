package io.github.isht1008.opensmsbackup.device

data class DeviceProfile(
    val deviceId: String,
    val manufacturer: String,
    val model: String,
    val marketingName: String?,
    val androidVersion: String,
    val primaryPhoneNumber: String?,
    val secondaryPhoneNumber: String?,
    val displayName: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    val shortId: String
        get() = deviceId.replace("-", "").take(4).uppercase()

    val maskedPrimaryPhoneNumber: String?
        get() = DeviceDisplayName.maskedNumber(primaryPhoneNumber)
}
