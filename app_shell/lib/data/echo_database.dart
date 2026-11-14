/// EchoFold 的 SQLite 持久化模式。
///
/// 设计约束（与 `openspec/changes/add-live-semantic-transcript/design.md` 对齐）：
/// - 所有时间是 UTC 毫秒；单调时钟只用于测量间隔，不入库；
/// - `segments.sourceFinalIds`、`segments.chunkIds`、`versions.sourceVersions`、
///   `artifacts.sourceVersions`、`artifacts.citations` 都是**有序 JSON 数组**，
///   数组顺序有语义，禁止在读写路径上排序；
/// - `versions.isDefault` 不单独落列：默认版本由 `segments.defaultVersionId` 派生，
///   避免同一事实双写后不一致；
/// - 会话删除级联清掉本地记录（final、段、版本、产物、任务、标题、音频块索引），
///   但 `settings` 不属于会话，模型安装由原生层管理，二者都不受级联影响。
library;

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:drift_flutter/drift_flutter.dart';

part 'echo_database.g.dart';

/// 录音会话摘要。
@DataClassName('SessionRow')
class Sessions extends Table {
  TextColumn get id => text()();
  TextColumn get title => text().nullable()();
  TextColumn get placeholderTitle => text()();
  IntColumn get createdAtUtc => integer()();
  TextColumn get state => text()();

  TextColumn get modelId => text().nullable()();
  TextColumn get modelVersion => text().nullable()();
  TextColumn get installationId => text().nullable()();
  TextColumn get asrConfigHash => text().nullable()();

  IntColumn get durationMs => integer().withDefault(const Constant(0))();
  IntColumn get gapCount => integer().withDefault(const Constant(0))();
  IntColumn get audioBytes => integer().withDefault(const Constant(0))();
  TextColumn get failureLabel => text().nullable()();

  @override
  Set<Column<Object>> get primaryKey => <Column<Object>>{id};
}

/// 音频块索引；`(sessionId, chunkSeq)` 唯一。
@DataClassName('AudioChunkRow')
class AudioChunks extends Table {
  @override
  String get tableName => 'audio_chunks';

  TextColumn get id => text()();
  TextColumn get sessionId =>
      text().references(Sessions, #id, onDelete: KeyAction.cascade)();
  IntColumn get chunkSeq => integer()();
  TextColumn get state => text()();
  IntColumn get createdAtUtc => integer()();
  TextColumn get tempPath => text()();
  TextColumn get finalPath => text().nullable()();
  IntColumn get sealedAtUtc => integer().nullable()();
  IntColumn get capturedStartMs => integer().nullable()();
  IntColumn get capturedEndMs => integer().nullable()();
  IntColumn get durationMs => integer().nullable()();
  TextColumn get codec => text().nullable()();
  IntColumn get sampleRate => integer().nullable()();
  IntColumn get bitRate => integer().nullable()();
  TextColumn get checksum => text().nullable()();
  TextColumn get failureStage => text().nullable()();

  @override
  Set<Column<Object>> get primaryKey => <Column<Object>>{id};

  @override
  List<Set<Column<Object>>> get uniqueKeys =>
      <Set<Column<Object>>>[
        <Column<Object>>{sessionId, chunkSeq},
      ];
}

/// 不可变 FINAL 证据。
@DataClassName('FinalSourceRow')
class FinalSources extends Table {
  @override
  String get tableName => 'final_sources';

  TextColumn get id => text()();
  TextColumn get sessionId =>
      text().references(Sessions, #id, onDelete: KeyAction.cascade)();
  /// 避开 Table.text 构建器；保留 SQLite 列名与 JSON 键。
  @JsonKey('text')
  TextColumn get text$ => text().named('text')();
  IntColumn get finalizedAtUtc => integer()();
  IntColumn get captureStartedMonoNs => integer()();
  IntColumn get captureEndedMonoNs => integer()();
  IntColumn get capturedStartMs => integer()();
  IntColumn get capturedEndMs => integer()();
  TextColumn get chunkIds => text()();
  TextColumn get modelId => text()();
  TextColumn get modelVersion => text()();

  @override
  Set<Column<Object>> get primaryKey => <Column<Object>>{id};
}

/// 不可变转写段；`(sessionId, ordinal)` 唯一。
@DataClassName('SegmentRow')
class Segments extends Table {
  @override
  String get tableName => 'segments';

  TextColumn get id => text()();
  TextColumn get sessionId =>
      text().references(Sessions, #id, onDelete: KeyAction.cascade)();
  IntColumn get ordinal => integer()();
  TextColumn get kind => text()();

  /// 有序 FINAL ID JSON 数组；GAP 段为空数组。
  TextColumn get sourceFinalIds => text()();
  IntColumn get capturedStartMs => integer()();
  IntColumn get capturedEndMs => integer()();

  /// 有序音频块 ID JSON 数组。
  TextColumn get chunkIds => text()();
  TextColumn get gapReason => text().nullable()();
  IntColumn get foldDueAtUtc => integer().nullable()();
  TextColumn get defaultVersionId => text().nullable()();

  @override
  Set<Column<Object>> get primaryKey => <Column<Object>>{id};

  @override
  List<Set<Column<Object>>> get uniqueKeys =>
      <Set<Column<Object>>>[
        <Column<Object>>{sessionId, ordinal},
      ];
}

/// 不可变转写版本；默认版本由 [Segments.defaultVersionId] 派生。
@DataClassName('VersionRow')
class Versions extends Table {
  @override
  String get tableName => 'versions';

  TextColumn get id => text()();
  TextColumn get segmentId =>
      text().references(Segments, #id, onDelete: KeyAction.cascade)();
  TextColumn get kind => text()();
  IntColumn get layer => integer()();
  /// 避开 Table.text 构建器；保留 SQLite 列名与 JSON 键。
  @JsonKey('text')
  TextColumn get text$ => text().named('text')();
  TextColumn get parentVersionId => text().nullable()();
  TextColumn get modelId => text().nullable()();
  TextColumn get modelVersion => text().nullable()();

  IntColumn get createdAtUtc => integer()();
  IntColumn get finalizedAtUtc => integer()();
  IntColumn get captureStartMs => integer()();
  IntColumn get captureEndMs => integer()();

  /// 有序 `SourceVersionRef[]` JSON。
  TextColumn get sourceVersions => text().withDefault(const Constant('[]'))();
  TextColumn get sourceSetHash => text().nullable()();

  @override
  Set<Column<Object>> get primaryKey => <Column<Object>>{id};
}

/// 语义产物（L1/L2/L3 或标题）。
@DataClassName('ArtifactRow')
class Artifacts extends Table {
  @override
  String get tableName => 'artifacts';

  TextColumn get id => text()();
  TextColumn get sessionId =>
      text().references(Sessions, #id, onDelete: KeyAction.cascade)();
  TextColumn get segmentId =>
      text().references(Segments, #id, onDelete: KeyAction.cascade).nullable()();
  IntColumn get layer => integer()();
  TextColumn get state => text()();
  /// 避开 Table.text 构建器；保留 SQLite 列名与 JSON 键。
  @JsonKey('text')
  TextColumn get text$ => text().named('text')();

  /// 有序 `SourceVersionRef[]` JSON。
  TextColumn get sourceVersions => text()();
  TextColumn get sourceSetHash => text()();
  TextColumn get promptVersion => text()();
  TextColumn get model => text()();
  IntColumn get createdAtUtc => integer()();
  TextColumn get parentVersionId => text().nullable()();
  TextColumn get citations => text().withDefault(const Constant('[]'))();
  TextColumn get errorCategory => text().nullable()();

  @override
  Set<Column<Object>> get primaryKey => <Column<Object>>{id};
}

/// 处理任务；`idempotencyKey` 唯一。
@DataClassName('JobRow')
class Jobs extends Table {
  @override
  String get tableName => 'jobs';

  TextColumn get id => text()();
  TextColumn get idempotencyKey => text().unique()();
  TextColumn get sessionId =>
      text().references(Sessions, #id, onDelete: KeyAction.cascade)();
  TextColumn get targetId => text()();
  IntColumn get layer => integer()();
  TextColumn get state => text()();

  /// 有序 `SourceVersionRef[]` JSON。
  TextColumn get sourceVersions => text()();
  TextColumn get sourceSetHash => text()();
  TextColumn get promptVersion => text()();
  TextColumn get model => text()();
  IntColumn get createdAtUtc => integer()();
  IntColumn get updatedAtUtc => integer()();
  IntColumn get attempts => integer().withDefault(const Constant(0))();
  TextColumn get errorCategory => text().nullable()();

  @override
  Set<Column<Object>> get primaryKey => <Column<Object>>{id};
}

/// 每个会话一条标题展示状态。
@DataClassName('TitleRow')
class Titles extends Table {
  @override
  String get tableName => 'titles';

  TextColumn get sessionId =>
      text().references(Sessions, #id, onDelete: KeyAction.cascade)();
  TextColumn get placeholder => text()();
  BoolColumn get pendingFirstReveal =>
      boolean().withDefault(const Constant(false))();
  TextColumn get latestTitle => text().nullable()();
  TextColumn get latestVersionId => text().nullable()();
  IntColumn get revealedAtUtc => integer().nullable()();

  /// 有序 `SourceVersionRef[]` JSON；尚未生成标题时为 NULL。
  TextColumn get sourceVersions => text().nullable()();
  TextColumn get sourceSetHash => text().nullable()();
  IntColumn get generatedAtUtc => integer().nullable()();

  @override
  Set<Column<Object>> get primaryKey => <Column<Object>>{sessionId};
}

/// 与渠道无关的键值设置；不随会话删除。
@DataClassName('SettingRow')
class Settings extends Table {
  @override
  String get tableName => 'settings';

  TextColumn get settingKey => text().named('key')();
  TextColumn get settingValue => text().named('value')();

  @override
  Set<Column<Object>> get primaryKey => <Column<Object>>{settingKey};
}

/// 生产数据库当前模式版本；任何列语义变化都必须递增并补迁移。
const int echoDatabaseSchemaVersion = 1;

/// drift 数据库入口。
@DriftDatabase(
  tables: <Type>[
    Sessions,
    AudioChunks,
    FinalSources,
    Segments,
    Versions,
    Artifacts,
    Jobs,
    Titles,
    Settings,
  ],
)
class EchoDatabase extends _$EchoDatabase {
  EchoDatabase(super.executor);

  /// 生产入口：由 drift_flutter 决定应用目录与 isolate。
  static EchoDatabase open({String name = 'echofold'}) =>
      EchoDatabase(driftDatabase(name: name));

  /// 测试用内存库；不会写盘，也不会跨进程共享。
  static EchoDatabase inMemory() => EchoDatabase(NativeDatabase.memory());

  @override
  int get schemaVersion => echoDatabaseSchemaVersion;

  @override
  MigrationStrategy get migration => MigrationStrategy(
        onCreate: (Migrator m) async {
          await m.createAll();
        },
        onUpgrade: (Migrator m, int from, int to) async {
          // 当前只有 v1。遇到未知版本必须显式失败，不能用“删库重建”掩盖数据损坏。
          throw StateError(
            'EchoFold 数据库版本 $from 无法迁移到当前 schemaVersion $to；'
            '请使用匹配版本的应用打开，或显式导出后重建。',
          );
        },
        beforeOpen: (OpeningDetails details) async {
          // 级联删除依赖外键开关；PRAGMA 必须在事务外执行。
          await customStatement('PRAGMA foreign_keys = ON');
        },
      );
}
