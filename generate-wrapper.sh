#!/bin/bash

# Generate Web3j wrapper for MinimalForwarder
web3j generate solidity \
  -a src/MinimalForwarder.sol \
  -o src/main/kotlin \
  -p com.conduit.chainservice.contracts