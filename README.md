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


# Troubleshooting

### IntelliJ IDEA Issue - Object/Class Already Defined Error
All you have to do is:
* Mark directory as target/src_managed/main/compiled_protobuf as 'Generated Source Root'
* Unmark target/src_managed/main as source directory
