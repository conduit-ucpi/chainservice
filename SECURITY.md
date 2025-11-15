# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.x.x   | :white_check_mark: |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

If you discover a security vulnerability in the Chain Service, please follow these steps:

### 1. Private Disclosure

Send a detailed report to: **security@conduit-ucpi.com**

Include:
- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Affected versions
- Suggested fix (if any)
- Your contact information

### 2. Response Timeline

- **Initial Response**: Within 48 hours
- **Status Update**: Within 7 days with severity assessment
- **Fix Timeline**: Based on severity (critical issues prioritized)
- **Public Disclosure**: After fix deployment, coordinated with reporter

### 3. Severity Levels

**Critical**: Immediate threat (private key exposure, unauthorized fund access)
- Response: Immediate
- Bounty: Up to $10,000

**High**: Significant risk (authentication bypass, data leak)
- Response: Within 7 days
- Bounty: Up to $5,000

**Medium**: Notable security issue with workarounds
- Response: Within 30 days
- Bounty: Up to $1,000

**Low**: Minor security concern
- Response: Best effort
- Bounty: Recognition

## Security Measures

### Private Key Protection

- **Never logged**: Private keys excluded from all logging
- **Environment variables only**: Never hardcoded in code
- **Encrypted at rest**: Consider using secret management services (Vault, AWS Secrets Manager)
- **Memory cleanup**: Sensitive data cleared after use

### Authentication & Authorization

- Delegated to `web3userservice` - no local authentication
- JWT token validation on all protected endpoints
- Admin-only endpoints properly secured
- Rate limiting on public endpoints

### API Security

- Input validation on all endpoints
- CORS properly configured
- SQL injection prevention (parameterized queries)
- XSS protection headers
- No sensitive data in error responses

### Blockchain Security

- Transaction signature verification
- Gas limit validation
- Nonce management to prevent replay
- RPC endpoint timeout protection

### Dependencies

- Regular security updates
- Automated vulnerability scanning
- Minimal dependency footprint
- Trusted sources only (Maven Central)

## Known Limitations

- Relayer wallet holds funds for gas - requires monitoring
- Dependency on external RPC providers
- Trust in user service for authentication
- Block timestamp reliance for contract queries

## Configuration Security

### Production Checklist

- [ ] Use HTTPS for all service URLs
- [ ] Rotate JWT secrets regularly
- [ ] Enable authentication (`AUTH_ENABLED=true`)
- [ ] Configure proper CORS origins
- [ ] Use secure RPC endpoints
- [ ] Monitor relayer wallet balance
- [ ] Set appropriate gas limits
- [ ] Enable rate limiting

### Secrets Management

**Required Secrets:**
- `RELAYER_PRIVATE_KEY` - Secure with secret manager
- Database credentials (if applicable)
- External API keys

**Never commit:**
- `.env` or `.env.local` files
- Private keys or mnemonics
- Production configuration files

## Incident Response

In case of security incident:

1. **Immediate**: Disable affected endpoints if possible
2. **Assessment**: Evaluate impact and affected users
3. **Communication**: Notify stakeholders
4. **Remediation**: Deploy fixes
5. **Post-mortem**: Document and improve processes

## Audit Status

- **Last Security Review**: [Date - if applicable]
- **Auditor**: [Firm - if applicable]
- **Findings**: [Link to report - if applicable]

## Contact

- Security Email: security@conduit-ucpi.com
- GitHub Security Advisories: [Link]

## Recognition

Security researchers who responsibly disclose vulnerabilities will be acknowledged in our security hall of fame with their permission.

Thank you for helping keep Conduit UCPI secure!
