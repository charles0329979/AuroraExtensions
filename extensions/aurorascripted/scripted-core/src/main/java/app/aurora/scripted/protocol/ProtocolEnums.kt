package app.aurora.scripted.protocol

enum class HttpMethod { GET }

enum class RetryPolicy {
    NONE,
    TRANSIENT_NETWORK,
}

enum class ExpectedContentType {
    ANY,
    HTML,
    JSON,
    IMAGE,
}

enum class ScriptResponseStatus {
    NEED_RESOURCE,
    COMPLETE,
    FAILED,
}

enum class MangaStatus {
    UNKNOWN,
    ONGOING,
    COMPLETED,
    LICENSED,
    PUBLISHING_FINISHED,
    CANCELLED,
    ON_HIATUS,
}
