package kiit.rpc

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kiit.codes.Invalid
import kiit.codes.Unserved
import kiit.result.Failure
import kiit.result.Success
import kiit.rpc.http.mockHttpRpc
import kiit.rpc.http.respondJson
import kiit.rpc.http.testCallerId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private const val URL = "https://api.example.com/users/1"

@Serializable
private data class User(val id: Int, val name: String)

class ExecuteTest {
    @Test
    fun happy_path_decodes_the_response_body_into_t() =
        runTest {
            val client = mockHttpRpc { respondJson("""{"id":1,"name":"Ada"}""") }
            val outcome = client.executeOutcome<User>(RpcRequest.get(URL, testCallerId))

            val success = assertIs<Success<User>>(outcome)
            assertEquals(User(1, "Ada"), success.value)
        }

    @Test
    fun a_decode_failure_becomes_a_failure_even_though_the_transport_succeeded() =
        runTest {
            val client = mockHttpRpc { respondJson("""{"unexpected":"shape"}""") }
            val outcome = client.executeOutcome<User>(RpcRequest.get(URL, testCallerId))

            val failure = assertIs<Failure<*>>(outcome)
            assertEquals(Unserved.UNEXPECTED, failure.status)
        }

    @Test
    fun a_non_2xx_status_fails_without_attempting_to_decode_the_body() =
        runTest {
            val client = mockHttpRpc { respond("not json at all", HttpStatusCode.NotFound) }
            val outcome = client.executeOutcome<User>(RpcRequest.get(URL, testCallerId))

            val failure = assertIs<Failure<*>>(outcome)
            assertEquals(Invalid.NOT_FOUND, failure.status)
        }

    @Test
    fun a_transport_failure_is_folded_into_a_failure() =
        runTest {
            val client = mockHttpRpc { throw IllegalStateException("connection refused") }
            val outcome = client.executeOutcome<User>(RpcRequest.get(URL, testCallerId))

            assertIs<Failure<*>>(outcome)
        }

    @Test
    fun executeResult_returns_a_try_backed_by_a_throwable_on_failure() =
        runTest {
            val client = mockHttpRpc { respond("not json at all", HttpStatusCode.NotFound) }
            val result = client.executeResult<User>(RpcRequest.get(URL, testCallerId))

            val failure = assertIs<Failure<Throwable>>(result)
            assertIs<Throwable>(failure.error)
        }

    @Test
    fun the_plain_overload_with_an_explicit_serializer_works_the_same_as_the_reified_one() =
        runTest {
            val client = mockHttpRpc { respondJson("""{"id":2,"name":"Grace"}""") }
            val outcome = client.executeOutcome<User>(RpcRequest.get(URL, testCallerId), serializer = Serializer())

            val success = assertIs<Success<User>>(outcome)
            assertEquals(User(2, "Grace"), success.value)
        }
}
