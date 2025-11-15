# Contributing to Conduit UCPI Chain Service

Thank you for your interest in contributing to the Conduit UCPI Chain Service! This Kotlin/Spring Boot microservice handles blockchain transaction relaying with gas sponsorship.

## Code of Conduct

By participating in this project, you agree to maintain a respectful and collaborative environment for all contributors.

## How to Contribute

### Reporting Bugs

1. Check if the bug has already been reported in [Issues](https://github.com/conduit-ucpi/chainservice/issues)
2. If not, create a new issue with:
   - Clear title and description
   - Steps to reproduce
   - Expected vs actual behavior
   - Environment details (Java version, network, etc.)
   - Relevant logs or stack traces

### Suggesting Enhancements

1. Check existing [Issues](https://github.com/conduit-ucpi/chainservice/issues) for similar suggestions
2. Create a new issue describing:
   - The enhancement and its benefits
   - Potential implementation approach
   - Any performance or security implications

### Pull Requests

1. **Fork the repository** and create a new branch from `main`
2. **Make your changes** following Kotlin coding standards
3. **Add tests** for any new functionality
4. **Run the test suite**: `./gradlew test`
5. **Ensure build passes**: `./gradlew build`
6. **Update documentation** if needed
7. **Submit a pull request** with a clear description

## Development Workflow

### Setup

```bash
# Clone your fork
git clone https://github.com/conduit-ucpi/chainservice.git
cd chainservice

# Create .env file
cp .env.example .env
# Edit .env with your configuration

# Build project
./gradlew build
```

### Testing

```bash
# Run tests
./gradlew test

# Run tests with coverage
./gradlew test jacocoTestReport

# Run application locally
./gradlew bootRun
```

### Before Submitting

- [ ] All tests pass (`./gradlew test`)
- [ ] Build succeeds (`./gradlew build`)
- [ ] Code follows Kotlin style guide
- [ ] New tests added for new functionality
- [ ] Documentation updated (README, KDoc comments)
- [ ] No new compiler warnings

## Coding Standards

### Kotlin Style Guide

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use KDoc comments for public APIs
- Prefer immutability (val over var)
- Use data classes for DTOs
- Leverage Kotlin's null safety

### Spring Boot Best Practices

- Use dependency injection (constructor injection preferred)
- Proper exception handling with `@ControllerAdvice`
- Validate inputs with annotations
- Use `@Transactional` appropriately
- Follow RESTful API conventions

### Git Commit Messages

- Use present tense ("Add feature" not "Added feature")
- Use imperative mood ("Move cursor to..." not "Moves cursor to...")
- First line: brief summary (50 chars or less)
- Reference issues when relevant

Example:
```
Add caching layer for contract queries

- Implement Redis-based caching for frequent queries
- Add cache invalidation on contract state changes
- Include performance benchmarks in tests

Closes #456
```

## Architecture Guidelines

### Plugin System

When adding new plugins:
- Implement `BlockchainServicePlugin` interface
- Auto-register via Spring component scanning
- Keep plugins independent and focused
- Document plugin-specific configuration

### Security Considerations

- Never log private keys or sensitive data
- Validate all external inputs
- Use parameterized queries (prevent injection)
- Implement rate limiting for public endpoints
- Proper error messages (don't leak internal details)

## Testing Guidelines

### Test Coverage

- Aim for >80% code coverage
- Test happy paths and edge cases
- Mock external services (blockchain RPC, user service)
- Use MockK for Kotlin-friendly mocking

### Test Organization

```kotlin
@Test
fun `should relay transaction successfully`() {
    // Given
    val signedTx = "0x..."

    // When
    val result = service.relayTransaction(signedTx)

    // Then
    assertThat(result.status).isEqualTo(TransactionStatus.SUCCESS)
}
```

## Review Process

1. Automated tests must pass
2. Code review by at least one maintainer
3. Security review for authentication/authorization changes
4. Documentation review
5. Final approval and merge

## Questions?

Feel free to open an issue with the `question` label or reach out to the maintainers.

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
