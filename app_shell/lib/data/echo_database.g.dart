// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'echo_database.dart';

// ignore_for_file: type=lint
abstract class _$EchoDatabase extends GeneratedDatabase {
  _$EchoDatabase(QueryExecutor e) : super(e);
  $EchoDatabaseManager get managers => $EchoDatabaseManager(this);
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [];
}

class $EchoDatabaseManager {
  final _$EchoDatabase _db;
  $EchoDatabaseManager(this._db);
}
