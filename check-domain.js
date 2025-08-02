// Quick script to check what domain parameters the MinimalForwarder uses
const { ethers } = require('ethers');

// MinimalForwarder ABI (just the parts we need)
const FORWARDER_ABI = [
  "function DOMAIN_SEPARATOR() view returns (bytes32)",
  "function getNonce(address from) view returns (uint256)"
];

async function checkDomain() {
  const provider = new ethers.JsonRpcProvider(process.env.RPC_URL);
  const forwarder = new ethers.Contract(
    "0x2735feb1f75203A09E3CCD9184D8022452C31e81", // Your forwarder address
    FORWARDER_ABI,
    provider
  );

  try {
    const domainSeparator = await forwarder.DOMAIN_SEPARATOR();
    console.log("Domain Separator:", domainSeparator);
    
    const nonce = await forwarder.getNonce("0x43cD4eDE85fa5334050325985cfdD9B1Ce58671a");
    console.log("User Nonce:", nonce.toString());
    
    // Try to reverse engineer what domain separator this represents
    // Standard MinimalForwarder uses:
    const testDomains = [
      { name: "MinimalForwarder", version: "0.0.1" },
      { name: "MinimalForwarder", version: "1" },
      { name: "EIP712Forwarder", version: "1" },
      { name: "MinimalForwarder", version: "" }
    ];
    
    for (const domain of testDomains) {
      const testSeparator = ethers.TypedDataEncoder.hashDomain({
        name: domain.name,
        version: domain.version,
        chainId: 43113,
        verifyingContract: "0x2735feb1f75203A09E3CCD9184D8022452C31e81"
      });
      
      if (testSeparator === domainSeparator) {
        console.log("✅ MATCH FOUND:", domain);
        return;
      }
    }
    
    console.log("❌ No standard domain match found");
    
  } catch (error) {
    console.error("Error:", error);
  }
}

checkDomain();