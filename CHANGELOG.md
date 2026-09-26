# Changelog

All notable changes to kiit-rpc are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/), versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Extracted from the Kiit monorepo as its own standalone module (originally kiit-httprpc).
- `RpcRequest`/`RpcResponse`/`RpcSettings`/`RpcOptions`/`RpcClient` redesign: `RpcClient`
  collapses to one abstract `execute()` method, with `get`/`query`/`create`/`update`/`patch`/
  `delete` as defaults built on top of it. `ExecuteParams` and the local `HttpMethod` enum are
  gone, replaced by the shared `kiit.requests.Verb`.
- `RpcRequest` implements `kiit.requests.ClientRequest`/`Request`, the shared call shape also used
  by kiit-requests' `ServerRequest` on the inbound side. Depends on `kiit-requests`
  (`Verb`/`Version`/`Trace`/`Content`), `kiit-identity` (`Identity`), and `kiit-inputs`
  (`Meta`/`Args`).
- `RpcRequest.callerId` is required, not optional, matching the shared `Request` base. Filled in
  automatically from `RpcSettings.callerId` by `RpcClient`'s named methods (`get`/`query`/...),
  overridable per call. `RpcSettings.callerId` itself is now required too, so `HttpRpc`
  construction always has an identity to attribute calls to.
- `RpcRequest.tags: List<Tag>` (`kiit.requests.Tag`), for correlation labels, same as
  `ServerRequest.tags` on the inbound side.
- `RpcResponse.data` is a `kiit.requests.Content`, not a plain `String` — binary responses come back
  as `ContentFile`/`ContentData` with their bytes intact instead of being forced through text
  decoding.
- `RpcResponse.meta` preserves every value for a repeated header (`Set-Cookie`), not just the
  first.
- 4-step status resolution: an `x-server-status-rfc9457` response header, then an optional
  caller-supplied `StatusConverter`, then optional body parsing (`RpcSettings.parseStatusFromBody`,
  off by default), then the raw HTTP status code as a last resort.
- A failed call's `Err.ref` carries the original `RpcResponse`, so a caller isn't limited to just
  the resolved `Status` on failure.
- `RpcSettings.baseUrl`/`defaultAuth`/`callerId`/`defaultMeta`, and `RpcRequest.options`
  (`RpcOptions`) for a per-call timeout override.
- `HttpRpc` implements `AutoCloseable`. `close()` only releases a client it built itself, never
  one supplied via the new `client` constructor param.
- `HttpRpc(client: HttpClient? = null)` — a fully pre-built Ktor client, used as-is. Covers
  installing Ktor's own `HttpCache` plugin (or any other plugin kiit-rpc doesn't wrap) and sharing
  one client across several libraries.
