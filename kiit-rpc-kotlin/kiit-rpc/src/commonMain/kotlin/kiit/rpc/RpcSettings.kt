package kiit.rpc

import kiit.identity.Identity
import kiit.inputs.ListMap
import kiit.inputs.Meta
import kiit.inputs.MetaMap

/** Client-wide config for [kiit.rpc.http.HttpRpc]. Anything not covered here is reachable via its `engine` param. */
data class RpcSettings(
    /** Default identity for every call this client makes. Required, see [RpcRequest.callerId]. */
    val callerId: Identity,
    /** Prefixed onto any call `url` that isn't already absolute. An absolute `url` is sent as-is. */
    val baseUrl: String? = null,
    /** Used when a call's own [RpcRequest.auth] is null. */
    val defaultAuth: Auth? = null,
    val defaultMeta: Meta = MetaMap(ListMap()),
    val requestTimeoutMillis: Long? = null,
    /** No-op on Darwin (iOS) - Ktor's HttpTimeout plugin doesn't support this there. */
    val connectTimeoutMillis: Long? = null,
    val socketTimeoutMillis: Long? = null,
    val followRedirects: Boolean = true,
    /** Opt-in body-parsing fallback for [kiit.rpc.http.KiitStatusConverter], see its KDoc. */
    val parseStatusFromBody: Boolean = false,
)
