# nettunnel

*nettunnel* is an [omz-net-lib](https://sw-vc.warpcs.org/omegazero/omz-net-lib) extension allowing two mutually trusting peers to communicate efficiently over a single TCP connection.
This usage of virtual connections reduces round-trip times during connection establishment.

Prebuilt JARs and example code: <https://i.warp03.xyz/u/b/software/nettunnel/>

# omz-proxy plugin

*nettunnel-omzproxy* contains an [omz-proxy3](https://sw-vc.warpcs.org/omegazero/omz-proxy3) plugin to allow this library to be used for communication between two omz-proxy instances.

## Configuration

Configuration ID: `ntun`

| Name | Type | Description | Required | Default value |
| --- | --- | --- | --- | --- |
| enable | boolean | Whether this plugin is enabled. | no | `true` |
| alwaysEnableServer | boolean | Always enable and start the server implementation, regardless of whether the feature set contains `ntun.server.plain` or `ntun.server.tls`. | no | `false` |
| alwaysEnableClient | boolean | Always enable and start the client implementation, regardless of whether the feature set contains `ntun.client.plain` or `ntun.client.tls`. | no | `false` |
| baseImpl | string | The underlying network implementation to use. | no | `"nio"` |
| maxPacketSize | number | The maximum protocol frame size. | no | `16384` |
| maxConcurrentConns | number | The maximum number of concurrent connections. | no | `65535` |
| sharedSecret | string | The shared secret for authentication. A value of `null` disables authentication. | no | `null` |
| encrypted | boolean | Use an encrypted connection for communication. | no | `false` |
| serverBindPort | number | (Server-only) The port to listen for TCP connections on. | no | `NetTunnel.DEFAULT_PORT` (`1184`) |
| serverParams | object | (Client-only) Server parameters to connect to. | ~ | `null` |
| serverParams.address | string | The server address. | yes | - |
| serverParams.port | number | The server port. | no | `NetTunnel.DEFAULT_PORT` |
| serverParams.localAddress | string | The local address to connect from. | no | none (system default) |
| useUpstreamServerParams | boolean | If `true`, use the default upstream server parameters of the proxy configuration for the values of `serverParams`. If both `serverParams` and this is specified, `serverParams` is used. | no | `false` |

To enable the server, pass `-Dorg.omegazero.proxy.serverImplNamespace=ntun.server` as a command line argument.

To enable the client, either pass `-Dorg.omegazero.proxy.clientImplNamespace=ntun.client` as a command line argument *or* set `alwaysEnableClient` to `true` and set `clientImplOverride` on an `UpstreamServer` to `ntun.client`.
