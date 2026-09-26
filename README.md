<div align="center">

# kiit-rpc

**A simple, declarative Kotlin Multiplatform client for RPC-style HTTP calls.**

Every call returns an `Outcome<RpcResponse>` carrying a status that classifies the kind of
success or failure, instead of just a raw response object or a thrown exception. That status
comes from kiit-codes, the same taxonomy kiit-result already builds on. `get`, `query`, `create`,
`update`, `patch`, and `delete` all funnel through one pipeline: build the request, run it through
an optional chain of policies, resolve the status, hand back the outcome.

[![Build](https://img.shields.io/github/actions/workflow/status/kiitdev/kiit-rpc/ci.yml?branch=main)](https://github.com/kiitdev/kiit-rpc/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/kiitdev/kiit-rpc)](./LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-multiplatform-purple.svg)](https://kotlinlang.org)

Part of [Kiit](https://www.kiit.dev)

</div>

## Table of Contents

- [Why](#why)
- [Start](#start)
- [Concepts](#concepts)
- [Typed calls](#typed-calls)
- [Policies](#policies)
- [Settings and per-call options](#settings-and-per-call-options)
- [Engine, client, and lifecycle](#engine-client-and-lifecycle)
- [Usage](#usage)
- [Requirements](#requirements)
- [License](#license)

## Why

Most HTTP clients hand back either a response you inspect yourself or an exception you catch.
Neither tells you what *kind* of outcome you got, just that something happened. kiit-rpc pairs
every call with kiit-codes' status taxonomy: a 404 resolves to `Invalid.NOT_FOUND`, a 401 to
`Restricted.UNAUTHENTICATED`, and so on, whether the server is kiit-based (it can embed the exact
status in a response header) or any other API (the plain HTTP code maps to the closest match).

## Start

kiit-rpc hasn't been published to Maven Central yet. Once it is:

```kotlin
dependencies {
    implementation("dev.kiit:kiit-rpc:<version>")
}
```

`kiit-rpc` depends on `dev.kiit:kiit-codes`, `dev.kiit:kiit-result`, `dev.kiit:kiit-call`,
`dev.kiit:kiit-inputs`, and `dev.kiit:kiit-requests` transitively (all `api`), so you don't need
to add any of them separately.

**A basic call:**

```kotlin
import kiit.call.Identity
import kiit.requests.Contents
import kiit.rpc.RpcSettings
import kiit.rpc.http.HttpRpc

val callerId = Identity.api(company = "acme", area = "web", service = "gateway")
val client = HttpRpc(settings = RpcSettings(callerId = callerId))
val outcome = client.get("https://httpbin.org/get")

outcome.onSuccess { response -> println(Contents.toText(response.data)) }
    .onFailure { err -> println("failed: ${err.message}") }
```

`callerId` is required, not optional: every `RpcRequest` carries one, so "who made this call" is
never silently unset. `HttpRpc`'s named methods (`get`/`query`/`create`/...) fill it in from
`RpcSettings.callerId` automatically, so you don't pass it per call, only when constructing the
client (or an `RpcRequest` directly, or overriding it for one specific call).

Query params (`args`) are `kiit.inputs.Args`, headers (`meta`) are `kiit.inputs.Meta`, not a plain `Map`:

```kotlin
import kiit.inputs.ArgsMap
import kiit.inputs.ListMap

val args = ArgsMap(ListMap(listOf("q" to "hello")))
client.get("https://httpbin.org/get", args = args)
```

See [`samples/sample-kotlin`](./samples/sample-kotlin) for a runnable end-to-end example,
including auth, a typed call, a logging policy, and per-call settings.

## Concepts

| Term | What it is |
|---|---|
| **`RpcClient`** (`kiit.rpc`) | The public contract: `execute` is the one abstract method, `get`/`query`/`create`/`update`/`patch`/`delete` are defaults built on top of it. `kiit.rpc.http.HttpRpc` is the Ktor-backed implementation. |
| **`RpcRequest`** | The caller-facing, outbound mirror of kiit-requests' `ServerRequest`, implementing the shared `kiit.requests.ClientRequest`/`Request` interfaces: `verb`/`url`/`meta`/`args`/`data`/`auth`/`version`/`trace`/`source`/`options`. A flat `url`, not the area/name/action convention, since an outbound call isn't necessarily hitting a kiit API. |
| **`RpcResponse`** | `status`/`data`/`meta`. `data` is a `kiit.requests.Content` (`ContentText`/`ContentFile`/`ContentData`), never forced through text decoding, so a binary response comes back with its bytes intact. `meta` is a `kiit.inputs.Meta`, preserving every value for a repeated header like `Set-Cookie`. |
| **`Outcome<T>`** | `Result<T, Err>` from kiit-result. Every call returns `Outcome<RpcResponse>`, carrying a resolved `Status`. On failure, `Err.ref` carries the original `RpcResponse`, so nothing is lost even when the status alone doesn't say enough. |
| **`RpcSettings`** | Client-wide config: `callerId`, `baseUrl`, `defaultMeta`, `defaultAuth`, timeouts, `followRedirects`, `parseStatusFromBody`. |
| **`RpcOptions`** | Per-call override of `RpcSettings`' timeouts, via `RpcRequest.options`. |
| **`Policy<I, O>`** (`RpcPolicy` = `Policy<RpcRequest, RpcResponse>`) | Wraps a call: retry, log, rewrite the request, short-circuit. A list of them chains together, first one outermost. Sees the request after `RpcSettings`' defaults are merged in, the same way Ktor's own `Logging` plugin sees a request after `DefaultRequest` resolves. |
| **`Serializer<T>`** | Encode/decode abstraction behind typed calls. `KotlinxSerializer` is the default, bring your own for Moshi/Gson/Jackson. |
| **`StatusConverter`** (`kiit.rpc`, default impl `kiit.rpc.http.KiitStatusConverter`) | Resolves the `Status` for a response: checks the `x-server-status-rfc9457` header first, then optionally the body (`RpcSettings.parseStatusFromBody`, off by default), then falls back to the HTTP status code. |

The public contract (`RpcClient`, `RpcRequest`, `RpcResponse`, `RpcSettings`, `RpcOptions`,
`StatusConverter`, `Auth`, `Body`, `Policy`, `Serializer`) lives at `kiit.rpc`. The concrete Ktor
engine and its own pluggable strategy objects (`HttpRpc`, `KiitStatusConverter`) live at
`kiit.rpc.http`, so a future non-HTTP transport can sit alongside it as its own subpackage without
reshaping the public contract.

`query` is HTTP's newer method for a GET-like call that still carries a body, read-only like GET
but meant for searches too complex or too large for a URL. It's mapped to `POST` on the wire for
now, not the literal QUERY verb. OkHttp, the engine behind the JVM and Android targets, refuses to
build a GET request with a body at all, so QUERY-as-GET would fail there the moment a body is
actually present.

## Typed calls

`executeResult`/`executeOutcome` call and decode the response body into a type in one step:

```kotlin
import kiit.rpc.RpcRequest
import kotlinx.serialization.Serializable

@Serializable
data class User(val id: Int, val name: String)

val result = client.executeResult<User>(RpcRequest.get("https://api.example.com/users/1"))
```

`executeResult` returns a `Try<T>` (`Result<T, Throwable>`), `executeOutcome` returns an
`Outcome<T>` (`Result<T, Err>`) if you'd rather stay in kiit-codes' status world. Both have a
reified overload that infers the serializer for any `@Serializable` type, and a plain overload
taking an explicit `Serializer<T>` for anything else, including Swift, where reified generics
don't exist in the compiled framework.

Decoding only looks at the fields your type declares. A response with extra fields you don't care
about still decodes fine.

## Policies

An `RpcPolicy` wraps every call before it reaches the network:

```kotlin
import kiit.result.Outcome
import kiit.rpc.RpcPolicy
import kiit.rpc.RpcRequest
import kiit.rpc.RpcResponse
import kiit.rpc.http.HttpRpc

class LoggingPolicy : RpcPolicy {
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

val client = HttpRpc(policies = listOf(LoggingPolicy()))
```

The first policy in the list runs outermost. One that never calls `operation` short-circuits the
call entirely. That's on purpose, there's no guard against it: retry, auth refresh, and caching
all need that power.

## Settings and per-call options

`RpcSettings` holds client-wide defaults, applied to every call from that `HttpRpc` instance:

```kotlin
import kiit.rpc.RpcSettings
import kiit.rpc.http.HttpRpc

val client = HttpRpc(settings = RpcSettings(baseUrl = "https://api.example.com"))
val outcome = client.get("/users")
```

`RpcOptions` overrides just the timeouts, for just one call:

```kotlin
import kiit.rpc.RpcOptions
import kiit.rpc.RpcRequest

val request = RpcRequest.get("https://api.example.com/slow-endpoint")
    .copy(options = RpcOptions(requestTimeoutMillis = 30_000))
val outcome = client.execute(request)
```

`connectTimeoutMillis` (on both `RpcSettings` and `RpcOptions`) is a no-op on the Darwin (iOS)
engine, Ktor's `HttpTimeout` plugin doesn't support a connect-timeout override there. It still
works on OkHttp (JVM/Android).

## Engine, client, and lifecycle

`HttpRpc` takes two escape hatches for anything it doesn't wrap an opinion around, and implements
`AutoCloseable` for the one it builds internally:

```kotlin
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kiit.rpc.http.HttpRpc

// engine: swap just the engine's own config, RpcSettings' timeouts/redirects still apply on top
// of it. OkHttp is already on the classpath, kiit-rpc's own JVM/Android target depends on it.
val withCustomEngine = HttpRpc(engine = OkHttp.create { /* e.g. config.connectionPool(...) */ })

// client: a fully pre-built HttpClient, used exactly as given — e.g. with Ktor's own HttpCache
// plugin installed (built into ktor-client-core, no extra dependency needed), or one client
// shared across several libraries. RpcSettings' timeout/redirect fields don't apply here,
// you've already configured the client yourself.
val cachingClient = HttpClient(OkHttp) { install(HttpCache) }
val withOwnClient = HttpRpc(client = cachingClient)
```

```kotlin
withCustomEngine.close()
```

`close()` releases the client `HttpRpc` built itself. It's a no-op if the instance never made a
call, and it never closes a `client` you supplied — that one's still yours to manage, since it may
be shared elsewhere in your app.

## Usage

1. **Calling other kiit-based services.** The status comes back exact, not guessed from the HTTP
   code, since a kiit server can embed its own structured status via a response header.
2. **Calling any other HTTP API.** The plain status code still maps to the closest kiit-codes
   status, the taxonomy isn't kiit-only.
3. **Typed responses.** `executeResult`/`executeOutcome` skip the manual decode step.
4. **Cross-cutting concerns.** Logging, retry, or auth refresh as a `Policy`, instead of wrapping
   every call site by hand.
5. **Binary responses.** An image or PDF response comes back as `ContentFile`/`ContentData` with
   its bytes intact, never corrupted by a forced text decode.

**Good fit if:**
1. You already use (or want) kiit-codes' status taxonomy and want an HTTP client that plugs into
   it directly.
2. You want a request pipeline you can extend with policies, without forking the client.

**Probably not necessary if:**
1. You need HTTP features this doesn't cover, HTTP/2 push and WebSockets aren't in scope. Ktor
   itself supports them, this library just doesn't add an opinion on top.
2. You just need a plain HTTP call and don't want kiit-result/kiit-codes on your classpath at all.

## Requirements

- Kotlin Multiplatform
- JVM, Android, iOS (arm64, simulator arm64, x64)
- Depends on `dev.kiit:kiit-codes`, `dev.kiit:kiit-result`, `dev.kiit:kiit-call`,
  `dev.kiit:kiit-inputs`, and `dev.kiit:kiit-requests` (all transitively available to consumers via `api`)

## License

[Apache License 2.0](./LICENSE)

---

<div align="center">

**kiit-rpc** is one module of [Kiit](https://www.kiit.dev), a lightweight, modular Kotlin
toolkit for building server applications, APIs, CLIs, and jobs.

**Adopt one module at a time.**

</div>
