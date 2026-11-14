#!/usr/bin/env python3
"""Compare literal 46→49 migration SQL with the independently generated Room49 schema.

Uses host SQLite, not Android/SQLCipher. This does not replace device migration tests.
Fails if the selected migrations cease to contain only the checked literal SQL.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sqlite3


def load_schema(connection, schema):
    for entity in schema["database"]["entities"]:
        table = entity["tableName"]
        connection.execute(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity["indices"]:
            connection.execute(index["createSql"].replace("${TABLE_NAME}", table))
    for view in schema["database"].get("views", []):
        connection.execute(view["createSql"])


def table_signature(connection, table):
    quoted = '"' + table.replace('"', '""') + '"'
    columns = connection.execute(f"PRAGMA table_info({quoted})").fetchall()
    foreign_keys = sorted(connection.execute(f"PRAGMA foreign_key_list({quoted})").fetchall())
    indices = []
    for row in connection.execute(f"PRAGMA index_list({quoted})").fetchall():
        name = '"' + row[1].replace('"', '""') + '"'
        # SQLite chooses autoindex names; compare their semantics and ordered columns.
        indices.append((row[2], row[3], row[4], connection.execute(f"PRAGMA index_xinfo({name})").fetchall()))
    return {"columns": columns, "foreignKeys": foreign_keys, "indices": sorted(indices)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    schemas = root / "app/schemas/com.forestry.counter.data.local.ForestryDatabase"
    before = json.loads((schemas / "46.json").read_text())
    after = json.loads((schemas / "49.json").read_text())
    assert (before["database"]["version"], after["database"]["version"]) == (46, 49)
    local = root / "app/src/main/java/com/forestry/counter/data/local"
    registry = (local / "DatabaseMigrations.kt").read_text()
    for migration in ("ControlBasisMigrations.MIGRATION_46_47",
                      "CartographicMeasurementMigrations.MIGRATION_47_48",
                      "CartographicWorkspaceMigrations.MIGRATION_48_49"):
        assert migration in registry, f"Unregistered migration: {migration}"
    wrapper = (local / "CartographicWorkspaceMigrations.kt").read_text()
    calls = ["CartographicLayerMigrations.migrate(db)",
             "com.forestry.counter.data.packs.offline.OfflineMapPackSchema.create(db)",
             "CartographicTraceMigrations.create(db)"]
    assert all(call in wrapper for call in calls)
    assert [wrapper.index(call) for call in calls] == sorted(wrapper.index(call) for call in calls)
    paths = [local / "ControlBasisMigrations.kt", local / "CartographicMeasurementMigrations.kt",
             local / "CartographicLayerMigrations.kt",
             local.parent / "packs/offline/OfflineMapPackSchema.kt",
             local / "CartographicTraceMigrations.kt"]
    migrated = sqlite3.connect(":memory:")
    expected = sqlite3.connect(":memory:")
    migrated.execute("PRAGMA foreign_keys=ON")
    expected.execute("PRAGMA foreign_keys=ON")
    load_schema(migrated, before)
    load_schema(expected, after)
    count = 0
    inputs = []
    pattern = r'db\.execSQL\(\s*(?:"""(.*?)"""|("(?:\\.|[^"\\])*"))\s*\)'
    for path in paths:
        source = path.read_text()
        matches = list(re.finditer(pattern, source, re.DOTALL))
        assert len(matches) == source.count("db.execSQL("), f"Non-literal SQL requires review: {path}"
        for match in matches:
            sql = match[1] if match[1] is not None else json.loads(match[2])
            assert sql.lstrip().startswith("CREATE "), "Additive migration contract changed"
            migrated.execute(sql)
            count += 1
        inputs.append({"path": str(path.relative_to(root)),
                       "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    tables = {row[0] for row in migrated.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")}
    # FTS creates shadow tables in both databases; compare those too.
    expected_tables = {row[0] for row in expected.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")}
    assert tables == expected_tables, {"extra": sorted(tables - expected_tables), "missing": sorted(expected_tables - tables)}
    for table in sorted(tables):
        assert table_signature(migrated, table) == table_signature(expected, table), f"Schema mismatch: {table}"
    assert migrated.execute("PRAGMA foreign_key_check").fetchall() == []
    assert migrated.execute("PRAGMA integrity_check").fetchall() == [("ok",)]
    result = {"status": "PASS", "scope": "Host SQLite schema compatibility only",
              "sqliteVersion": sqlite3.sqlite_version, "fromVersion": 46, "toVersion": 49,
              "literalStatements": count, "roomEntities": len(after["database"]["entities"]),
              "tablesComparedIncludingFts": len(tables), "migrationInputs": inputs,
              "room49IdentityHash": after["database"]["identityHash"],
              "notClaimed": ["Android SQLCipher execution", "User-record migration", "Device qualification"]}
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
