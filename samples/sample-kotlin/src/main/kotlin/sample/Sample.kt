package sample

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.request.get
import kiit.call.Identity
import kiit.inputs.Args
import kiit.inputs.ArgsMap
import kiit.inputs.ListMap
import kiit.requests.Contents
import kiit.rpc.Auth
import kiit.rpc.Body
import kiit.rpc.RpcOptions
import kiit.rpc.RpcPolicy
import kiit.rpc.RpcRequest
import kiit.rpc.RpcResponse
import kiit.rpc.RpcSettings
import kiit.rpc.executeResult
import kiit.rpc.http.HttpRpc
import kiit.result.Outcome
import kiit.result.Success
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

/**
 * Canonical sample for kiit-rpc. One running example against httpbin.org, in five parts:
 * 1. Basic calls, 2. Auth, 3. Typed decode, 4. Policy, 5. Settings/per-call options.
 *
 * Every example is wrapped in `// <example id="..." tags="...">` ... `// </example>` so it can be
 * extracted for the docs later, matching kiit-codes' sample. The `verify(...)` calls sit outside
 * the markers, they check the example still works against the real httpbin.org.
 */

private var checks = 0

private fun verify(label: String, condition: Boolean) {
    check(condition) { "FAILED: $label" }
    checks++
    println("  ok: $label")
}

private fun section(title: String) {
    println()
    println("=".repeat(60))
    println(title)
    println("=".repeat(60))
}

private fun argsOf(vararg pairs: Pair<String, String>): Args = ArgsMap(ListMap(pairs.toList()))

// The caller identity every call in this sample makes as. A real service would build this once
// at startup and reuse it, not construct a fresh one per call.
private val callerId = Identity.api(company = "acme", area = "web", service = "gateway")

// ============================================================
// Part 1: Basic calls
// ============================================================

private suspend fun showBasicCalls(client: HttpRpc) {
    section("Part 1: Basic calls")

    // <example id="basic-get" tags="calls">
    // A GET with query params, no body. args become the URL's query string.
    val getOutcome = client.get("https://httpbin.org/get", args = argsOf("greeting" to "hello"))
    // </example>
    verify("basic-get: succeeded", getOutcome is Success)

    // <example id="basic-create" tags="calls">
    // create (POST) with a JSON body.
    val createOutcome = client.create("https://httpbin.org/post", data = Body.JsonContent("""{"name":"Ada"}"""))
    // </example>
    verify("basic-create: succeeded", createOutcome is Success)
}

// ============================================================
// Part 2: Auth
// ============================================================

private suspend fun showAuth(client: HttpRpc) {
    section("Part 2: Auth")

    // <example id="auth-bearer" tags="auth">
    // httpbin's /bearer endpoint echoes back the token when the Authorization header is valid.
    val outcome = client.get("https://httpbin.org/bearer", auth = Auth.Bearer("demo-token"))
    // </example>
    verify("auth-bearer: succeeded", outcome is Success)
    if (outcome is Success) {
        val text = Contents.toText(outcome.value.data) ?: ""
        verify("auth-bearer: token echoed back", text.contains("demo-token"))
    }
}

// ============================================================
// Part 3: Typed decode
// ============================================================

@Serializable
private data class HttpBinGet(val url: String)

private suspend fun showTypedDecode(client: HttpRpc) {
    section("Part 3: Typed decode")

    // <example id="typed-executeResult" tags="typed">
    // executeResult<T> calls and decodes the body into T in one step.
    val result = client.executeResult<HttpBinGet>(RpcRequest.get("https://httpbin.org/get", callerId))
    // </example>
    result.onSuccess { println("decoded url: ${it.url}") }
    verify("typed-executeResult: decoded", result.getOrNull()?.url?.contains("httpbin.org/get") == true)
}

// ============================================================
// Part 4: Policy
// ============================================================

// <example id="policy-logging" tags="policy">
// A Policy that logs before and after the call it wraps. It sees the almost-final request,
// after RpcSettings' defaults are merged in, the same way Ktor's own Logging plugin does.
private class LoggingPolicy : RpcPolicy {
    override suspend fun run(
        i: RpcRequest,
        operation: suspend (RpcRequest) -> Outcome<RpcResponse>,
    ): Outcome<RpcResponse> {
        println("-> ${i.verb} ${i.url}")
        val outcome = operation(i)
        println("<- ${outcome.status.name}")
        return outcome
    }
}
// </example>

private suspend fun showPolicy() {
    section("Part 4: Policy")

    // <example id="policy-attach" tags="policy">
    // Attach it via the policies list when constructing HttpRpc.
    val client = HttpRpc(settings = RpcSettings(callerId = callerId), policies = listOf(LoggingPolicy()))
    val outcome = client.get("https://httpbin.org/get")
    // </example>
    verify("policy-attach: succeeded", outcome is Success)
}

// ============================================================
// Part 5: Settings and per-call options
// ============================================================

private suspend fun showSettings() {
    section("Part 5: Settings and per-call options")

    // <example id="settings-base-url" tags="settings">
    // baseUrl lets every call pass a relative path instead of the full URL.
    val client = HttpRpc(settings = RpcSettings(callerId = callerId, baseUrl = "https://httpbin.org"))
    val outcome = client.get("/get")
    // </example>
    verify("settings-base-url: succeeded", outcome is Success)

    // <example id="options-per-call-timeout" tags="settings">
    // RpcOptions overrides RpcSettings' timeouts for just this one call. /delay/2 takes 2s to
    // respond, a 50ms request timeout fails it well before that, without affecting other calls.
    val request =
        RpcRequest.get("https://httpbin.org/delay/2", callerId).copy(options = RpcOptions(requestTimeoutMillis = 50))
    val timedOut = client.execute(request)
    // </example>
    verify("options-per-call-timeout: failed as expected", timedOut !is Success)
}

// ============================================================
// Part 6: Supplying your own client, and lifecycle
// ============================================================

private suspend fun showOwnClientAndClose() {
    section("Part 6: Supplying your own client, and lifecycle")

    // <example id="own-client" tags="lifecycle">
    // A fully pre-built HttpClient, used as-is — here with Ktor's own HttpCache plugin
    // installed (built into ktor-client-core, no extra dependency needed). RpcSettings'
    // timeout/redirect fields don't apply, this client is already configured.
    val cachingClient = HttpClient(OkHttp) { install(HttpCache) }
    val client = HttpRpc(settings = RpcSettings(callerId = callerId), client = cachingClient)
    val outcome = client.get("https://httpbin.org/get")
    // </example>
    verify("own-client: succeeded", outcome is Success)

    // <example id="close" tags="lifecycle">
    // close() releases a client HttpRpc built itself. It never closes a client you supplied,
    // that one's still yours to manage.
    client.close()
    // </example>
    val stillUsable = cachingClient.get("https://httpbin.org/get") { }
    verify("own-client: not closed by HttpRpc.close()", stillUsable.status.value == 200)
}

fun main() =
    runBlocking {
        val client = HttpRpc(settings = RpcSettings(callerId = callerId))
        showBasicCalls(client)
        showAuth(client)
        showTypedDecode(client)
        showPolicy()
        showSettings()
        showOwnClientAndClose()

        println()
        println("All $checks checks passed.")
    }
