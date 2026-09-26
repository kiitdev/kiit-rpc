@file:OptIn(ExperimentalUuidApi::class)

package kiit.rpc

import kiit.inputs.Inputs
import kiit.inputs.ListMap
import kiit.inputs.MetaMap
import kiit.requests.ClientRequest
import kiit.requests.Source
import kiit.requests.Trace
import kiit.requests.Verb
import kiit.requests.Version
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The caller-facing, outbound mirror of kiit-requests' `ServerRequest`, implementing the shared
 * `ClientRequest` interface. Uses a flat `url` instead of the `path`/`parts` convention, since an
 * outbound call isn't necessarily hitting a kiit API.
 *
 * No `callerId` field. Identity belongs to the calling service, set once at startup, not
 * something that changes per call the way `auth`/`options` do. It lives on `RpcSettings`.
 */
data class RpcRequest(
    override val verb: Verb,
    override val url: String,
    override val meta: Inputs = MetaMap(ListMap()),
    override val args: Inputs = MetaMap(ListMap()),
    val data: Body? = null,
    val auth: Auth? = null,
    override val version: Version = Version(api = "0"),
    override val trace: Trace? = null,
    override val requestId: String = Uuid.random().toString(),
    override val timestamp: Instant = Clock.System.now(),
    override val source: Source = Source.Api,
    val options: RpcOptions? = null,
) : ClientRequest {
    companion object {
        fun get(
            url: String,
            meta: Inputs? = null,
            args: Inputs? = null,
            auth: Auth? = null,
        ): RpcRequest =
            RpcRequest(
                verb = Verb.Get,
                url = url,
                meta = meta ?: MetaMap(ListMap()),
                args = args ?: MetaMap(ListMap()),
                auth = auth,
            )

        fun query(
            url: String,
            meta: Inputs? = null,
            args: Inputs? = null,
            auth: Auth? = null,
            data: Body? = null,
        ): RpcRequest =
            RpcRequest(
                verb = Verb.Query,
                url = url,
                meta = meta ?: MetaMap(ListMap()),
                args = args ?: MetaMap(ListMap()),
                data = data,
                auth = auth,
            )

        fun create(
            url: String,
            meta: Inputs? = null,
            args: Inputs? = null,
            auth: Auth? = null,
            data: Body? = null,
        ): RpcRequest =
            RpcRequest(
                verb = Verb.Create,
                url = url,
                meta = meta ?: MetaMap(ListMap()),
                args = args ?: MetaMap(ListMap()),
                data = data,
                auth = auth,
            )

        fun update(
            url: String,
            meta: Inputs? = null,
            args: Inputs? = null,
            auth: Auth? = null,
            data: Body? = null,
        ): RpcRequest =
            RpcRequest(
                verb = Verb.Update,
                url = url,
                meta = meta ?: MetaMap(ListMap()),
                args = args ?: MetaMap(ListMap()),
                data = data,
                auth = auth,
            )

        fun patch(
            url: String,
            meta: Inputs? = null,
            args: Inputs? = null,
            auth: Auth? = null,
            data: Body? = null,
        ): RpcRequest =
            RpcRequest(
                verb = Verb.Patch,
                url = url,
                meta = meta ?: MetaMap(ListMap()),
                args = args ?: MetaMap(ListMap()),
                data = data,
                auth = auth,
            )

        fun delete(
            url: String,
            meta: Inputs? = null,
            args: Inputs? = null,
            auth: Auth? = null,
        ): RpcRequest =
            RpcRequest(
                verb = Verb.Delete,
                url = url,
                meta = meta ?: MetaMap(ListMap()),
                args = args ?: MetaMap(ListMap()),
                auth = auth,
            )
    }
}
