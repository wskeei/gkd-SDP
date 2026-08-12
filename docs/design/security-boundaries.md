# Security Boundaries

This document records the transport boundaries that are enforced by application code.

## Cleartext configuration

`res/xml/network_security_config.xml` keeps `cleartextTrafficPermitted="true"` only because
user-supplied HTTP subscription origins may be enabled individually. The manifest-level flag
is not a blanket authorization. Every OkHttp-based client must enforce an application-level
policy before it connects.

- Subscription updates: `CleartextOriginInterceptor` allows HTTPS and only those HTTP
  `scheme://host:port` origins stored in `CleartextOriginAuthorizations`.
- Image preview: `ImagePreviewHttpsOnlyNetworkInterceptor` rejects HTTP and unsupported
  network schemes before a connection is made. It is registered as a network interceptor so
  HTTPS to HTTP redirects are also rejected.
- WebView and update checks never use the cleartext base config.

## Image preview policy

`ImagePreviewNetworkPolicy` is the single policy used by foreground rendering and paging
prefetch:

- `https://` URIs with a host and no userinfo are allowed.
- `http://` URIs are rejected without consulting subscription origin authorization.
- `ftp`, WebSocket, JavaScript, data, malformed, or userinfo-bearing network URIs are
  rejected with a stable error code.
- Local URIs such as `file://`, `content://`, and `android.resource://` remain displayable
  through Coil's non-network fetchers.

The preview UI shares one `ImageLoader` whose OkHttp client installs the HTTPS-only network
interceptor. No separate `context.imageLoader` or ad hoc `OkHttpClient` is used for preview
images.
