package app.aurora.scripted.protocol

sealed class ValidatedScriptOutcome {
    data class NeedResources(val instructions: List<ResourceInstruction>) : ValidatedScriptOutcome()
    data class Complete(val payload: ScriptPayload) : ValidatedScriptOutcome()
    data class Failed(val error: ScriptError) : ValidatedScriptOutcome()
}

object ProtocolValidator {
    fun validate(
        manifest: ScriptedSourceManifest,
        request: ScriptRequest,
        response: ScriptResponse,
    ): ValidatedScriptOutcome {
        if (response.protocolVersion != SCRIPTED_PROTOCOL_VERSION ||
            request.protocolVersion != SCRIPTED_PROTOCOL_VERSION ||
            manifest.protocolVersion != SCRIPTED_PROTOCOL_VERSION
        ) {
            return fail(ScriptErrorCode.PROTOCOL_MISMATCH, "protocolVersion must be $SCRIPTED_PROTOCOL_VERSION")
        }

        if (response.sourceId != request.sourceId ||
            response.sourceId != manifest.sourceId ||
            response.operationId != request.operationId ||
            response.requestId != request.requestId
        ) {
            return fail(ScriptErrorCode.ENVELOPE_MISMATCH, "Envelope ids do not match open ScriptRequest")
        }

        if (request.operationId !in manifest.capabilities.operations) {
            return fail(ScriptErrorCode.CAPABILITY_DENIED, "Operation not in capabilities: ${request.operationId}")
        }

        when (response.status) {
            ScriptResponseStatus.FAILED -> {
                val err = response.error
                    ?: ScriptError(ScriptErrorCode.SCRIPT_THROW, "FAILED without error")
                return ValidatedScriptOutcome.Failed(err)
            }
            ScriptResponseStatus.NEED_RESOURCE -> {
                return validateNeedResource(manifest, response)
            }
            ScriptResponseStatus.COMPLETE -> {
                return validateComplete(manifest, request, response)
            }
        }
    }

    private fun validateNeedResource(
        manifest: ScriptedSourceManifest,
        response: ScriptResponse,
    ): ValidatedScriptOutcome {
        val limits = manifest.limits
        if (response.resources.isEmpty()) {
            return fail(ScriptErrorCode.INVALID_JSON, "NEED_RESOURCE requires non-empty resources")
        }
        if (response.resources.size > limits.maxResourcesPerResponse) {
            return fail(
                ScriptErrorCode.LIMIT_EXCEEDED,
                "Too many resources: ${response.resources.size}",
            )
        }

        val instructions = mutableListOf<ResourceInstruction>()
        for (res in response.resources) {
            when (val built = buildInstruction(manifest, res)) {
                is BuildResult.Ok -> instructions.add(built.instruction)
                is BuildResult.Fail -> return ValidatedScriptOutcome.Failed(built.error)
            }
        }
        return ValidatedScriptOutcome.NeedResources(instructions)
    }

    private fun validateComplete(
        manifest: ScriptedSourceManifest,
        request: ScriptRequest,
        response: ScriptResponse,
    ): ValidatedScriptOutcome {
        if (response.resources.isNotEmpty()) {
            return fail(ScriptErrorCode.INVALID_JSON, "COMPLETE must not include resources")
        }
        val payload = response.payload
            ?: return fail(ScriptErrorCode.INVALID_JSON, "COMPLETE requires payload")

        when (request.operationId) {
            ScriptedOperation.PAGES -> {
                val pages = payload.pages
                if (pages == null || pages.isEmpty()) {
                    return fail(ScriptErrorCode.INVALID_JSON, "PAGES COMPLETE requires non-empty pages")
                }
                if (pages.size > manifest.limits.maxPages) {
                    return fail(ScriptErrorCode.LIMIT_EXCEEDED, "Too many pages: ${pages.size}")
                }
                for (page in pages) {
                    val urlErr = UrlSchemePolicy.check(
                        url = page.imageUrl,
                        baseUrl = manifest.baseUrl,
                        allowedHosts = manifest.capabilities.allowedHosts,
                        allowHttp = manifest.capabilities.allowHttp,
                        maxUrlLength = manifest.limits.maxUrlLength,
                    )
                    if (urlErr != null) return ValidatedScriptOutcome.Failed(urlErr)
                    if (page.referer != null) {
                        val refErr = UrlSchemePolicy.check(
                            url = page.referer,
                            baseUrl = manifest.baseUrl,
                            allowedHosts = manifest.capabilities.allowedHosts,
                            allowHttp = manifest.capabilities.allowHttp,
                            maxUrlLength = manifest.limits.maxUrlLength,
                        )
                        if (refErr != null) return ValidatedScriptOutcome.Failed(refErr)
                    }
                }
            }
            ScriptedOperation.POPULAR, ScriptedOperation.LATEST, ScriptedOperation.SEARCH -> {
                val page = payload.mangasPage
                if (page == null || page.mangas.isEmpty()) {
                    return fail(ScriptErrorCode.INVALID_JSON, "Catalogue COMPLETE requires mangas")
                }
                if (page.mangas.size > manifest.limits.maxMangasPerPage) {
                    return fail(ScriptErrorCode.LIMIT_EXCEEDED, "Too many mangas")
                }
            }
            ScriptedOperation.DETAILS -> {
                if (payload.manga == null) {
                    return fail(ScriptErrorCode.INVALID_JSON, "DETAILS COMPLETE requires manga")
                }
            }
            ScriptedOperation.CHAPTERS -> {
                val chapters = payload.chapters
                if (chapters == null || chapters.isEmpty()) {
                    return fail(ScriptErrorCode.INVALID_JSON, "CHAPTERS COMPLETE requires chapters")
                }
                if (chapters.size > manifest.limits.maxChapters) {
                    return fail(ScriptErrorCode.LIMIT_EXCEEDED, "Too many chapters")
                }
            }
        }

        return ValidatedScriptOutcome.Complete(payload)
    }

    private fun buildInstruction(
        manifest: ScriptedSourceManifest,
        res: ResourceRequest,
    ): BuildResult {
        if (res.sourceId != manifest.sourceId) {
            return BuildResult.Fail(
                ScriptError(ScriptErrorCode.ENVELOPE_MISMATCH, "resource.sourceId mismatch"),
            )
        }
        if (res.method != HttpMethod.GET) {
            return BuildResult.Fail(
                ScriptError(ScriptErrorCode.INVALID_METHOD, "Only GET allowed in v2"),
            )
        }

        val urlErr = UrlSchemePolicy.check(
            url = res.url,
            baseUrl = manifest.baseUrl,
            allowedHosts = manifest.capabilities.allowedHosts,
            allowHttp = manifest.capabilities.allowHttp,
            maxUrlLength = manifest.limits.maxUrlLength,
        )
        if (urlErr != null) return BuildResult.Fail(urlErr)

        if (res.referer != null) {
            val refErr = UrlSchemePolicy.check(
                url = res.referer,
                baseUrl = manifest.baseUrl,
                allowedHosts = manifest.capabilities.allowedHosts,
                allowHttp = manifest.capabilities.allowHttp,
                maxUrlLength = manifest.limits.maxUrlLength,
            )
            if (refErr != null) return BuildResult.Fail(refErr)
        }

        val timeout = when {
            res.timeoutMs == null -> manifest.limits.defaultTimeoutMs
            res.timeoutMs < 1L -> {
                return BuildResult.Fail(
                    ScriptError(ScriptErrorCode.LIMIT_EXCEEDED, "timeout must be >= 1"),
                )
            }
            res.timeoutMs > manifest.limits.maxTimeoutMs -> {
                return BuildResult.Fail(
                    ScriptError(
                        ScriptErrorCode.LIMIT_EXCEEDED,
                        "timeout exceeds maxTimeoutMs: ${res.timeoutMs}",
                    ),
                )
            }
            else -> res.timeoutMs
        }

        when (
            val headers = HeaderPolicy.sanitize(
                jsHeaders = res.headers,
                referer = res.referer,
                limits = manifest.limits,
                defaultReferer = manifest.defaultReferer,
                userAgentOverride = manifest.userAgent,
            )
        ) {
            is HeaderSanitizeResult.Err -> return BuildResult.Fail(headers.error)
            is HeaderSanitizeResult.Ok -> {
                // Never include Host from JS (already stripped); do not add Host manually.
                val sanitized = headers.headers.filterKeys { !it.equals("Host", ignoreCase = true) }
                return BuildResult.Ok(
                    ResourceInstruction(
                        resourceId = res.resourceId,
                        sourceId = res.sourceId,
                        url = res.url,
                        method = res.method,
                        headers = sanitized,
                        expectedContentType = res.expectedContentType,
                        timeoutMs = timeout,
                        retryPolicy = res.retryPolicy,
                        maxResponseBodyBytes = manifest.limits.maxResponseBodyBytes,
                    ),
                )
            }
        }
    }

    private fun fail(code: ScriptErrorCode, message: String) =
        ValidatedScriptOutcome.Failed(ScriptError(code, message))

    private sealed class BuildResult {
        data class Ok(val instruction: ResourceInstruction) : BuildResult()
        data class Fail(val error: ScriptError) : BuildResult()
    }
}
