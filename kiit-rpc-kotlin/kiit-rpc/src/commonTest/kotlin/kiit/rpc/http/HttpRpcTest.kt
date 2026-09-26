package kiit.rpc.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kiit.call.Identity
import kiit.codes.Err
import kiit.codes.Invalid
import kiit.codes.Succeeded
import kiit.inputs.Args
import kiit.inputs.ArgsMap
import kiit.inputs.ListMap
import kiit.inputs.Meta
import kiit.inputs.MetaMap
import kiit.requests.ContentText
import kiit.requests.ContentTypes
import kiit.result.Failure
import kiit.result.Success
import kiit.rpc.Auth
import kiit.rpc.Body
import kiit.rpc.RpcOptions
import kiit.rpc.RpcRequest
import kiit.rpc.RpcResponse
import kiit.rpc.RpcSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import io.ktor.http.HttpMethod as KtorHttpMethod

private const val BASE_URL = "https://api.example.com/users"

private fun inputsOf(vararg pairs: Pair<String, String>): Meta = MetaMap(ListMap(pairs.toList()))

private fun argsOf(vararg pairs: Pair<String, String>): Args = ArgsMap(ListMap(pairs.toList()))

class HttpRpcTest {
    @Test
    fun get_sends_query_params_from_args_and_no_body() =
        runTest {
            lateinit var captured: HttpRequestData
            val client =
                mockHttpRpc { request ->
                    captured = request
                    respond("", HttpStatusCode.OK)
                }
            client.get(BASE_URL, args = argsOf("page" to "2", "size" to "10"))

            assertEquals(KtorHttpMethod.Get, captured.method)
            assertEquals("2", captured.url.parameters["page"])
            assertEquals("10", captured.url.parameters["size"])
            assertEquals(null, captured.textBody)
        }

    @Test
    fun create_sends_post_with_json_content_type_and_body() =
        runTest {
            lateinit var captured: HttpRequestData
            val client =
                mockHttpRpc { request ->
                    captured = request
                    respond("", HttpStatusCode.OK)
                }
            client.create(BASE_URL, data = Body.JsonContent("""{"name":"Ada"}"""))

            assertEquals(KtorHttpMethod.Post, captured.method)
            assertEquals("application/json", captured.body.contentType.toString())
            assertEquals("""{"name":"Ada"}""", captured.textBody)
        }

    @Test
    fun update_sends_put_and_patch_sends_patch_and_delete_sends_delete() =
        runTest {
            val methods = mutableListOf<KtorHttpMethod>()
            val client =
                mockHttpRpc { request ->
                    methods += request.method
                    respond("", HttpStatusCode.OK)
                }
            client.update(BASE_URL, data = Body.RawContent("x"))
            client.patch(BASE_URL, data = Body.RawContent("x"))
            client.delete(BASE_URL)

            assertEquals(listOf(KtorHttpMethod.Put, KtorHttpMethod.Patch, KtorHttpMethod.Delete), methods)
        }

    @Test
    fun query_sends_post_on_the_wire_with_its_body_attached() =
        runTest {
            lateinit var captured: HttpRequestData
            val client =
                mockHttpRpc { request ->
                    captured = request
                    respond("", HttpStatusCode.OK)
                }
            client.query(BASE_URL, data = Body.JsonContent("""{"filter":"active"}"""))

            assertEquals(KtorHttpMethod.Post, captured.method)
            assertEquals("""{"filter":"active"}""", captured.textBody)
        }

    @Test
    fun meta_headers_merge_with_default_headers_and_override_on_conflict() =
        runTest {
            lateinit var captured: HttpRequestData
            val settings =
                RpcSettings(callerId = testCallerId, defaultMeta = inputsOf("X-Client" to "kiit-rpc", "X-Env" to "prod"))
            val client =
                mockHttpRpc(settings = settings) { request ->
                    captured = request
                    respond("", HttpStatusCode.OK)
                }
            client.get(BASE_URL, meta = inputsOf("X-Env" to "staging"))

            assertEquals("kiit-rpc", captured.headers["X-Client"])
            assertEquals("staging", captured.headers["X-Env"])
        }

    @Test
    fun basic_auth_produces_a_base64_authorization_header() =
        runTest {
            lateinit var captured: HttpRequestData
            val client =
                mockHttpRpc { request ->
                    captured = request
                    respond("", HttpStatusCode.OK)
                }
            client.get(BASE_URL, auth = Auth.Basic("user", "pass"))

            // "user:pass" base64-encoded, checked against the known constant instead of re-deriving it.
            assertEquals("Basic dXNlcjpwYXNz", captured.headers["Authorization"])
        }

    @Test
    fun bearer_auth_produces_a_bearer_authorization_header() =
        runTest {
            lateinit var captured: HttpRequestData
            val client =
                mockHttpRpc { request ->
                    captured = request
                    respond("", HttpStatusCode.OK)
                }
            client.get(BASE_URL, auth = Auth.Bearer("token123"))

            assertEquals("Bearer token123", captured.headers["Authorization"])
        }

    @Test
    fun default_auth_is_used_when_a_call_supplies_none() =
        runTest {
            lateinit var captured: HttpRequestData
            val settings = RpcSettings(callerId = testCallerId, defaultAuth = Auth.Bearer("default-token"))
            val client =
                mockHttpRpc(settings = settings) { request ->
                    captured = request
                    respond("", HttpStatusCode.OK)
                }
            client.get(BASE_URL)

            assertEquals("Bearer default-token", captured.headers["Authorization"])
        }

    @Test
    fun a_call_s_own_auth_overrides_the_default() =
        runTest {
            lateinit var captured: HttpRequestData
            val settings = RpcSettings(callerId = testCallerId, defaultAuth = Auth.Bearer("default-token"))
            val client =
                mockHttpRpc(settings = settings) { request ->
                    captured = request
                    respond("", HttpStatusCode.OK)
                }
            client.get(BASE_URL, auth = Auth.Bearer("call-token"))

            assertEquals("Bearer call-token", captured.headers["Authorization"])
        }

    @Test
    fun caller_id_is_sent_as_a_header_when_configured() =
        runTest {
            lateinit var captured: HttpRequestData
            val settings = RpcSettings(callerId = Identity.test("kiit", "tests"))
            val client =
                mockHttpRpc(settings = settings) { request ->
                    captured = request
                    respond("", HttpStatusCode.OK)
                }
            client.get(BASE_URL)

            assertEquals(settings.callerId.id, captured.headers["X-Caller-Id"])
        }

    @Test
    fun form_data_body_is_url_encoded_with_the_right_content_type() =
        runTest {
            lateinit var captured: HttpRequestData
            val client =
                mockHttpRpc { request ->
                    captured = request
                    respond("", HttpStatusCode.OK)
                }
            client.create(BASE_URL, data = Body.FormData(listOf("a" to "1", "b" to "hello world")))

            assertEquals("application/x-www-form-urlencoded", captured.body.contentType.toString())
            assertEquals("a=1&b=hello+world", captured.textBody)
        }

    @Test
    fun multipart_body_gets_a_multipart_content_type_with_a_boundary() =
        runTest {
            lateinit var captured: HttpRequestData
            val client =
                mockHttpRpc { request ->
                    captured = request
                    respond("", HttpStatusCode.OK)
                }
            val text = ContentText("hello".encodeToByteArray(), "hello", ContentTypes.Plain)
            val multipart = Body.MultiPart(listOf("note" to text))
            client.create(BASE_URL, data = multipart)

            assertTrue(captured.body.contentType.toString().startsWith("multipart/form-data"))
        }

    @Test
    fun a_2xx_response_with_no_structured_body_resolves_via_the_http_code_table() =
        runTest {
            val client = mockHttpRpc { respond("", HttpStatusCode.OK) }
            val outcome = client.get(BASE_URL)

            val success = assertIs<Success<RpcResponse>>(outcome)
            assertEquals(Succeeded.SUCCESS, success.status)
        }

    @Test
    fun a_404_response_resolves_to_a_failure_with_not_found() =
        runTest {
            val client = mockHttpRpc { respond("", HttpStatusCode.NotFound) }
            val outcome = client.get(BASE_URL)

            val failure = assertIs<Failure<*>>(outcome)
            assertEquals(Invalid.NOT_FOUND, failure.status)
        }

    @Test
    fun a_failure_carries_the_original_response_on_its_error_ref() =
        runTest {
            val client = mockHttpRpc { respond("""{"error":"nope"}""", HttpStatusCode.NotFound) }
            val outcome = client.get(BASE_URL)

            val failure = assertIs<Failure<Err>>(outcome)
            val ref = assertIs<RpcResponse>(failure.error.ref)
            assertEquals(404, ref.status)
        }

    @Test
    fun a_relative_url_is_joined_onto_the_configured_base_url() =
        runTest {
            lateinit var captured: HttpRequestData
            val settings = RpcSettings(callerId = testCallerId, baseUrl = "https://api.example.com")
            val client =
                mockHttpRpc(settings = settings) { request ->
                    captured = request
                    respond("", HttpStatusCode.OK)
                }
            client.get("/users")

            assertEquals("https://api.example.com/users", captured.url.toString())
        }

    @Test
    fun base_url_and_relative_url_join_cleanly_regardless_of_slashes() =
        runTest {
            lateinit var captured: HttpRequestData
            val settings = RpcSettings(callerId = testCallerId, baseUrl = "https://api.example.com/")
            val client =
                mockHttpRpc(settings = settings) { request ->
                    captured = request
                    respond("", HttpStatusCode.OK)
                }
            client.get("/users")

            assertEquals("https://api.example.com/users", captured.url.toString())
        }

    @Test
    fun an_absolute_url_is_sent_as_is_even_when_a_base_url_is_configured() =
        runTest {
            lateinit var captured: HttpRequestData
            val settings = RpcSettings(callerId = testCallerId, baseUrl = "https://api.example.com")
            val client =
                mockHttpRpc(settings = settings) { request ->
                    captured = request
                    respond("", HttpStatusCode.OK)
                }
            client.get("https://other.example.com/status")

            assertEquals("https://other.example.com/status", captured.url.toString())
        }

    @Test
    fun a_request_that_exceeds_the_configured_timeout_fails() =
        runTest {
            val settings = RpcSettings(callerId = testCallerId, requestTimeoutMillis = 20)
            val client =
                mockHttpRpc(settings = settings) {
                    delay(200)
                    respond("", HttpStatusCode.OK)
                }
            val outcome = client.get(BASE_URL)

            assertIs<Failure<*>>(outcome)
        }

    @Test
    fun per_call_options_override_the_client_wide_timeout() =
        runTest {
            val settings = RpcSettings(callerId = testCallerId, requestTimeoutMillis = 5000)
            val client =
                mockHttpRpc(settings = settings) {
                    delay(200)
                    respond("", HttpStatusCode.OK)
                }
            val request = RpcRequest.get(BASE_URL, testCallerId).copy(options = RpcOptions(requestTimeoutMillis = 20))
            val outcome = client.execute(request)

            assertIs<Failure<*>>(outcome)
        }

    @Test
    fun closingAnInstanceThatNeverMadeACallIsANoOp() {
        val client = HttpRpc(settings = RpcSettings(callerId = testCallerId), engine = MockEngine { respond("", HttpStatusCode.OK) })
        client.close()
    }

    @Test
    fun closeReleasesAnInternallyBuiltClient() =
        runTest {
            val client = mockHttpRpc { respond("", HttpStatusCode.OK) }
            client.get(BASE_URL)
            client.close()

            // A closed Ktor engine throws (JobCancellationException, a CancellationException
            // subtype) rather than resolving to a clean Failure — performCall's own catch
            // deliberately re-throws any CancellationException instead of swallowing it, since
            // otherwise real caller-side coroutine cancellation would silently become a Failure
            // too. Using an HttpRpc after close() is a caller error, expected to throw.
            var threw = false
            try {
                client.get(BASE_URL)
            } catch (e: CancellationException) {
                threw = true
            }
            assertTrue(threw)
        }

    @Test
    fun aSuppliedClientIsUsedAsIsAndNotClosedByHttpRpc() =
        runTest {
            val rawClient = HttpClient(MockEngine { respond("", HttpStatusCode.OK) })
            val client = HttpRpc(settings = RpcSettings(callerId = testCallerId), client = rawClient)

            val outcome = client.get(BASE_URL)
            assertIs<Success<RpcResponse>>(outcome)

            client.close()

            // rawClient is still usable directly — HttpRpc.close() didn't close it, since it
            // doesn't own it.
            val stillUsable = rawClient.get(BASE_URL)
            assertEquals(HttpStatusCode.OK, stillUsable.status)
        }
}
