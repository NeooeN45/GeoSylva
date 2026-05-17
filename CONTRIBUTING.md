# Contributing to GeoSylva

Thank you for your interest in contributing to GeoSylva! This document provides guidelines and standards for contributors.

## Code Standards

### Kotlin Style
- Follow official Kotlin coding conventions
- Use meaningful variable and function names (intent-revealing nouns/verbs)
- Functions should have max 30 lines and max 3 parameters
- Cyclomatic complexity: max 5 per function
- No commented-out code - delete it, git remembers
- No magic numbers - every literal gets a named constant
- No global mutable state
- No `any` type in TypeScript, no untyped params in Python, no `!!` in Kotlin

### Architecture
- **Clean Architecture**: data/domain/presentation separation
- **Repository Pattern**: interfaces in domain, implementations in data layer
- **No business logic in controllers/handlers/ViewModels**
- **No database calls in service layer** - only repository interfaces
- **DTOs at API boundaries**, domain models inside the system
- **Feature flags** for incomplete features - never ship dead code paths
- **Configuration via environment variables**, never hardcoded

### Naming Conventions
- **Variables**: intent-revealing nouns - `userCount`, not `n` or `data`
- **Booleans**: `is`, `has`, `can`, `should` prefix - `isAuthenticated`, `hasPermission`
- **Functions**: action verbs - `fetchUser`, `validateToken`, `parseResponse`
- **Constants**: SCREAMING_SNAKE in Python/Kotlin, UPPER_SNAKE in TypeScript
- **No abbreviations** except: `id`, `url`, `api`, `db`, `err`, `ctx`, `req`, `res`
- **Test functions**: `should_[expected]_when_[condition]`

## Git Workflow

### Commit Messages
Use **Conventional Commits** format:
```
type(scope): description

[optional body]

[optional footer]
```

**Types**: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`, `ci`, `revert`
- Max 72 characters in subject line
- Body explains *why*, not *what*
- Examples:
  - `feat(forestry): add tree height measurement with camera`
  - `fix(gps): resolve crash when location permission denied`
  - `refactor(ui): extract map components from MapScreen.kt`

### Branch Names
Format: `type/short-description`
- `feat/user-auth`
- `fix/token-refresh`
- `refactor/martelage-models`

### Rules
- **Never force-push** to `main` or `develop`
- **Never commit**: `.env`, secrets, `node_modules`, build artifacts, IDE configs
- **Every commit must pass lint + tests** (enforced via pre-commit hook when possible)
- **PRs**: small and focused - one concern per PR, reviewable in <30 min
- **Squash merge** feature branches - keep `main` history linear and clean

## Testing

### Coverage Requirements
- **80% coverage** on domain/business logic
- **60% minimum** overall coverage
- **Structure**: Arrange → Act → Assert, clearly separated
- **One logical assertion** per test
- **Prefer fakes and stubs** over mocks - tests that verify behavior, not implementation

### Test Types
- **Unit tests**: For business logic, calculations, repositories
- **Integration tests**: For all external boundaries (DB, API, file system)
- **Room migration tests**: Use `MigrationTestHelper` for all DB migrations
- **No `sleep()` or time-based assertions** - use deterministic triggers

### Edge Cases to Always Test
- Empty input
- Null/None values
- Max values
- Concurrent access

## Security

### Input Validation
- **Sanitize all user input** at the boundary - never trust external data
- **Use parameterized queries** - never concatenate SQL strings
- **Hash passwords** with bcrypt/argon2 - never MD5/SHA1

### Permissions
- **JWT tokens**: short expiry (15min access, 7d refresh), validate on every request
- **No sensitive data in logs**, URLs, or error messages returned to clients
- **Dependencies**: check for known CVEs before adding; pin versions in production

## Performance

### Guidelines
- **Measure before optimizing** - never guess bottlenecks
- **Database**: always add indexes for foreign keys and WHERE clause columns
- **N+1 queries**: always batch or eager-load related data
- **Cache at the right layer**: DB query cache → service cache → HTTP cache
- **Async I/O** for all network and file operations - never block the main thread
- **Pagination** for all list endpoints - never return unbounded collections

### Android Specific
- **Log slow queries** (>100ms) in dev; alert on slow queries in production
- **Lazy loading** for large data structures
- **Compose recomposition**: use `remember` and `derivedStateOf` appropriately

## Code Review Process

### Before Submitting PR
1. **Run tests**: `./gradlew test`
2. **Run lint**: `./gradlew lint`
3. **Check formatting**: Ensure code follows style guidelines
4. **Update documentation**: If adding new features, update README

### Review Guidelines
- **Focus on logic and architecture**, not style (handled by tools)
- **Verify test coverage** for new code
- **Check for security vulnerabilities**
- **Ensure performance considerations** are addressed
- **Validate that commit messages** follow conventions

## Development Environment Setup

### Prerequisites
- Android Studio Hedgehog | 2023.1.1 or later
- JDK 17
- Kotlin 1.9.23+
- Git

### Setup Steps
1. Clone repository
2. Open in Android Studio
3. Sync Gradle
4. Run tests to verify setup: `./gradlew test`

## Getting Help

- **Issues**: Use GitHub Issues for bug reports and feature requests
- **Discussions**: Use GitHub Discussions for questions and ideas
- **Documentation**: Check `docs/` folder for detailed guides

## License

By contributing to GeoSylva, you acknowledge that:

1. **NO FORKS ALLOWED** - Creating forks of this codebase is strictly prohibited
2. **PROPRIETARY CODE** - All contributions become exclusive property of GeoSylva
3. **NO REDISTRIBUTION** - Code cannot be shared or redistributed outside the project
4. **COMMERCIAL USE RESTRICTED** - Only for direct professional forestry activities

This is a **proprietary restrictive license** - not open source. All rights reserved by GeoSylva.
