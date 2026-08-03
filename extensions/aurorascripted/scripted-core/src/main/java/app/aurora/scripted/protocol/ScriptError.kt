package app.aurora.scripted.protocol

enum class ScriptErrorCode {
    INVALID_JSON,
    PROTOCOL_MISMATCH,
    ENVELOPE_MISMATCH,
    UNKNOWN_OPERATION,
    CAPABILITY_DENIED,
    SCHEME_FORBIDDEN,
    HOST_NOT_ALLOWED,
    HEADER_FORBIDDEN,
    HEADER_NOT_ALLOWLISTED,
    LIMIT_EXCEEDED,
    INVALID_URL,
    INVALID_METHOD,
    TIMEOUT,
    NETWORK,
    HTTP_STATUS,
    PAYLOAD_TOO_LARGE,
    MIME_MISMATCH,
    SCRIPT_THROW,
    SCRIPT_TIMEOUT,
    UNSUPPORTED,
}

data class ScriptError(
    val code: ScriptErrorCode,
    val message: String,
    val retryable: Boolean = false,
    val details: Map<String, String> = emptyMap(),
)
