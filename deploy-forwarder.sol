// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/metatx/MinimalForwarder.sol";

// Simple deployment contract for MinimalForwarder
contract DeployForwarder {
    function deploy() external returns (address) {
        MinimalForwarder forwarder = new MinimalForwarder();
        return address(forwarder);
    }
}