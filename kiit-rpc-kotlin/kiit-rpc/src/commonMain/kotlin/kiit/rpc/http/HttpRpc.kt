package kiit.rpc.http

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.content.TextContent
import io.ktor.http.encodeURLParameter
import kiit.call.Identity
import kiit.codes.Err
import kiit.codes.Failed
import kiit.codes.Passed
import kiit.codes.Unserved
import kiit.inputs.Args
import kiit.inputs.ArgsMap
import kiit.inputs.ListMap
import kiit.inputs.Meta
import kiit.inputs.MetaMap
import kiit.requests.Content
import kiit.requests.ContentData
import kiit.requests.ContentFile
import kiit.requests.ContentText
import kiit.requests.ContentType
import kiit.requests.ContentTypes
import kiit.requests.Verb
import kiit.result.Failure
import kiit.result.Outcome
import kiit.result.Success
import kiit.rpc.Auth
import kiit.rpc.Body
import kiit.rpc.Policies
import kiit.rpc.RpcClient
import kiit.rpc.RpcOptions
import kiit.rpc.RpcPolicy
import kiit.rpc.RpcRequest
import kiit.rpc.RpcResponse
import kiit.rpc.RpcSettings
import kiit.rpc.StatusConverter
import kotlinx.coroutines.CancellationException
import kotlin.io.encoding.Base64
import io.ktor.http.ContentType as KtorContentType
import io.ktor.http.HttpMethod as KtorHttpMethod

private const val CALLER_ID_HEADER = "X-Caller-Id"

/**
 * Ktor-backed [RpcClient]. Every call funnels through [execute], the one place the [Policy]
 * chain runs, the actual network call happens (in [performCall]), and the response's
 * [kiit.codes.Status] gets resolved via [statusConverter].
 *
 * [engine] swaps just the Ktor engine, [RpcSettings]' timeouts/redirects still apply on top of
 * it. [client] is the heavier escape hatch: a fully pre-built `HttpClient`, used exactly as
 * given, e.g. with `HttpCache` or other plugins installed, or shared across several libraries.
 * `RpcSettings`' timeout/redirect fields are not applied to a supplied [client], the caller
 * already configured it.
 *
 * Implements [AutoCloseable]: [close] releases the underlying Ktor `HttpClient` (connection pool,
 * engine threads). Matters for a long-lived, app-scoped instance on shutdown, DI teardown, or in
 * tests. Closing an instance that never made a call is a no-op, it doesn't build a client just
 * to tear it down. A caller-supplied [client] is never closed here, it may be shared elsewhere in
 * the caller's app, so its lifecycle stays the caller's responsibility.
 */
class HttpRpc(
    private val settings: RpcSettings,
    private val policies: List<RpcPolicy> = emptyList(),
    statusConverter: StatusConverter? = null,
    private val engine: HttpClientEngine? = null,
    client: HttpClient? = null,
) : RpcClient, AutoCloseable {
    override val callerId: Identity get() = settings.callerId

    private val statusConverter: StatusConverter =
        statusConverter ?: KiitStatusConverter(parseStatusFromBody = settings.parseStatusFromBody)

    private val suppliedClient: HttpClient? = client
    private val clientLazy: Lazy<HttpClient> = lazy { suppliedClient ?: buildClient() }
    private val resolvedClient: HttpClient get() = clientLazy.value

    private fun buildClient(): HttpClient {
        val customEngine = engine
        return if (customEngine != null) {
            HttpClient(customEngine) { applyTimeoutAndRedirects() }
        } else {
            HttpClient { applyTimeoutAndRedirects() }
        }
    }

    override fun close() {
        if (suppliedClient == null && clientLazy.isInitialized()) resolvedClient.close()
    }

    private fun HttpClientConfig<*>.applyTimeoutAndRedirects() {
        install(HttpTimeout) {
            requestTimeoutMillis = settings.requestTimeoutMillis
            connectTimeoutMillis = settings.connectTimeoutMillis
            socketTimeoutMillis = settings.socketTimeoutMillis
        }
        followRedirects = settings.followRedirects
    }

    /**
     * Merges [RpcSettings]' client-wide defaults into [request] before the policy chain runs, so
     * a [RpcPolicy] sees the almost-final request: base URL already joined with query args,
     * headers already merged with `defaultMeta`/content-type/auth/caller id. This matches
     * how Ktor's own `Logging` plugin sees a request, after `DefaultRequest` resolves, not before.
     */
    private fun resolveRequest(request: RpcRequest): RpcRequest {
        val url = buildUrl(request.url, request.args)
        val auth = request.auth ?: settings.defaultAuth
        val meta = mergedMeta(request.meta, request.data, auth, request.callerId)
        return request.copy(url = url, args = ArgsMap(ListMap()), meta = meta, auth = auth)
    }

    override suspend fun execute(request: RpcRequest): Outcome<RpcResponse> {
        val effectiveData = if (request.verb == Verb.Get) null else request.data
        val multipart = effectiveData as? Body.MultiPart
        val resolved = resolveRequest(request.copy(data = effectiveData))
        val pipeline = Policies.chain(policies) { req -> performCall(req, multipart) }
        return pipeline(resolved)
    }

    /**
     * Different Ktor engines throw different exception types for the same conceptual failure
     * (connection refused, DNS failure, TLS error), so a broad catch is the correct choice here,
     * not an oversight. [CancellationException] is excluded first, so cancelling the caller's
     * coroutine is never mistaken for a failed call.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun performCall(request: RpcRequest, multipart: Body.MultiPart?): Outcome<RpcResponse> =
        try {
            val response =
                resolvedClient.request(request.url) {
                    method = request.verb.toKtorMethod()
                    request.meta.keys().forEach { key -> header(key, request.meta.get(key)?.toString() ?: "") }
                    applyTimeoutOverride(request.options)
                    applyBody(request.data, multipart)
                }
            toOutcome(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Failure(Err.ex(e), Unserved.UNEXPECTED)
        }

    private fun buildUrl(url: String, args: Args): String {
        val resolved = resolveUrl(url)
        if (args.keys().isEmpty()) return resolved
        val builder = URLBuilder(resolved)
        args.keys().forEach { key -> args.getAll(key).forEach { value -> builder.parameters.append(key, value) } }
        return builder.buildString()
    }

    /** Joins [url] onto [RpcSettings.baseUrl], unless [url] is already absolute. */
    private fun resolveUrl(url: String): String {
        val base = settings.baseUrl
        return if (base == null || url.isAbsolute()) url else "${base.trimEnd('/')}/${url.trimStart('/')}"
    }

    private fun String.isAbsolute(): Boolean = startsWith("http://") || startsWith("https://")

    /** Order: `defaultMeta`, then the call's own `meta` (overrides defaults), then content-type/auth/caller id. */
    private fun mergedMeta(
        requestMeta: Meta,
        data: Body?,
        auth: Auth?,
        callerId: Identity,
    ): Meta {
        val merged = LinkedHashMap<String, String>()
        val defaults = settings.defaultMeta
        defaults.keys().forEach { key -> merged[key] = defaults.get(key)?.toString() ?: "" }
        requestMeta.keys().forEach { key -> merged[key] = requestMeta.get(key)?.toString() ?: "" }
        contentTypeFor(data)?.let { merged[HttpHeaders.ContentType] = it }
        authHeader(auth)?.let { (key, value) -> merged[key] = value }
        merged[CALLER_ID_HEADER] = callerId.id
        return MetaMap(ListMap(merged.toList()))
    }

    /** Multipart's Content-Type (with boundary) is set by Ktor itself, see [buildMultiPartBody]. */
    private fun contentTypeFor(body: Body?): String? =
        when (body) {
            null, is Body.MultiPart -> null
            is Body.FormData -> "application/x-www-form-urlencoded"
            is Body.RawContent -> "text/plain"
            is Body.JsonContent -> "application/json"
        }

    private fun authHeader(auth: Auth?): Pair<String, String>? =
        when (auth) {
            null -> null
            is Auth.Basic -> HttpHeaders.Authorization to basicAuthValue(auth)
            is Auth.Bearer -> HttpHeaders.Authorization to "Bearer ${auth.token}"
        }

    private fun basicAuthValue(auth: Auth.Basic): String {
        val credentials = "${auth.name}:${auth.pswd}".encodeToByteArray()
        return "Basic " + Base64.Default.encode(credentials)
    }

    private fun HttpRequestBuilder.applyTimeoutOverride(options: RpcOptions?) {
        if (options == null) return
        timeout {
            options.requestTimeoutMillis?.let { requestTimeoutMillis = it }
            options.connectTimeoutMillis?.let { connectTimeoutMillis = it }
            options.socketTimeoutMillis?.let { socketTimeoutMillis = it }
        }
    }

    /** [multipart] takes precedence: it carries the real bytes/boundary, [data] is resolved to text otherwise. */
    private fun HttpRequestBuilder.applyBody(data: Body?, multipart: Body.MultiPart?) {
        when {
            multipart != null -> setBody(buildMultiPartBody(multipart))
            data != null -> {
                val text = resolveBodyText(data) ?: ""
                val contentType = contentTypeFor(data)?.let(KtorContentType::parse) ?: KtorContentType.Text.Plain
                setBody(TextContent(text, contentType))
            }
        }
    }

    private fun resolveBodyText(body: Body?): String? =
        when (body) {
            null, is Body.MultiPart -> null
            is Body.FormData -> encodeFormData(body.values)
            is Body.RawContent -> body.content
            is Body.JsonContent -> body.content
        }

    /** `spaceToPlus = true`: the actual application/x-www-form-urlencoded convention, not raw percent-encoding. */
    private fun encodeFormData(values: List<Pair<String, String>>): String =
        values.joinToString("&") { (key, value) ->
            "${key.encodeURLParameter(spaceToPlus = true)}=${value.encodeURLParameter(spaceToPlus = true)}"
        }

    private fun buildMultiPartBody(multipart: Body.MultiPart): MultiPartFormDataContent =
        MultiPartFormDataContent(
            formData {
                multipart.values.forEach { (name, content) -> appendPart(name, content) }
            },
        )

    private fun FormBuilder.appendPart(name: String, content: Content) {
        when (content) {
            is ContentText -> append(name, content.raw)
            is ContentData -> append(name, content.raw ?: content.data.decodeToString())
            is ContentFile -> appendFilePart(name, content)
        }
    }

    private fun FormBuilder.appendFilePart(name: String, content: ContentFile) {
        val partHeaders =
            Headers.build {
                append(HttpHeaders.ContentType, content.tpe.http)
                append(HttpHeaders.ContentDisposition, "filename=\"${content.name}\"")
            }
        append(name, content.data, partHeaders)
    }

    private suspend fun toOutcome(response: HttpResponse): Outcome<RpcResponse> {
        val meta = buildResponseMeta(response)
        val data = readContent(response)
        val rpcResponse = RpcResponse(status = response.status.value, data = data, meta = meta)
        return when (val status = statusConverter.convert(rpcResponse)) {
            is Passed -> Success(rpcResponse, status)
            is Failed -> Failure(Err.ErrorInfo(status.message, ref = rpcResponse), status)
        }
    }

    /** Preserves every value for a repeated header (e.g. multiple `Set-Cookie`), not just the first. */
    private fun buildResponseMeta(response: HttpResponse): Meta {
        val pairs = response.headers.entries().flatMap { (key, values) -> values.map { value -> key to value } }
        return MetaMap(ListMap(pairs))
    }

    /** Never forces a binary response through text decoding, see [isTextContentType]. */
    private suspend fun readContent(response: HttpResponse): Content {
        val ktorContentType = ktorContentTypeOf(response)
        val tpe = ContentType(ktorContentType?.toString() ?: ContentTypes.Octet.http, "")
        if (isTextContentType(ktorContentType)) {
            val text = response.bodyAsText()
            return ContentText(text.encodeToByteArray(), text, tpe)
        }
        val bytes = response.bodyAsBytes()
        val filename = contentDispositionFilename(response)
        return if (filename != null) ContentFile(filename, bytes, null, tpe) else ContentData(bytes, null, tpe)
    }

    private fun ktorContentTypeOf(response: HttpResponse): KtorContentType? =
        response.headers[HttpHeaders.ContentType]?.let(KtorContentType::parse)

    private fun isTextContentType(contentType: KtorContentType?): Boolean {
        if (contentType == null) return true
        if (contentType.contentType.equals("text", ignoreCase = true)) return true
        val sub = contentType.contentSubtype.lowercase()
        return sub == "json" || sub == "xml" || sub.endsWith("+json") || sub.endsWith("+xml")
    }

    private fun contentDispositionFilename(response: HttpResponse): String? {
        val header = response.headers[HttpHeaders.ContentDisposition] ?: return null
        val match = Regex("filename=\"?([^\";]+)\"?").find(header) ?: return null
        return match.groupValues[1]
    }

    /**
     * QUERY maps to POST on the wire, not the literal QUERY verb, for now. OkHttp (our
     * JVM/Android engine) throws building a GET request with a body at all, so mapping to GET
     * would crash there whenever [Body] is actually present. POST permits a body on every engine
     * and needs no server-side QUERY support, at the cost of QUERY's safe/idempotent-like-GET
     * semantic.
     */
    private fun Verb.toKtorMethod(): KtorHttpMethod =
        when (this) {
            Verb.Get -> KtorHttpMethod.Get
            Verb.Query -> KtorHttpMethod.Post
            Verb.Create -> KtorHttpMethod.Post
            Verb.Update -> KtorHttpMethod.Put
            Verb.Upsert -> KtorHttpMethod.Put
            Verb.Patch -> KtorHttpMethod.Patch
            Verb.Delete -> KtorHttpMethod.Delete
            Verb.Execute -> KtorHttpMethod.Post
        }
}
