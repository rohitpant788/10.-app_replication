# Database Setup Guide - Neon DB & Liquibase

This guide covers the database setup for the Data Microservice using **Neon DB** (serverless PostgreSQL) and **Liquibase** for database migrations.

## Table of Contents
- [Neon DB Overview](#neon-db-overview)
- [Configuration](#configuration)
- [Liquibase Overview](#liquibase-overview)
- [Creating Migrations](#creating-migrations)
- [Common Liquibase Commands](#common-liquibase-commands)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

---

## Neon DB Overview

[Neon](https://neon.tech) is a serverless PostgreSQL platform that provides:
- ✅ Automatic scaling
- ✅ Built-in connection pooling
- ✅ Branching for different environments
- ✅ Always-on availability

### Connection Details

Database connection credentials are configured externally in `application-my.properties` in the project root.

All microservices import this configuration using:

```properties
spring.config.import=optional:file:../application-my.properties
```

> **Security Note**: Database credentials are kept in `application-my.properties` which is NOT tracked by git. See the main README.md for setup instructions.

---

## Configuration

### Database Connection

The application uses **HikariCP** for connection pooling (included with Spring Boot):

```properties
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=20000
```

### JPA/Hibernate Configuration

Hibernate is configured to **NOT** manage the schema (Liquibase handles this):

```properties
spring.jpa.hibernate.ddl-auto=none
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.show-sql=true
```

---

## Liquibase Overview

**Liquibase** is a database schema change management tool that:
- Tracks database changes using changelog files
- Applies changes automatically on application startup
- Supports rollback of changes
- Works across different database platforms

### How It Works

1. **Changelog Master File**: `db/changelog/db.changelog-master.yaml`
   - References all individual changelog files in order
   
2. **Individual Changesets**: `db/changelog/changes/XXX-description.yaml`
   - Each file contains specific database changes
   - Changesets are uniquely identified by `id` and `author`

3. **Tracking Table**: `databasechangelog`
   - Liquibase automatically creates this table
   - Tracks which changesets have been applied

---

## Creating Migrations

### File Naming Convention

Use the following naming pattern for changelog files:

```
XXX-description.yaml
```

Examples:
- `001-initial-schema.yaml`
- `002-add-users-table.yaml`
- `003-add-email-index.yaml`
- `004-alter-users-add-phone.yaml`

### Step-by-Step: Creating a New Migration

**Step 1**: Create a new YAML file in `src/main/resources/db/changelog/changes/`

**Step 2**: Define your changeset:

```yaml
databaseChangeLog:
  - changeSet:
      id: 002-add-users-table
      author: your-name
      comment: Create users table for authentication
      changes:
        - createTable:
            tableName: users
            columns:
              - column:
                  name: id
                  type: BIGSERIAL
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: username
                  type: VARCHAR(50)
                  constraints:
                    nullable: false
                    unique: true
              - column:
                  name: email
                  type: VARCHAR(100)
                  constraints:
                    nullable: false
              - column:
                  name: created_at
                  type: TIMESTAMP
                  defaultValueComputed: CURRENT_TIMESTAMP
      rollback:
        - dropTable:
            tableName: users
```

**Step 3**: Add the file to the master changelog:

Edit `db.changelog-master.yaml`:

```yaml
databaseChangeLog:
  - include:
      file: db/changelog/changes/001-initial-schema.yaml
  - include:
      file: db/changelog/changes/002-add-users-table.yaml  # ← Add this line
```

**Step 4**: Restart the application - Liquibase will automatically apply the changes!

---

## Common Liquibase Commands

### Supported Change Types

Liquibase supports many change types:

- **Create Operations**
  - `createTable`
  - `createIndex`
  - `createSequence`
  - `createView`

- **Alter Operations**
  - `addColumn`
  - `modifyColumn`
  - `renameColumn`
  - `addForeignKeyConstraint`

- **Drop Operations**
  - `dropTable`
  - `dropColumn`
  - `dropIndex`

- **Data Operations**
  - `insert`
  - `update`
  - `delete`
  - `loadData` (from CSV)

### Example: Adding a Column

```yaml
- changeSet:
    id: 003-add-phone-to-users
    author: your-name
    changes:
      - addColumn:
          tableName: users
          columns:
            - column:
                name: phone
                type: VARCHAR(20)
    rollback:
      - dropColumn:
          tableName: users
          columnName: phone
```

### Example: Creating an Index

```yaml
- changeSet:
    id: 004-add-email-index
    author: your-name
    changes:
      - createIndex:
          tableName: users
          indexName: idx_users_email
          columns:
            - column:
                name: email
    rollback:
      - dropIndex:
          tableName: users
          indexName: idx_users_email
```

### Example: Inserting Data

```yaml
- changeSet:
    id: 005-seed-users
    author: your-name
    changes:
      - insert:
          tableName: users
          columns:
            - column:
                name: username
                value: admin
            - column:
                name: email
                value: admin@example.com
```

---

## Best Practices

### ✅ DO

1. **Never modify existing changesets** - Once applied to production, they're immutable
2. **Always include rollback** - Define how to undo your changes
3. **Use descriptive IDs** - Make it easy to identify what each changeset does
4. **One logical change per changeset** - Don't combine unrelated changes
5. **Test locally first** - Verify migrations work before deploying
6. **Use comments** - Explain why the change is being made
7. **Sequential numbering** - Use `001`, `002`, `003` for easy sorting

### ❌ DON'T

1. **Don't delete old changelog files** - They're part of your history
2. **Don't use `dropFirst=true` in production** - This will delete all data!
3. **Don't hardcode environment-specific data** - Use contexts or profiles
4. **Don't skip version control** - Always commit changelog files

---

## Troubleshooting

### Issue: Liquibase fails on startup

**Symptom**: Application fails to start with Liquibase error

**Solutions**:
1. Check database connectivity - can you connect to Neon DB?
2. Verify changelog files have correct syntax (YAML is indent-sensitive!)
3. Check `databasechangelog` table for already-applied changesets
4. Review application logs for specific error messages

### Issue: Changeset already exists

**Symptom**: Error saying changeset ID already exists

**Solution**: Each changeset must have a unique combination of `id` + `author`. Change your changeset ID to something unique.

### Issue: Connection timeout

**Symptom**: Application can't connect to Neon DB

**Solutions**:
1. Verify your Neon DB is active (not paused due to inactivity)
2. Check your connection string format
3. Ensure SSL mode is set to `require`
4. Verify credentials are correct

### Issue: Need to rollback a migration

**Solution**: 
Unfortunately, Spring Boot doesn't provide a built-in way to rollback. Options:
1. Create a new migration that reverses the changes
2. Use Liquibase CLI tool for manual rollback
3. Connect to database and manually revert changes (last resort)

### Issue: Schema already exists error

**Symptom**: Table already exists when trying to create it

**Solutions**:
1. Check if the changeset was already applied (look in `databasechangelog` table)
2. Use `preconditions` to check if table exists before creating:

```yaml
- changeSet:
    id: example
    author: you
    preconditions:
      - onFail: MARK_RAN
      - not:
          - tableExists:
              tableName: my_table
    changes:
      - createTable:
          tableName: my_table
```

---

## Viewing Your Database

### Using Neon Console

1. Go to [console.neon.tech](https://console.neon.tech)
2. Select your project
3. Click on "Tables" to see all tables
4. Click on "SQL Editor" to run queries

### Using psql (Command Line)

Retrieve your database credentials from `../application-my.properties` and connect using:

```bash
psql 'postgresql://[YOUR_USERNAME]:[YOUR_PASSWORD]@[YOUR_DB_HOST]/neondb?sslmode=require'
```

Common commands:
```sql
\dt              -- List all tables
\d table_name    -- Describe a table
SELECT * FROM databasechangelog;  -- See applied migrations
```

---

## Next Steps

1. ✅ Start the application to apply the initial migration
2. ✅ Verify the `application_info` table was created in Neon DB
3. ✅ Create your first custom migration for your business logic
4. ✅ Review the example migration: `001-initial-schema.yaml`

---

## Additional Resources

- [Liquibase Documentation](https://docs.liquibase.com/)
- [Neon Documentation](https://neon.tech/docs/)
- [Spring Boot with Liquibase](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.migration-tool.liquibase)
