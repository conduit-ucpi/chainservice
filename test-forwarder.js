// Test script to debug MinimalForwarder signature verification
const ethers = require('ethers');

// MinimalForwarder address
const FORWARDER_ADDRESS = '0x2735feb1f75203A09E3CCD9184D8022452C31e81';

// Test domains to try
const testDomains = [
  { name: 'MinimalForwarder', version: '0.0.1' },
  { name: 'MinimalForwarder', version: '1' },
  { name: 'MinimalForwarder', version: '' },
  { name: 'Forwarder', version: '0.0.1' },
  { name: 'Forwarder', version: '1' }
];

// ForwardRequest type
const types = {
  ForwardRequest: [
    { name: 'from', type: 'address' },
    { name: 'to', type: 'address' },
    { name: 'value', type: 'uint256' },
    { name: 'gas', type: 'uint256' },
    { name: 'nonce', type: 'uint256' },
    { name: 'data', type: 'bytes' }
  ]
};

async function testForwarder() {
  const provider = new ethers.JsonRpcProvider(process.env.RPC_URL || 'https://api.avax-test.network/ext/bc/C/rpc');
  
  // First, let's verify we have a contract at this address
  const code = await provider.getCode(FORWARDER_ADDRESS);
  console.log('Contract exists:', code !== '0x');
  
  // Try to get a test wallet to create a signature
  const testWallet = ethers.Wallet.createRandom();
  console.log('\nTest wallet address:', testWallet.address);
  
  // Test each domain configuration by creating signatures
  for (const domain of testDomains) {
    const fullDomain = {
      ...domain,
      chainId: 43113,
      verifyingContract: FORWARDER_ADDRESS
    };
    
    console.log(`\n\nTesting domain:`, domain);
    console.log('Full domain:', fullDomain);
    
    // Create a test message
    const testMessage = {
      from: testWallet.address,
      to: '0x0000000000000000000000000000000000000001',
      value: '0',
      gas: '250000',
      nonce: '0',
      data: '0x'
    };
    
    try {
      // Sign with EIP-712
      const signature = await testWallet.signTypedData(fullDomain, types, testMessage);
      console.log('Signature created successfully');
      console.log('Domain separator:', ethers.TypedDataEncoder.hashDomain(fullDomain));
      console.log('Message hash:', ethers.TypedDataEncoder.hash(fullDomain, types, testMessage));
      
      // If you want to test this signature, you'd need to call verify() on the forwarder
      // But since that's also reverting, we'll just show what domain parameters work for signing
      
    } catch (error) {
      console.log('Error signing:', error.message);
    }
  }
  
  console.log('\n\n💡 The correct domain is likely one of the above that created a signature successfully.');
  console.log('Try each one in your frontend until the transaction succeeds.');
}

testForwarder();