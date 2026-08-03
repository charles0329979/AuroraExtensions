package app.aurora.scripted.protocol

sealed class ParseResult {
    data class Ok(val response: ScriptResponse) : ParseResult()
    data class Err(val error: ScriptError) : ParseResult()
}

/**
 * Strict JSON → [ScriptResponse]. Unknown ResourceRequest fields → INVALID_JSON.
 */
object ScriptResponseParser {
    private val RESOURCE_ALLOWED_KEYS = setOf(
        "resourceId", "url", "method", "headers", "referer",
        "expectedContentType", "timeout", "timeoutMs", "retryPolicy", "sourceId",
    )

    private val RESPONSE_ALLOWED_KEYS = setOf(
        "protocolVersion", "sourceId", "operationId", "requestId",
        "status", "error", "resources", "payload",
    )

    fun parse(json: String): ParseResult {
        val trimmed = json.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return err(ScriptErrorCode.INVALID_JSON, "Not a JSON object")
        }

        val topKeys = ProtocolJson.objectKeys(trimmed)
        val unknownTop = topKeys - RESPONSE_ALLOWED_KEYS
        if (unknownTop.isNotEmpty()) {
            return err(ScriptErrorCode.INVALID_JSON, "Unknown ScriptResponse fields: $unknownTop")
        }

        val protocolVersion = ProtocolJson.extractInt(trimmed, "protocolVersion")
            ?: return err(ScriptErrorCode.INVALID_JSON, "Missing protocolVersion")
        val sourceId = ProtocolJson.extractString(trimmed, "sourceId")
            ?: return err(ScriptErrorCode.INVALID_JSON, "Missing sourceId")
        val operationRaw = ProtocolJson.extractString(trimmed, "operationId")
            ?: return err(ScriptErrorCode.INVALID_JSON, "Missing operationId")
        val operationId = try {
            ScriptedOperation.valueOf(operationRaw)
        } catch (_: Exception) {
            return err(ScriptErrorCode.UNKNOWN_OPERATION, "Unknown operationId: $operationRaw")
        }
        val requestId = ProtocolJson.extractString(trimmed, "requestId")
            ?: return err(ScriptErrorCode.INVALID_JSON, "Missing requestId")
        val statusRaw = ProtocolJson.extractString(trimmed, "status")
            ?: return err(ScriptErrorCode.INVALID_JSON, "Missing status")
        val status = try {
            ScriptResponseStatus.valueOf(statusRaw)
        } catch (_: Exception) {
            return err(ScriptErrorCode.INVALID_JSON, "Unknown status: $statusRaw")
        }

        val error = parseError(ProtocolJson.extractObject(trimmed, "error"))
        if (error is ParseErrorResult.Fail) return err(error.code, error.message)

        val resourcesRaw = ProtocolJson.extractArray(trimmed, "resources") ?: emptyList()
        val resources = mutableListOf<ResourceRequest>()
        for (resObj in resourcesRaw) {
            when (val parsed = parseResource(resObj)) {
                is ResourceParse.Ok -> resources.add(parsed.request)
                is ResourceParse.Fail -> return err(parsed.code, parsed.message)
            }
        }

        val payloadObj = ProtocolJson.extractObject(trimmed, "payload")
        val payload = if (payloadObj != null) {
            when (val p = parsePayload(payloadObj)) {
                is PayloadParse.Ok -> p.payload
                is PayloadParse.Fail -> return err(p.code, p.message)
            }
        } else {
            null
        }

        return ParseResult.Ok(
            ScriptResponse(
                protocolVersion = protocolVersion,
                sourceId = sourceId,
                operationId = operationId,
                requestId = requestId,
                status = status,
                error = (error as? ParseErrorResult.Ok)?.error,
                resources = resources,
                payload = payload,
            ),
        )
    }

    private fun parseResource(obj: String): ResourceParse {
        val keys = ProtocolJson.objectKeys(obj)
        val unknown = keys - RESOURCE_ALLOWED_KEYS
        if (unknown.isNotEmpty()) {
            return ResourceParse.Fail(ScriptErrorCode.INVALID_JSON, "Unknown ResourceRequest fields: $unknown")
        }

        val resourceId = ProtocolJson.extractString(obj, "resourceId")
            ?: return ResourceParse.Fail(ScriptErrorCode.INVALID_JSON, "Missing resourceId")
        val url = ProtocolJson.extractString(obj, "url")
            ?: return ResourceParse.Fail(ScriptErrorCode.INVALID_JSON, "Missing url")
        val methodRaw = ProtocolJson.extractString(obj, "method") ?: "GET"
        val method = try {
            HttpMethod.valueOf(methodRaw)
        } catch (_: Exception) {
            return ResourceParse.Fail(ScriptErrorCode.INVALID_METHOD, "Invalid method: $methodRaw")
        }
        val headersObj = ProtocolJson.extractObject(obj, "headers")
        val headers = if (headersObj != null) ProtocolJson.extractStringMap(headersObj) else emptyMap()
        val referer = ProtocolJson.extractNullableString(obj, "referer")
        val ectRaw = ProtocolJson.extractString(obj, "expectedContentType") ?: "ANY"
        val expectedContentType = try {
            ExpectedContentType.valueOf(ectRaw)
        } catch (_: Exception) {
            return ResourceParse.Fail(ScriptErrorCode.INVALID_JSON, "Invalid expectedContentType")
        }
        val timeoutMs = ProtocolJson.extractLong(obj, "timeoutMs")
            ?: ProtocolJson.extractLong(obj, "timeout")
        val retryRaw = ProtocolJson.extractString(obj, "retryPolicy") ?: "TRANSIENT_NETWORK"
        val retryPolicy = try {
            RetryPolicy.valueOf(retryRaw)
        } catch (_: Exception) {
            return ResourceParse.Fail(ScriptErrorCode.INVALID_JSON, "Invalid retryPolicy")
        }
        val sourceId = ProtocolJson.extractString(obj, "sourceId")
            ?: return ResourceParse.Fail(ScriptErrorCode.INVALID_JSON, "Missing resource sourceId")

        return ResourceParse.Ok(
            ResourceRequest(
                resourceId = resourceId,
                url = url,
                method = method,
                headers = headers,
                referer = referer,
                expectedContentType = expectedContentType,
                timeoutMs = timeoutMs,
                retryPolicy = retryPolicy,
                sourceId = sourceId,
            ),
        )
    }

    private fun parsePayload(obj: String): PayloadParse {
        val pagesRaw = ProtocolJson.extractArray(obj, "pages")
        val pages = pagesRaw?.map { pageObj ->
            val index = ProtocolJson.extractInt(pageObj, "index")
                ?: return PayloadParse.Fail(ScriptErrorCode.INVALID_JSON, "Page missing index")
            val imageUrl = ProtocolJson.extractString(pageObj, "imageUrl")
                ?: return PayloadParse.Fail(ScriptErrorCode.INVALID_JSON, "Page missing imageUrl")
            val referer = ProtocolJson.extractNullableString(pageObj, "referer")
            PagePayload(index = index, imageUrl = imageUrl, referer = referer)
        }

        val mangaObj = ProtocolJson.extractObject(obj, "manga")
        val manga = mangaObj?.let { parseManga(it) }

        val mangasRaw = ProtocolJson.extractArray(obj, "mangas")
        val hasNext = ProtocolJson.extractBoolean(obj, "hasNextPage")
        val mangasPage = if (mangasRaw != null) {
            MangasPagePayload(
                mangas = mangasRaw.map { parseManga(it) },
                hasNextPage = hasNext ?: false,
            )
        } else {
            null
        }

        val chaptersRaw = ProtocolJson.extractArray(obj, "chapters")
        val chapters = chaptersRaw?.map { ch ->
            ChapterPayload(
                url = ProtocolJson.extractString(ch, "url")
                    ?: return PayloadParse.Fail(ScriptErrorCode.INVALID_JSON, "Chapter missing url"),
                name = ProtocolJson.extractString(ch, "name")
                    ?: return PayloadParse.Fail(ScriptErrorCode.INVALID_JSON, "Chapter missing name"),
                chapterNumber = ProtocolJson.extractLong(ch, "chapterNumber")?.toFloat(),
                dateUpload = ProtocolJson.extractLong(ch, "dateUpload"),
                scanlator = ProtocolJson.extractNullableString(ch, "scanlator"),
            )
        }

        return PayloadParse.Ok(
            ScriptPayload(
                mangasPage = mangasPage,
                manga = manga,
                chapters = chapters,
                pages = pages,
            ),
        )
    }

    private fun parseManga(obj: String): MangaPayload {
        return MangaPayload(
            url = ProtocolJson.extractString(obj, "url") ?: "",
            title = ProtocolJson.extractString(obj, "title") ?: "",
            thumbnailUrl = ProtocolJson.extractNullableString(obj, "thumbnailUrl"),
            author = ProtocolJson.extractNullableString(obj, "author"),
            artist = ProtocolJson.extractNullableString(obj, "artist"),
            description = ProtocolJson.extractNullableString(obj, "description"),
            status = try {
                MangaStatus.valueOf(ProtocolJson.extractString(obj, "status") ?: "UNKNOWN")
            } catch (_: Exception) {
                MangaStatus.UNKNOWN
            },
            initialized = ProtocolJson.extractBoolean(obj, "initialized") ?: false,
        )
    }

    private fun parseError(obj: String?): ParseErrorResult {
        if (obj == null) return ParseErrorResult.Ok(null)
        val codeRaw = ProtocolJson.extractString(obj, "code")
            ?: return ParseErrorResult.Fail(ScriptErrorCode.INVALID_JSON, "error missing code")
        val code = try {
            ScriptErrorCode.valueOf(codeRaw)
        } catch (_: Exception) {
            return ParseErrorResult.Fail(ScriptErrorCode.INVALID_JSON, "Unknown error code: $codeRaw")
        }
        val message = ProtocolJson.extractString(obj, "message") ?: ""
        val retryable = ProtocolJson.extractBoolean(obj, "retryable") ?: false
        return ParseErrorResult.Ok(ScriptError(code = code, message = message, retryable = retryable))
    }

    private fun err(code: ScriptErrorCode, message: String) =
        ParseResult.Err(ScriptError(code, message))

    private sealed class ResourceParse {
        data class Ok(val request: ResourceRequest) : ResourceParse()
        data class Fail(val code: ScriptErrorCode, val message: String) : ResourceParse()
    }

    private sealed class PayloadParse {
        data class Ok(val payload: ScriptPayload) : PayloadParse()
        data class Fail(val code: ScriptErrorCode, val message: String) : PayloadParse()
    }

    private sealed class ParseErrorResult {
        data class Ok(val error: ScriptError?) : ParseErrorResult()
        data class Fail(val code: ScriptErrorCode, val message: String) : ParseErrorResult()
    }
}
