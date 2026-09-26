@file:OptIn(ExperimentalUuidApi::class)

package kiit.rpc

import kiit.call.Identity
import kiit.inputs.Args
import kiit.inputs.ArgsMap
import kiit.inputs.ListMap
import kiit.inputs.Meta
import kiit.inputs.MetaMap
import kiit.requests.ClientRequest
import kiit.requests.Tag
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
 * @property callerId Required, same as `ServerRequest.callerId`. Usually filled in from
 *   `RpcSettings.callerId` via [RpcClient]'s convenience methods, not set by hand per call.
 */
data class RpcRequest(
    override val verb: Verb,
    override val url: String,
    override val callerId: Identity,
    override val meta: Meta = MetaMap(ListMap()),
    override val args: Args = ArgsMap(ListMap()),
    val data: Body? = null,
    val auth: Auth? = null,
    override val version: Version = Version(api = "0"),
    override val trace: Trace? = null,
    override val requestId: String = Uuid.random().toString(),
    override val timestamp: Instant = Clock.System.now(),
    override val tags: List<Tag> = listOf(),
    val options: RpcOptions? = null,
) : ClientRequest {
    companion object {
        fun get(
            url: String,
            callerId: Identity,
            meta: Meta? = null,
            args: Args? = null,
            auth: Auth? = null,
        ): RpcRequest =
            RpcRequest(
                verb = Verb.Get,
                url = url,
                callerId = callerId,
                meta = meta ?: MetaMap(ListMap()),
                args = args ?: ArgsMap(ListMap()),
                auth = auth,
            )

        @Suppress("LongParameterList")
        fun query(
            url: String,
            callerId: Identity,
            meta: Meta? = null,
            args: Args? = null,
            auth: Auth? = null,
            data: Body? = null,
        ): RpcRequest =
            RpcRequest(
                verb = Verb.Query,
                url = url,
                callerId = callerId,
                meta = meta ?: MetaMap(ListMap()),
                args = args ?: ArgsMap(ListMap()),
                data = data,
                auth = auth,
            )

        @Suppress("LongParameterList")
        fun create(
            url: String,
            callerId: Identity,
            meta: Meta? = null,
            args: Args? = null,
            auth: Auth? = null,
            data: Body? = null,
        ): RpcRequest =
            RpcRequest(
                verb = Verb.Create,
                url = url,
                callerId = callerId,
                meta = meta ?: MetaMap(ListMap()),
                args = args ?: ArgsMap(ListMap()),
                data = data,
                auth = auth,
            )

        @Suppress("LongParameterList")
        fun update(
            url: String,
            callerId: Identity,
            meta: Meta? = null,
            args: Args? = null,
            auth: Auth? = null,
            data: Body? = null,
        ): RpcRequest =
            RpcRequest(
                verb = Verb.Update,
                url = url,
                callerId = callerId,
                meta = meta ?: MetaMap(ListMap()),
                args = args ?: ArgsMap(ListMap()),
                data = data,
                auth = auth,
            )

        @Suppress("LongParameterList")
        fun patch(
            url: String,
            callerId: Identity,
            meta: Meta? = null,
            args: Args? = null,
            auth: Auth? = null,
            data: Body? = null,
        ): RpcRequest =
            RpcRequest(
                verb = Verb.Patch,
                url = url,
                callerId = callerId,
                meta = meta ?: MetaMap(ListMap()),
                args = args ?: ArgsMap(ListMap()),
                data = data,
                auth = auth,
            )

        fun delete(
            url: String,
            callerId: Identity,
            meta: Meta? = null,
            args: Args? = null,
            auth: Auth? = null,
        ): RpcRequest =
            RpcRequest(
                verb = Verb.Delete,
                url = url,
                callerId = callerId,
                meta = meta ?: MetaMap(ListMap()),
                args = args ?: ArgsMap(ListMap()),
                auth = auth,
            )
    }
}
