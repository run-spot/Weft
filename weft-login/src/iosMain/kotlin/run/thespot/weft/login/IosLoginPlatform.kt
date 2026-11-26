@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package run.thespot.weft.login

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.value
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreFoundation.kCFAllocatorDefault
import platform.Foundation.NSURL
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.posix.time

/** iOS Keychain-backed storage for Supabase sessions and PKCE verifiers. */
public class IosLoginStorage(
    namespace: String = "default",
) : LoginStorage {
    private val service = "run.thespot.weft.login.${base64Url(sha256(namespace.encodeToByteArray()))}"

    override public suspend fun get(key: String): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = withQuery(key, includeReturnData = true) { query ->
            SecItemCopyMatching(query, result.ptr)
        }
        if (status != errSecSuccess) return@memScoped null
        val data: CFDataRef = result.value?.reinterpret() ?: return@memScoped null
        try {
            val count = CFDataGetLength(data).toInt()
            if (count == 0) return@memScoped ""
            CFDataGetBytePtr(data)?.readBytes(count)?.decodeToString()
        } finally {
            CFRelease(data)
        }
    }

    override public suspend fun put(key: String, value: String) {
        val data = value.encodeToByteArray().toCFData()
        try {
            val status = withCFDictionary(listOf(requireNotNull(kSecValueData) to data)) { attributes ->
                withQuery(key) { query -> SecItemUpdate(query, attributes) }
            }
            if (status == errSecItemNotFound) {
                val addStatus = withQuery(key, valueData = data) { SecItemAdd(it, null) }
                check(addStatus == errSecSuccess) { "Could not store Weft login data in Keychain." }
            } else {
                check(status == errSecSuccess) { "Could not update Weft login data in Keychain." }
            }
        } finally {
            CFRelease(data)
        }
    }

    override public suspend fun remove(key: String) {
        val status = withQuery(key) { SecItemDelete(it) }
        check(status == errSecSuccess || status == errSecItemNotFound) {
            "Could not remove Weft login data from Keychain."
        }
    }

    private inline fun <T> withQuery(
        key: String,
        includeReturnData: Boolean = false,
        valueData: CFDataRef? = null,
        block: (CFDictionaryRef) -> T,
    ): T = withCFString(service) { serviceRef ->
        withCFString(key) { accountRef ->
            val entries = mutableListOf<Pair<CFTypeRef, CFTypeRef>>().apply {
                add(requireNotNull(kSecClass) to requireNotNull(kSecClassGenericPassword))
                add(requireNotNull(kSecAttrService) to serviceRef)
                add(requireNotNull(kSecAttrAccount) to accountRef)
            }
            if (includeReturnData) {
                entries += requireNotNull(kSecReturnData) to requireNotNull(kCFBooleanTrue)
                entries += requireNotNull(kSecMatchLimit) to requireNotNull(kSecMatchLimitOne)
            }
            valueData?.let { entries += requireNotNull(kSecValueData) to it }
            withCFDictionary(entries, block)
        }
    }

    private inline fun <T> withCFString(value: String, block: (CFTypeRef) -> T): T {
        val string = CFStringCreateWithCString(kCFAllocatorDefault, value, kCFStringEncodingUTF8)
            ?: error("Could not create Keychain query string.")
        return try {
            block(string)
        } finally {
            CFRelease(string)
        }
    }

    private inline fun <T> withCFDictionary(
        entries: List<Pair<CFTypeRef, CFTypeRef>>,
        block: (CFDictionaryRef) -> T,
    ): T = memScoped {
        val keys = allocArray<COpaquePointerVar>(entries.size)
        val values = allocArray<COpaquePointerVar>(entries.size)
        entries.forEachIndexed { index, (key, value) ->
            keys[index] = key
            values[index] = value
        }
        val dictionary = CFDictionaryCreate(
            kCFAllocatorDefault,
            keys,
            values,
            entries.size.convert(),
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        ) ?: error("Could not create Keychain query dictionary.")
        try {
            block(dictionary)
        } finally {
            CFRelease(dictionary)
        }
    }

    private fun ByteArray.toCFData(): CFDataRef {
        val bytes = if (isEmpty()) byteArrayOf(0) else this
        return bytes.usePinned { pinned ->
        CFDataCreate(
            kCFAllocatorDefault,
            pinned.addressOf(0).reinterpret<UByteVar>() as CValuesRef<UByteVar>,
            size.convert(),
        )
            ?: error("Could not create Keychain data.")
        }
    }
}

/** Initializes the login plugin with iOS Keychain storage. */
public suspend fun LoginPlugin.initialize(): LoginSession? {
    val client = this as? SupabaseLoginClient
        ?: throw IllegalArgumentException("iOS login initialization requires the WeftLogin implementation.")
    return initialize(IosLoginStorage(client.config.storageNamespace))
}

/** Opens the authorization URL in the system browser. The app receives the configured callback URL. */
public fun LoginAuthorizationRequest.openInSystemBrowser() {
    val url = NSURL.URLWithString(authorizationUrl)
        ?: throw IllegalArgumentException("OAuth authorization URL is invalid.")
    UIApplication.sharedApplication.openURL(url)
}

internal actual fun createLoginHttpClient(): HttpClient = HttpClient(Darwin)

@OptIn(ExperimentalForeignApi::class)
internal actual fun secureRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    val status = bytes.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, size.convert(), pinned.addressOf(0))
    }
    check(status == errSecSuccess) { "Could not generate secure random bytes." }
    return bytes
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun sha256(input: ByteArray): ByteArray {
    val inputBytes = if (input.isEmpty()) byteArrayOf(0) else input
    return memScoped {
        val digest = allocArray<UByteVar>(CC_SHA256_DIGEST_LENGTH)
        inputBytes.usePinned { inputPinned ->
            CC_SHA256(inputPinned.addressOf(0), input.size.convert(), digest as CValuesRef<UByteVar>)
        }
        digest.readBytes(CC_SHA256_DIGEST_LENGTH)
    }
}

internal actual fun platformEpochSeconds(): Long = time(null)
