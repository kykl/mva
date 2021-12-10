# Minimum Viable Architecture (MVA)

An Akka messaging and GRPC based implementation of a minimum viable architecture that's secured, horizontally scalable and highly available for low latency messaging. 

## Use Cases

* Multiplayer gaming
* Realtime communications i.e chat and messaging
* Backend for building reactive applications

# Usage Notes

## SSL Setup

1. Obtain a certificate chain and a private key from your provider
2. Ensure that the certificate chain file contains the entire chain from the root up - some `cat` massaging may be necessary
3. Copy the certificate chain file to `cert-chain.crt` in the project root
4. Copy the private key file to `private-key.pem` in the project root
5. Connect using a client (like `ChatterBox`) and use a hostname that matches the certificate
6. Use something like Route53 DNS to point to the server.

## Proto files

The folder containing `.proto` files is a `git subtree` of a separate repo (here `ZhangBanger/proto`). The simplest thing to do is to commit updates upstream and pull them down. You need to set up the repo and then fetch it.

### Set up subtree

`git subtree add --prefix=src/main/protobuf git@github.com:ZhangBanger/proto.git master`

### Fetch update
 
`git subtree pull --prefix=src/main/protobuf git@github.com:ZhangBanger/proto.git master`

# Development

## AuthService

Create your own `AuthService` that implements `doAuth()` and returns a `Future[String]`.
 
The current working example calls an HTTP endpoint using the `akka-http` library, extracts a string entry from the JSON response, and asychronously populates that value. The endpoint is defined in `application.conf` under the `auth.uri`. The implementation is loaded at runtime by defining `auth.service`.

# Troubleshooting

### IntelliJ IDEA Issue - Object/Class Already Defined Error
All you have to do is:
* Mark directory as target/src_managed/main/compiled_protobuf as 'Generated Source Root'
* Unmark target/src_managed/main as source directory
