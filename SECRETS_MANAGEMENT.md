# Secrets Management for Cloud Foundry Deployment

## Overview

This project uses CF CLI's native `--vars-file` feature to inject secrets into the manifest during deployment without committing them to version control.

## File Structure

- **`secrets.yml`** - Contains actual secret values (gitignored, never commit!)
- **`secrets.yml.template`** - Template showing required variables (committed to git)
- **`manifest.yml`** - Contains variable placeholders like `((VARIABLE_NAME))`
- **`deploy-with-secrets.sh`** - Deployment script that uses `cf push --vars-file secrets.yml`

## Setup

### 1. Create your secrets file

```bash
cp secrets.yml.template secrets.yml
# Edit secrets.yml and fill in your actual values
```

### 2. Configure variables in secrets.yml

The variable names MUST match what the application expects in `application-cf.properties`:

```yaml
# secrets.yml
OPENAI_API_KEY: 'your-actual-api-key'
OPENAI_MODEL: 'gpt-5-nano'
```

### 3. Use variables in manifest.yml

Reference secrets using double-parentheses syntax:

```yaml
# manifest.yml
env:
  OPENAI_API_KEY: ((OPENAI_API_KEY))
  OPENAI_MODEL: ((OPENAI_MODEL))
```

## Deployment

### Without building:
```bash
./deploy-with-secrets.sh
```

### With building:
```bash
./deploy-with-secrets.sh --build
# or
./deploy-with-secrets.sh -b
```

## Important Notes

### Variable Name Matching

The environment variable names in `secrets.yml` and `manifest.yml` MUST match the property names expected by Spring Boot in `application-cf.properties`.

For example, if `application-cf.properties` has:
```properties
spring.ai.openai.api-key=${OPENAI_API_KEY:dummy-key}
```

Then use:
```yaml
# secrets.yml
OPENAI_API_KEY: 'sk-...'

# manifest.yml
env:
  OPENAI_API_KEY: ((OPENAI_API_KEY))
```

### Avoid Duplicate Property Settings

⚠️ **IMPORTANT**: Do NOT set the same property in multiple places:
- If a property is already set in `application.properties` (e.g., `max_completion_tokens=8000`), do NOT override it in `secrets.yml` unless you need a different value
- Setting both causes conflicts in Spring AI

### What Goes in secrets.yml?

**DO include:**
- API keys
- Passwords
- Tokens
- Connection strings with credentials
- Model names (if they vary per environment)

**DON'T include:**
- Non-secret configuration (put in manifest.yml directly)
- Properties already set in application.properties
- Build-time configuration

## Troubleshooting

### "400 Bad Request" from OpenAI API

Check for duplicate property settings:
```bash
# View all environment variables
cf env imc-chatbot

# Look for duplicate settings like both maxTokens and max_completion_tokens
```

### Variables not substituting

1. Ensure variable names match exactly (case-sensitive)
2. Check secrets.yml syntax (YAML format)
3. Verify you're using `--vars-file` in deploy script

### Secrets file not found

```bash
# Create from template
cp secrets.yml.template secrets.yml
```

## Security Best Practices

1. **Never commit secrets.yml** - It's in .gitignore
2. **Rotate secrets regularly** - Update secrets.yml and redeploy
3. **Use CF service bindings** for services when possible
4. **Audit access** - Track who has access to secrets.yml
5. **Use different secrets per environment** - dev, staging, prod

## Migration from Old Approach

If you were previously setting secrets via `cf set-env`:

1. Create secrets.yml with current values
2. Deploy with `./deploy-with-secrets.sh`
3. Remove old env vars: `cf unset-env imc-chatbot OPENAI_API_KEY && cf restage imc-chatbot`

## References

- [CF CLI Variable Substitution](https://docs.cloudfoundry.org/devguide/deploy-apps/manifest-attributes.html#variable-substitution)
- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
