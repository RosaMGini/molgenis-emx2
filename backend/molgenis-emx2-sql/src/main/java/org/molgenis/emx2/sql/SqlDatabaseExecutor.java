package org.molgenis.emx2.sql;

import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.name;
import static org.molgenis.emx2.Constants.MG_USER_PREFIX;
import static org.molgenis.emx2.sql.SqlDatabase.*;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;

class SqlDatabaseExecutor {
  private SqlDatabaseExecutor() {
    // hide
  }

  static void executeCreateUser(DSLContext jooq, String user) {
    try {
      String userName = MG_USER_PREFIX + user;
      jooq.execute("CREATE ROLE {0} WITH NOLOGIN", name(userName));
      if (!ADMIN.equals(user) && !USER.equals(user) && !ANONYMOUS.equals(user)) {
        // non-system users get role 'user' as way to identify all users
        jooq.execute("GRANT {0} TO {1}", name(MG_USER_PREFIX + USER), name(userName));
        // all users can see what anynymous can see
        jooq.execute("GRANT {0} TO {1}", name(MG_USER_PREFIX + ANONYMOUS), name(userName));
      }
    } catch (DataAccessException dae) {
      throw new SqlMolgenisException("Add user failed", dae);
    }
  }

  static void executeGrantCreateSchema(DSLContext jooq, String user) {
    try {
      String databaseName = jooq.fetchOne("SELECT current_database()").get(0, String.class);
      jooq.execute(
          "GRANT CREATE ON DATABASE {0} TO {1}", name(databaseName), name(MG_USER_PREFIX + user));
    } catch (DataAccessException dae) {
      throw new SqlMolgenisException(dae);
    }
  }

  static void executeCreateRole(DSLContext jooq, String role) {
    jooq.execute(
        "DO $$\n"
            + "BEGIN\n"
            + "    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = {0}) THEN\n"
            + "        CREATE ROLE {1};\n"
            + "    END IF;\n"
            + "END\n"
            + "$$;\n",
        inline(role), name(role));
  }
}
