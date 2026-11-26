package run.thespot.weft.live

/** Lifecycle state rendered by a live activity surface. */
public enum class LiveActivityStatus {
    Active,
    Completed,
    Failed,
    Cancelled,
}

/** Whether this platform can accept live-activity operations at the moment. */
public enum class LiveActivityAvailability {
    Available,
    PermissionRequired,
    Disabled,
    Unsupported,
}

/** Platform-neutral failure category reported by a live-activity provider. */
public enum class LiveActivityFailureReason {
    Unsupported,
    PermissionRequired,
    Disabled,
    NotConfigured,
    InvalidState,
    PlatformFailure,
}

/**
 * Progress for a live activity.
 *
 * The percentage is calculated with integer arithmetic and is therefore deterministic for every
 * valid [Long] value, including totals close to [Long.MAX_VALUE].
 */
public data class LiveActivityProgress(
    public val current: Long,
    public val total: Long,
) {
    init {
        require(total > 0) { "progress total must be greater than zero" }
        require(current in 0..total) { "progress current must be between zero and total" }
    }

    public val percentage: Int
        get() = floorPercentageWithoutOverflow(current, total)
}

/** A complete mutable snapshot identified by [id]. */
public data class LiveActivity(
    public val id: String,
    public val title: String,
    public val subtitle: String? = null,
    public val status: LiveActivityStatus = LiveActivityStatus.Active,
    public val progress: LiveActivityProgress? = null,
    public val startedAtEpochMillis: Long? = null,
    public val endsAtEpochMillis: Long? = null,
    public val deepLink: String? = null,
) {
    init {
        require(LIVE_ACTIVITY_ID_PATTERN.matches(id)) {
            "live activity id must be 1-128 ASCII letters, digits, '.', '_', ':', or '-'"
        }
        require(title.isNotBlank() && title.utf8Length() <= 128) {
            "live activity title must be non-blank and at most 128 UTF-8 bytes"
        }
        require(subtitle == null || subtitle.utf8Length() <= 512) {
            "live activity subtitle must be at most 512 UTF-8 bytes"
        }
        require(deepLink == null || isValidDeepLink(deepLink)) {
            "deepLink must be an absolute hierarchical URI of at most 2048 UTF-8 bytes"
        }
        require(startedAtEpochMillis == null || startedAtEpochMillis >= 0) {
            "live activity start time must be a non-negative epoch timestamp"
        }
        require(endsAtEpochMillis == null || endsAtEpochMillis >= 0) {
            "live activity end time must be a non-negative epoch timestamp"
        }
        if (startedAtEpochMillis != null && endsAtEpochMillis != null) {
            require(endsAtEpochMillis >= startedAtEpochMillis) {
                "live activity end time must not precede its start time"
            }
        }
        if (status == LiveActivityStatus.Completed && progress != null) {
            require(progress.current == progress.total) {
                "completed live activity progress must equal its total"
            }
        }
    }
}

/** Failure raised when a live-activity operation cannot be accepted. */
public class LiveActivityException(
    public val reason: LiveActivityFailureReason,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

private val LIVE_ACTIVITY_ID_PATTERN = Regex("[A-Za-z0-9._:-]{1,128}")
private val LIVE_ACTIVITY_SCHEME_PATTERN = Regex("[A-Za-z][A-Za-z0-9+.-]*")

internal fun String.utf8Length(): Int = encodeToByteArray().size

/**
 * Accept only an absolute hierarchical URI with a non-empty authority. This shared check is kept
 * deliberately small; Android and Apple adapters repeat it with their native URI parsers before
 * passing a URL to a platform API.
 */
internal fun isValidDeepLink(value: String): Boolean {
    if (value.isEmpty() || value.utf8Length() > 2048) return false
    if (value.any { it.isWhitespace() || it.code < 0x20 || it.code == 0x7f || it == '\\' }) return false

    var percentIndex = value.indexOf('%')
    while (percentIndex >= 0) {
        if (percentIndex + 2 >= value.length ||
            !value[percentIndex + 1].isHexDigit() ||
            !value[percentIndex + 2].isHexDigit()
        ) {
            return false
        }
        percentIndex = value.indexOf('%', percentIndex + 1)
    }

    val separator = value.indexOf("://")
    if (separator <= 0 || !LIVE_ACTIVITY_SCHEME_PATTERN.matches(value.substring(0, separator))) {
        return false
    }

    val authorityStart = separator + 3
    if (authorityStart >= value.length) return false
    val authorityEnd = value.indexOfAny(charArrayOf('/', '?', '#'), authorityStart)
    val authority = if (authorityEnd == -1) {
        value.substring(authorityStart)
    } else {
        value.substring(authorityStart, authorityEnd)
    }
    if (!isValidAuthority(authority)) return false

    val componentStart = if (authorityEnd == -1) value.length else authorityEnd
    val queryStart = value.indexOf('?', authorityStart)
    val fragmentStart = value.indexOf('#', authorityStart)
    val pathEnd = listOfNotNull(
        queryStart.takeUnless { it == -1 },
        fragmentStart.takeUnless { it == -1 },
    ).minOrNull() ?: value.length
    if (!isValidUriComponent(value.substring(componentStart, pathEnd), allowQuestion = false)) {
        return false
    }

    if (queryStart >= 0 && (fragmentStart < 0 || queryStart < fragmentStart)) {
        val queryEnd = if (fragmentStart >= 0) fragmentStart else value.length
        if (!isValidUriComponent(value.substring(queryStart + 1, queryEnd), allowQuestion = true)) return false
    }
    if (fragmentStart >= 0 && !isValidUriComponent(value.substring(fragmentStart + 1), allowQuestion = true)) {
        return false
    }
    return true
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun isValidAuthority(authority: String): Boolean {
    if (authority.isEmpty() || authority.any { !it.isAuthorityChar() }) return false
    val hostPort = authority.substringAfterLast('@')
    if (hostPort.isEmpty()) return false

    if (hostPort.startsWith('[')) {
        val closingBracket = hostPort.indexOf(']')
        if (closingBracket <= 1) return false
        val suffix = hostPort.substring(closingBracket + 1)
        return suffix.isEmpty() ||
            (suffix.startsWith(':') && suffix.length > 1 && suffix.drop(1).all(Char::isDigit))
    }

    val firstColon = hostPort.indexOf(':')
    if (firstColon == -1) return true
    if (hostPort.indexOf(':', firstColon + 1) >= 0) return false
    val host = hostPort.substring(0, firstColon)
    val port = hostPort.substring(firstColon + 1)
    return host.isNotEmpty() && port.isNotEmpty() && port.all(Char::isDigit)
}

private fun Char.isAuthorityChar(): Boolean =
    isUnreserved() || isSubDelimiter() || this in ":@[]%"

private fun isValidUriComponent(value: String, allowQuestion: Boolean): Boolean =
    value.all {
        it.isUnreserved() || it.isSubDelimiter() || it in ":@/%" || (allowQuestion && it == '?')
    }

private fun Char.isUnreserved(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this in "-._~"

private fun Char.isSubDelimiter(): Boolean = this in "!$&'()*+,;="

/** Exact floor(current * 100 / total), without overflowing a Long or using floating point. */
internal fun floorPercentageWithoutOverflow(current: Long, total: Long): Int {
    if (current == 0L) return 0
    if (current == total) return 100

    var low = 0L
    var high = 100L
    while (low < high) {
        val candidate = (low + high + 1) / 2
        if (ceilProductDividedByHundred(candidate, total) <= current) {
            low = candidate
        } else {
            high = candidate - 1
        }
    }
    return low.toInt()
}

/** Returns ceil(multiplier * value / 100) for multiplier in 0..100 without overflow. */
private fun ceilProductDividedByHundred(multiplier: Long, value: Long): Long {
    val whole = value / 100L
    val remainder = value % 100L
    val wholeProduct = multiplier * whole
    val remainderProduct = multiplier * remainder
    return wholeProduct + (remainderProduct + 99L) / 100L
}
