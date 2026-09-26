package kiit.rpc.http

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kiit.identity.Identity
import kiit.rpc.RpcPolicy
import kiit.rpc.RpcSettings

/** Shared test identity, since [RpcSettings.callerId] is required. */
val testCallerId: Identity = Identity.test("kiit", "rpc-tests")

/**
 * Builds an [HttpRpc] wired to a Ktor [MockEngine], so tests never make a real network call.
 *
 * ktlint's `function-signature` rule wants this collapsed onto one (>120 char) line since there's
 * no `.editorconfig` `max_line_length` for it to check against here, which would then fail
 * detekt's `MaxLineLength`. Suppressed rather than collapsed.
 */
@Suppress("ktlint:standard:function-signature")
fun mockHttpRpc(
    settings: RpcSettings = RpcSettings(callerId = testCallerId),
    policies: List<RpcPolicy> = emptyList(),
    handler: MockRequestHandler,
): HttpRpc {
    return HttpRpc(settings = settings, policies = policies, engine = MockEngine(handler))
}

/** Shorthand for a JSON response, the common case across these tests. */
fun MockRequestHandleScope.respondJson(json: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(json, status, headersOf(HttpHeaders.ContentType, "application/json"))

/** The last [HttpRequestData.body]'s text, assuming [HttpRpc] sent it as [io.ktor.http.content.TextContent]. */
val HttpRequestData.textBody: String?
    get() = (body as? io.ktor.http.content.TextContent)?.text
