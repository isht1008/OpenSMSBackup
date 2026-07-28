package io.github.isht1008.opensmsbackup.gmail.mirror

fun maskMirrorAccount(account: String): String {
    val separator = account.indexOf('@')
    if (separator <= 0 || separator == account.lastIndex) return "Account hidden"
    val local = account.substring(0, separator)
    val domain = account.substring(separator + 1)
    return "${local.take(1)}***@${domain.take(1)}***"
}