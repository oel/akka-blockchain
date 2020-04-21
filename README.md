# Akka Blockchain

This is an Actor-based Scala application that runs on an Akka cluster to simulate cryptocurrency mining activities on a decentralized blockchain network.  It involves using of hash functions, Merkle trees and some basic PKCS cryptographic functions.  Proof-of-Work is adopted as the concensus algorithm.  For an overview of the application, please visit [Genuine Blog](https://blog.genuine.com/an-akka-actor-based-blockchain/).

The application uses Akka classic actors and has been tested fully functional on Akka *2.5* and *2.6* on an expandable cluster, with each node simulating an independent miner.  With the default configuration, the application will launch an Akka cluster on a single host with two seed nodes at port# *2551* and *2552* for additional nodes to join.

The main program takes as arguments a port#, path to the miner's public-key for collecting mining reward, and an optional flag "test" for a quick test (as opposed to entering a mining loop):

```bash
$ sbt "runMain akkablockchain.Main port# /path/to/minerPublicKey [test]"
```

To save time for cryptographic key generation (required for user accounts) in application startup, a few public-keys (*accountX_public.pem*; *X=0,..,9*) have been created and saved under "*{project-root}/src/main/resources/key/*".  To generate additional keys, method *generateKeyPairPemFiles()* within the included *Crypto* class can be used.

## Running akka-blockchain on separate JVMs

Git-clone the repo to a local disk, open up separate shell command line terminals and launch the application from the *project-root* on separate terminals by binding them to different port#.

1. Start the cluster seed node #1 at port# 2551 using account0 as miner's account
```bash
$ sbt "runMain akkablockchain.Main 2551 src/main/resources/keys/account0_public.pem [test]"
```
2. Start the cluster seed node #2 at port# 2552 using account1 as miner's account
```bash
$ sbt "runMain akkablockchain.Main 2552 src/main/resources/keys/account1_public.pem [test]"
```
3. Start another cluster node at port# 2553 with using account2 as miner's account
```bash
$ sbt "runMain akkablockchain.Main 2553 src/main/resources/keys/account2_public.pem [test]"
```
4. Start additional cluster nodes at different port#s, if wanted
