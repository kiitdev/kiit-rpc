package kiit.rpc

import kiit.call.Identity
import kiit.inputs.Args
import kiit.inputs.Meta
import kiit.result.Outcome

/**
 * Public contract [kiit.rpc.http.HttpRpc] implements, so a consumer can mock or substitute the
 * client in tests without depending on the concrete Ktor implementation.
 *
 * [execute] is the only abstract method. Every named verb below is a default built on top of it,
 * so a new transport only has to implement [execute] to get the whole named-method API for free.
 * [callerId] is this client's own default identity, used for any call that doesn't override it.
 */
interface RpcClient {
    val callerId: Identity

    suspend fun execute(request: RpcRequest): Outcome<RpcResponse>

    suspend fun get(
        url: String,
        meta: Meta? = null,
        args: Args? = null,
        auth: Auth? = null,
        callerId: Identity? = null,
    ): Outcome<RpcResponse> = execute(RpcRequest.get(url, callerId ?: this.callerId, meta, args, auth))

    suspend fun query(
        url: String,
        meta: Meta? = null,
        args: Args? = null,
        auth: Auth? = null,
        data: Body? = null,
        callerId: Identity? = null,
    ): Outcome<RpcResponse> = execute(RpcRequest.query(url, callerId ?: this.callerId, meta, args, auth, data))

    suspend fun create(
        url: String,
        meta: Meta? = null,
        args: Args? = null,
        auth: Auth? = null,
        data: Body? = null,
        callerId: Identity? = null,
    ): Outcome<RpcResponse> = execute(RpcRequest.create(url, callerId ?: this.callerId, meta, args, auth, data))

    suspend fun update(
        url: String,
        meta: Meta? = null,
        args: Args? = null,
        auth: Auth? = null,
        data: Body? = null,
        callerId: Identity? = null,
    ): Outcome<RpcResponse> = execute(RpcRequest.update(url, callerId ?: this.callerId, meta, args, auth, data))

    suspend fun patch(
        url: String,
        meta: Meta? = null,
        args: Args? = null,
        auth: Auth? = null,
        data: Body? = null,
        callerId: Identity? = null,
    ): Outcome<RpcResponse> = execute(RpcRequest.patch(url, callerId ?: this.callerId, meta, args, auth, data))

    suspend fun delete(
        url: String,
        meta: Meta? = null,
        args: Args? = null,
        auth: Auth? = null,
        callerId: Identity? = null,
    ): Outcome<RpcResponse> = execute(RpcRequest.delete(url, callerId ?: this.callerId, meta, args, auth))
}
