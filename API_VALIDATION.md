# API Validation for Chain Service

The Chain Service includes comprehensive API validation tasks to ensure compatibility with dependent services (User Service, Contract Service, and Email Service).

## Overview

The API validation system:
- Fetches OpenAPI specifications from dependent services
- Validates that expected endpoints exist and match client expectations
- Generates detailed reports on compatibility issues
- Can fail builds when critical mismatches are found
- Handles network failures gracefully

## Gradle Tasks

### Main Validation Tasks

```bash
# Validate all dependent services
./gradlew validateApiDependencies

# Validate specific services
./gradlew validateUserServiceApi
./gradlew validateContractServiceApi
./gradlew validateEmailServiceApi

# Generate detailed validation report
./gradlew generateApiValidationReport
```

### Integration with Build

API validation runs automatically during the build process when enabled:

```bash
# Build with API validation (default)
./gradlew build

# Build without API validation
./gradlew build -Papi.validation.enabled=false
```

## Configuration

Configure API validation in `gradle.properties`:

```properties
# Enable/disable API validation during build
api.validation.enabled=true

# Fail build if API validation finds mismatches
api.validation.failOnMismatch=true

# Timeout for API calls in milliseconds
api.validation.timeout=30000

# Environment context for validation
api.validation.environment=development
```

You can also override these properties from the command line:

```bash
./gradlew validateApiDependencies -Papi.validation.timeout=60000
./gradlew build -Papi.validation.failOnMismatch=false
```

## Environment Variables

Set service URLs via environment variables:

```bash
export USER_SERVICE_URL=http://localhost:8080
export CONTRACT_SERVICE_URL=http://localhost:8081
export EMAIL_SERVICE_URL=http://localhost:8979

./gradlew validateApiDependencies
```

## What Gets Validated

### User Service
- `/api/user/identity` (GET) - Token validation endpoint
- Response schema for user identity data
- Authentication requirements

### Contract Service  
- `/api/contracts/{contractId}` (PATCH) - Contract update endpoint
- `/api/contracts/{contractId}` (GET) - Contract retrieval endpoint
- Request/response schemas for contract management
- Authentication requirements

### Email Service
- `/api/email/payment-notification` (POST) - Payment notification emails
- `/api/email/dispute-raised` (POST) - Dispute notification emails  
- `/api/email/dispute-resolved` (POST) - Resolution notification emails
- Request/response schemas for email data
- Authentication requirements

## Validation Reports

Reports are generated in `build/reports/api-validation/`:

- `validation-report.json` - Machine-readable JSON report for CI/CD
- `validation-report.md` - Human-readable Markdown report
- `validation-summary.txt` - Brief summary for console output

### Sample JSON Report Structure

```json
{
  "timestamp": "2024-01-01T12:00:00Z",
  "overallStatus": "PASSED",
  "totalServices": 3,
  "passedServices": 3,
  "failedServices": 0,
  "results": [
    {
      "serviceName": "User Service",
      "serviceUrl": "http://localhost:8080",
      "isServiceAvailable": true,
      "errors": [],
      "warnings": [],
      "validatedEndpoints": [...]
    }
  ]
}
```

## Error Handling

The validation system gracefully handles:

- **Service Unavailable**: When dependent services are not running
- **Network Timeouts**: Configurable timeout for API calls
- **Missing OpenAPI Specs**: When services don't expose `/api-docs`
- **Invalid Specifications**: When OpenAPI specs are malformed
- **Schema Mismatches**: When endpoint schemas don't match expectations

## CI/CD Integration

### GitHub Actions Example

```yaml
- name: Run API Validation
  run: ./gradlew validateApiDependencies
  env:
    USER_SERVICE_URL: ${{ vars.USER_SERVICE_URL }}
    CONTRACT_SERVICE_URL: ${{ vars.CONTRACT_SERVICE_URL }}
    EMAIL_SERVICE_URL: ${{ vars.EMAIL_SERVICE_URL }}

- name: Upload Validation Report
  if: always()
  uses: actions/upload-artifact@v3
  with:
    name: api-validation-report
    path: build/reports/api-validation/
```

### Build Script Integration

```bash
#!/bin/bash
set -e

# Start dependent services for testing
docker-compose up -d user-service contract-service email-service

# Wait for services to be ready
sleep 30

# Run API validation
./gradlew validateApiDependencies

# Continue with rest of build
./gradlew build
```

## Development Workflow

### Adding New API Dependencies

1. **Update Client Code**: Add new service client in `src/main/kotlin/com/conduit/chainservice/service/`

2. **Define Expected Endpoints**: Create or update client spec in `src/main/kotlin/com/conduit/chainservice/validation/`:
   ```kotlin
   object NewServiceClientSpec {
       fun getExpectedEndpoints(): List<ExpectedEndpoint> {
           return listOf(
               ExpectedEndpoint(
                   path = "/api/new-endpoint",
                   method = "POST",
                   description = "Description of what this endpoint does",
                   requestBodySchema = mapOf(...),
                   responseSchema = mapOf(...),
                   requiresAuthentication = true,
                   tags = listOf("critical")
               )
           )
       }
   }
   ```

3. **Update Validator**: Add validation method in `ApiValidator.kt`

4. **Update Runner**: Add service validation in `ApiValidationRunner.kt`

5. **Add Gradle Task**: Add new validation task in `build.gradle.kts`

6. **Test**: Run validation and verify it works correctly

### Debugging Validation Issues

1. **Check Service Availability**:
   ```bash
   curl http://localhost:8080/actuator/health
   ```

2. **Verify OpenAPI Spec**:
   ```bash
   curl http://localhost:8080/api-docs
   ```

3. **Run Individual Service Validation**:
   ```bash
   ./gradlew validateUserServiceApi --info
   ```

4. **Generate Detailed Report**:
   ```bash
   ./gradlew generateApiValidationReport
   cat build/reports/api-validation/validation-report.md
   ```

## Best Practices

1. **Keep Client Specs Updated**: When service APIs change, update the corresponding client spec
2. **Use Critical Tags**: Mark essential endpoints with "critical" tag for priority validation
3. **Handle Service Unavailability**: Don't fail builds when services are temporarily unavailable in development
4. **Regular Validation**: Run API validation in CI/CD to catch integration issues early
5. **Monitor Reports**: Review validation reports to identify API evolution and breaking changes

## Troubleshooting

### Common Issues

**Build fails with "Service unavailable"**:
- Ensure dependent services are running
- Check service URLs are correct
- Verify network connectivity
- Consider setting `api.validation.failOnMismatch=false` for development

**OpenAPI spec not found**:
- Verify service exposes `/api-docs` endpoint
- Check service is using SpringDoc OpenAPI
- Ensure service is fully started before validation

**Schema validation errors**:
- Compare actual API response with expected schema
- Update client spec if API legitimately changed
- Check for version mismatches between services

**Timeout errors**:
- Increase `api.validation.timeout` value
- Check if services are responding slowly
- Verify network latency to services