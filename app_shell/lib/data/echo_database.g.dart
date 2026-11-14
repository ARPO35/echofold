// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'echo_database.dart';

// ignore_for_file: type=lint
class $SessionsTable extends Sessions
    with TableInfo<$SessionsTable, SessionRow> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $SessionsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
    'title',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _placeholderTitleMeta = const VerificationMeta(
    'placeholderTitle',
  );
  @override
  late final GeneratedColumn<String> placeholderTitle = GeneratedColumn<String>(
    'placeholder_title',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _createdAtUtcMeta = const VerificationMeta(
    'createdAtUtc',
  );
  @override
  late final GeneratedColumn<int> createdAtUtc = GeneratedColumn<int>(
    'created_at_utc',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _stateMeta = const VerificationMeta('state');
  @override
  late final GeneratedColumn<String> state = GeneratedColumn<String>(
    'state',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _modelIdMeta = const VerificationMeta(
    'modelId',
  );
  @override
  late final GeneratedColumn<String> modelId = GeneratedColumn<String>(
    'model_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _modelVersionMeta = const VerificationMeta(
    'modelVersion',
  );
  @override
  late final GeneratedColumn<String> modelVersion = GeneratedColumn<String>(
    'model_version',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _installationIdMeta = const VerificationMeta(
    'installationId',
  );
  @override
  late final GeneratedColumn<String> installationId = GeneratedColumn<String>(
    'installation_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _asrConfigHashMeta = const VerificationMeta(
    'asrConfigHash',
  );
  @override
  late final GeneratedColumn<String> asrConfigHash = GeneratedColumn<String>(
    'asr_config_hash',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _durationMsMeta = const VerificationMeta(
    'durationMs',
  );
  @override
  late final GeneratedColumn<int> durationMs = GeneratedColumn<int>(
    'duration_ms',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _gapCountMeta = const VerificationMeta(
    'gapCount',
  );
  @override
  late final GeneratedColumn<int> gapCount = GeneratedColumn<int>(
    'gap_count',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _audioBytesMeta = const VerificationMeta(
    'audioBytes',
  );
  @override
  late final GeneratedColumn<int> audioBytes = GeneratedColumn<int>(
    'audio_bytes',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _failureLabelMeta = const VerificationMeta(
    'failureLabel',
  );
  @override
  late final GeneratedColumn<String> failureLabel = GeneratedColumn<String>(
    'failure_label',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    title,
    placeholderTitle,
    createdAtUtc,
    state,
    modelId,
    modelVersion,
    installationId,
    asrConfigHash,
    durationMs,
    gapCount,
    audioBytes,
    failureLabel,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'sessions';
  @override
  VerificationContext validateIntegrity(
    Insertable<SessionRow> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('title')) {
      context.handle(
        _titleMeta,
        title.isAcceptableOrUnknown(data['title']!, _titleMeta),
      );
    }
    if (data.containsKey('placeholder_title')) {
      context.handle(
        _placeholderTitleMeta,
        placeholderTitle.isAcceptableOrUnknown(
          data['placeholder_title']!,
          _placeholderTitleMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_placeholderTitleMeta);
    }
    if (data.containsKey('created_at_utc')) {
      context.handle(
        _createdAtUtcMeta,
        createdAtUtc.isAcceptableOrUnknown(
          data['created_at_utc']!,
          _createdAtUtcMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_createdAtUtcMeta);
    }
    if (data.containsKey('state')) {
      context.handle(
        _stateMeta,
        state.isAcceptableOrUnknown(data['state']!, _stateMeta),
      );
    } else if (isInserting) {
      context.missing(_stateMeta);
    }
    if (data.containsKey('model_id')) {
      context.handle(
        _modelIdMeta,
        modelId.isAcceptableOrUnknown(data['model_id']!, _modelIdMeta),
      );
    }
    if (data.containsKey('model_version')) {
      context.handle(
        _modelVersionMeta,
        modelVersion.isAcceptableOrUnknown(
          data['model_version']!,
          _modelVersionMeta,
        ),
      );
    }
    if (data.containsKey('installation_id')) {
      context.handle(
        _installationIdMeta,
        installationId.isAcceptableOrUnknown(
          data['installation_id']!,
          _installationIdMeta,
        ),
      );
    }
    if (data.containsKey('asr_config_hash')) {
      context.handle(
        _asrConfigHashMeta,
        asrConfigHash.isAcceptableOrUnknown(
          data['asr_config_hash']!,
          _asrConfigHashMeta,
        ),
      );
    }
    if (data.containsKey('duration_ms')) {
      context.handle(
        _durationMsMeta,
        durationMs.isAcceptableOrUnknown(data['duration_ms']!, _durationMsMeta),
      );
    }
    if (data.containsKey('gap_count')) {
      context.handle(
        _gapCountMeta,
        gapCount.isAcceptableOrUnknown(data['gap_count']!, _gapCountMeta),
      );
    }
    if (data.containsKey('audio_bytes')) {
      context.handle(
        _audioBytesMeta,
        audioBytes.isAcceptableOrUnknown(data['audio_bytes']!, _audioBytesMeta),
      );
    }
    if (data.containsKey('failure_label')) {
      context.handle(
        _failureLabelMeta,
        failureLabel.isAcceptableOrUnknown(
          data['failure_label']!,
          _failureLabelMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  SessionRow map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return SessionRow(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      title: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title'],
      ),
      placeholderTitle: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}placeholder_title'],
      )!,
      createdAtUtc: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}created_at_utc'],
      )!,
      state: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}state'],
      )!,
      modelId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}model_id'],
      ),
      modelVersion: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}model_version'],
      ),
      installationId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}installation_id'],
      ),
      asrConfigHash: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}asr_config_hash'],
      ),
      durationMs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}duration_ms'],
      )!,
      gapCount: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}gap_count'],
      )!,
      audioBytes: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}audio_bytes'],
      )!,
      failureLabel: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}failure_label'],
      ),
    );
  }

  @override
  $SessionsTable createAlias(String alias) {
    return $SessionsTable(attachedDatabase, alias);
  }
}

class SessionRow extends DataClass implements Insertable<SessionRow> {
  final String id;
  final String? title;
  final String placeholderTitle;
  final int createdAtUtc;
  final String state;
  final String? modelId;
  final String? modelVersion;
  final String? installationId;
  final String? asrConfigHash;
  final int durationMs;
  final int gapCount;
  final int audioBytes;
  final String? failureLabel;
  const SessionRow({
    required this.id,
    this.title,
    required this.placeholderTitle,
    required this.createdAtUtc,
    required this.state,
    this.modelId,
    this.modelVersion,
    this.installationId,
    this.asrConfigHash,
    required this.durationMs,
    required this.gapCount,
    required this.audioBytes,
    this.failureLabel,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    if (!nullToAbsent || title != null) {
      map['title'] = Variable<String>(title);
    }
    map['placeholder_title'] = Variable<String>(placeholderTitle);
    map['created_at_utc'] = Variable<int>(createdAtUtc);
    map['state'] = Variable<String>(state);
    if (!nullToAbsent || modelId != null) {
      map['model_id'] = Variable<String>(modelId);
    }
    if (!nullToAbsent || modelVersion != null) {
      map['model_version'] = Variable<String>(modelVersion);
    }
    if (!nullToAbsent || installationId != null) {
      map['installation_id'] = Variable<String>(installationId);
    }
    if (!nullToAbsent || asrConfigHash != null) {
      map['asr_config_hash'] = Variable<String>(asrConfigHash);
    }
    map['duration_ms'] = Variable<int>(durationMs);
    map['gap_count'] = Variable<int>(gapCount);
    map['audio_bytes'] = Variable<int>(audioBytes);
    if (!nullToAbsent || failureLabel != null) {
      map['failure_label'] = Variable<String>(failureLabel);
    }
    return map;
  }

  SessionsCompanion toCompanion(bool nullToAbsent) {
    return SessionsCompanion(
      id: Value(id),
      title: title == null && nullToAbsent
          ? const Value.absent()
          : Value(title),
      placeholderTitle: Value(placeholderTitle),
      createdAtUtc: Value(createdAtUtc),
      state: Value(state),
      modelId: modelId == null && nullToAbsent
          ? const Value.absent()
          : Value(modelId),
      modelVersion: modelVersion == null && nullToAbsent
          ? const Value.absent()
          : Value(modelVersion),
      installationId: installationId == null && nullToAbsent
          ? const Value.absent()
          : Value(installationId),
      asrConfigHash: asrConfigHash == null && nullToAbsent
          ? const Value.absent()
          : Value(asrConfigHash),
      durationMs: Value(durationMs),
      gapCount: Value(gapCount),
      audioBytes: Value(audioBytes),
      failureLabel: failureLabel == null && nullToAbsent
          ? const Value.absent()
          : Value(failureLabel),
    );
  }

  factory SessionRow.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return SessionRow(
      id: serializer.fromJson<String>(json['id']),
      title: serializer.fromJson<String?>(json['title']),
      placeholderTitle: serializer.fromJson<String>(json['placeholderTitle']),
      createdAtUtc: serializer.fromJson<int>(json['createdAtUtc']),
      state: serializer.fromJson<String>(json['state']),
      modelId: serializer.fromJson<String?>(json['modelId']),
      modelVersion: serializer.fromJson<String?>(json['modelVersion']),
      installationId: serializer.fromJson<String?>(json['installationId']),
      asrConfigHash: serializer.fromJson<String?>(json['asrConfigHash']),
      durationMs: serializer.fromJson<int>(json['durationMs']),
      gapCount: serializer.fromJson<int>(json['gapCount']),
      audioBytes: serializer.fromJson<int>(json['audioBytes']),
      failureLabel: serializer.fromJson<String?>(json['failureLabel']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'title': serializer.toJson<String?>(title),
      'placeholderTitle': serializer.toJson<String>(placeholderTitle),
      'createdAtUtc': serializer.toJson<int>(createdAtUtc),
      'state': serializer.toJson<String>(state),
      'modelId': serializer.toJson<String?>(modelId),
      'modelVersion': serializer.toJson<String?>(modelVersion),
      'installationId': serializer.toJson<String?>(installationId),
      'asrConfigHash': serializer.toJson<String?>(asrConfigHash),
      'durationMs': serializer.toJson<int>(durationMs),
      'gapCount': serializer.toJson<int>(gapCount),
      'audioBytes': serializer.toJson<int>(audioBytes),
      'failureLabel': serializer.toJson<String?>(failureLabel),
    };
  }

  SessionRow copyWith({
    String? id,
    Value<String?> title = const Value.absent(),
    String? placeholderTitle,
    int? createdAtUtc,
    String? state,
    Value<String?> modelId = const Value.absent(),
    Value<String?> modelVersion = const Value.absent(),
    Value<String?> installationId = const Value.absent(),
    Value<String?> asrConfigHash = const Value.absent(),
    int? durationMs,
    int? gapCount,
    int? audioBytes,
    Value<String?> failureLabel = const Value.absent(),
  }) => SessionRow(
    id: id ?? this.id,
    title: title.present ? title.value : this.title,
    placeholderTitle: placeholderTitle ?? this.placeholderTitle,
    createdAtUtc: createdAtUtc ?? this.createdAtUtc,
    state: state ?? this.state,
    modelId: modelId.present ? modelId.value : this.modelId,
    modelVersion: modelVersion.present ? modelVersion.value : this.modelVersion,
    installationId: installationId.present
        ? installationId.value
        : this.installationId,
    asrConfigHash: asrConfigHash.present
        ? asrConfigHash.value
        : this.asrConfigHash,
    durationMs: durationMs ?? this.durationMs,
    gapCount: gapCount ?? this.gapCount,
    audioBytes: audioBytes ?? this.audioBytes,
    failureLabel: failureLabel.present ? failureLabel.value : this.failureLabel,
  );
  SessionRow copyWithCompanion(SessionsCompanion data) {
    return SessionRow(
      id: data.id.present ? data.id.value : this.id,
      title: data.title.present ? data.title.value : this.title,
      placeholderTitle: data.placeholderTitle.present
          ? data.placeholderTitle.value
          : this.placeholderTitle,
      createdAtUtc: data.createdAtUtc.present
          ? data.createdAtUtc.value
          : this.createdAtUtc,
      state: data.state.present ? data.state.value : this.state,
      modelId: data.modelId.present ? data.modelId.value : this.modelId,
      modelVersion: data.modelVersion.present
          ? data.modelVersion.value
          : this.modelVersion,
      installationId: data.installationId.present
          ? data.installationId.value
          : this.installationId,
      asrConfigHash: data.asrConfigHash.present
          ? data.asrConfigHash.value
          : this.asrConfigHash,
      durationMs: data.durationMs.present
          ? data.durationMs.value
          : this.durationMs,
      gapCount: data.gapCount.present ? data.gapCount.value : this.gapCount,
      audioBytes: data.audioBytes.present
          ? data.audioBytes.value
          : this.audioBytes,
      failureLabel: data.failureLabel.present
          ? data.failureLabel.value
          : this.failureLabel,
    );
  }

  @override
  String toString() {
    return (StringBuffer('SessionRow(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('placeholderTitle: $placeholderTitle, ')
          ..write('createdAtUtc: $createdAtUtc, ')
          ..write('state: $state, ')
          ..write('modelId: $modelId, ')
          ..write('modelVersion: $modelVersion, ')
          ..write('installationId: $installationId, ')
          ..write('asrConfigHash: $asrConfigHash, ')
          ..write('durationMs: $durationMs, ')
          ..write('gapCount: $gapCount, ')
          ..write('audioBytes: $audioBytes, ')
          ..write('failureLabel: $failureLabel')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    title,
    placeholderTitle,
    createdAtUtc,
    state,
    modelId,
    modelVersion,
    installationId,
    asrConfigHash,
    durationMs,
    gapCount,
    audioBytes,
    failureLabel,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is SessionRow &&
          other.id == this.id &&
          other.title == this.title &&
          other.placeholderTitle == this.placeholderTitle &&
          other.createdAtUtc == this.createdAtUtc &&
          other.state == this.state &&
          other.modelId == this.modelId &&
          other.modelVersion == this.modelVersion &&
          other.installationId == this.installationId &&
          other.asrConfigHash == this.asrConfigHash &&
          other.durationMs == this.durationMs &&
          other.gapCount == this.gapCount &&
          other.audioBytes == this.audioBytes &&
          other.failureLabel == this.failureLabel);
}

class SessionsCompanion extends UpdateCompanion<SessionRow> {
  final Value<String> id;
  final Value<String?> title;
  final Value<String> placeholderTitle;
  final Value<int> createdAtUtc;
  final Value<String> state;
  final Value<String?> modelId;
  final Value<String?> modelVersion;
  final Value<String?> installationId;
  final Value<String?> asrConfigHash;
  final Value<int> durationMs;
  final Value<int> gapCount;
  final Value<int> audioBytes;
  final Value<String?> failureLabel;
  final Value<int> rowid;
  const SessionsCompanion({
    this.id = const Value.absent(),
    this.title = const Value.absent(),
    this.placeholderTitle = const Value.absent(),
    this.createdAtUtc = const Value.absent(),
    this.state = const Value.absent(),
    this.modelId = const Value.absent(),
    this.modelVersion = const Value.absent(),
    this.installationId = const Value.absent(),
    this.asrConfigHash = const Value.absent(),
    this.durationMs = const Value.absent(),
    this.gapCount = const Value.absent(),
    this.audioBytes = const Value.absent(),
    this.failureLabel = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  SessionsCompanion.insert({
    required String id,
    this.title = const Value.absent(),
    required String placeholderTitle,
    required int createdAtUtc,
    required String state,
    this.modelId = const Value.absent(),
    this.modelVersion = const Value.absent(),
    this.installationId = const Value.absent(),
    this.asrConfigHash = const Value.absent(),
    this.durationMs = const Value.absent(),
    this.gapCount = const Value.absent(),
    this.audioBytes = const Value.absent(),
    this.failureLabel = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       placeholderTitle = Value(placeholderTitle),
       createdAtUtc = Value(createdAtUtc),
       state = Value(state);
  static Insertable<SessionRow> custom({
    Expression<String>? id,
    Expression<String>? title,
    Expression<String>? placeholderTitle,
    Expression<int>? createdAtUtc,
    Expression<String>? state,
    Expression<String>? modelId,
    Expression<String>? modelVersion,
    Expression<String>? installationId,
    Expression<String>? asrConfigHash,
    Expression<int>? durationMs,
    Expression<int>? gapCount,
    Expression<int>? audioBytes,
    Expression<String>? failureLabel,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (title != null) 'title': title,
      if (placeholderTitle != null) 'placeholder_title': placeholderTitle,
      if (createdAtUtc != null) 'created_at_utc': createdAtUtc,
      if (state != null) 'state': state,
      if (modelId != null) 'model_id': modelId,
      if (modelVersion != null) 'model_version': modelVersion,
      if (installationId != null) 'installation_id': installationId,
      if (asrConfigHash != null) 'asr_config_hash': asrConfigHash,
      if (durationMs != null) 'duration_ms': durationMs,
      if (gapCount != null) 'gap_count': gapCount,
      if (audioBytes != null) 'audio_bytes': audioBytes,
      if (failureLabel != null) 'failure_label': failureLabel,
      if (rowid != null) 'rowid': rowid,
    });
  }

  SessionsCompanion copyWith({
    Value<String>? id,
    Value<String?>? title,
    Value<String>? placeholderTitle,
    Value<int>? createdAtUtc,
    Value<String>? state,
    Value<String?>? modelId,
    Value<String?>? modelVersion,
    Value<String?>? installationId,
    Value<String?>? asrConfigHash,
    Value<int>? durationMs,
    Value<int>? gapCount,
    Value<int>? audioBytes,
    Value<String?>? failureLabel,
    Value<int>? rowid,
  }) {
    return SessionsCompanion(
      id: id ?? this.id,
      title: title ?? this.title,
      placeholderTitle: placeholderTitle ?? this.placeholderTitle,
      createdAtUtc: createdAtUtc ?? this.createdAtUtc,
      state: state ?? this.state,
      modelId: modelId ?? this.modelId,
      modelVersion: modelVersion ?? this.modelVersion,
      installationId: installationId ?? this.installationId,
      asrConfigHash: asrConfigHash ?? this.asrConfigHash,
      durationMs: durationMs ?? this.durationMs,
      gapCount: gapCount ?? this.gapCount,
      audioBytes: audioBytes ?? this.audioBytes,
      failureLabel: failureLabel ?? this.failureLabel,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (placeholderTitle.present) {
      map['placeholder_title'] = Variable<String>(placeholderTitle.value);
    }
    if (createdAtUtc.present) {
      map['created_at_utc'] = Variable<int>(createdAtUtc.value);
    }
    if (state.present) {
      map['state'] = Variable<String>(state.value);
    }
    if (modelId.present) {
      map['model_id'] = Variable<String>(modelId.value);
    }
    if (modelVersion.present) {
      map['model_version'] = Variable<String>(modelVersion.value);
    }
    if (installationId.present) {
      map['installation_id'] = Variable<String>(installationId.value);
    }
    if (asrConfigHash.present) {
      map['asr_config_hash'] = Variable<String>(asrConfigHash.value);
    }
    if (durationMs.present) {
      map['duration_ms'] = Variable<int>(durationMs.value);
    }
    if (gapCount.present) {
      map['gap_count'] = Variable<int>(gapCount.value);
    }
    if (audioBytes.present) {
      map['audio_bytes'] = Variable<int>(audioBytes.value);
    }
    if (failureLabel.present) {
      map['failure_label'] = Variable<String>(failureLabel.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('SessionsCompanion(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('placeholderTitle: $placeholderTitle, ')
          ..write('createdAtUtc: $createdAtUtc, ')
          ..write('state: $state, ')
          ..write('modelId: $modelId, ')
          ..write('modelVersion: $modelVersion, ')
          ..write('installationId: $installationId, ')
          ..write('asrConfigHash: $asrConfigHash, ')
          ..write('durationMs: $durationMs, ')
          ..write('gapCount: $gapCount, ')
          ..write('audioBytes: $audioBytes, ')
          ..write('failureLabel: $failureLabel, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $AudioChunksTable extends AudioChunks
    with TableInfo<$AudioChunksTable, AudioChunkRow> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $AudioChunksTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _sessionIdMeta = const VerificationMeta(
    'sessionId',
  );
  @override
  late final GeneratedColumn<String> sessionId = GeneratedColumn<String>(
    'session_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES sessions (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _chunkSeqMeta = const VerificationMeta(
    'chunkSeq',
  );
  @override
  late final GeneratedColumn<int> chunkSeq = GeneratedColumn<int>(
    'chunk_seq',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _stateMeta = const VerificationMeta('state');
  @override
  late final GeneratedColumn<String> state = GeneratedColumn<String>(
    'state',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _createdAtUtcMeta = const VerificationMeta(
    'createdAtUtc',
  );
  @override
  late final GeneratedColumn<int> createdAtUtc = GeneratedColumn<int>(
    'created_at_utc',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _tempPathMeta = const VerificationMeta(
    'tempPath',
  );
  @override
  late final GeneratedColumn<String> tempPath = GeneratedColumn<String>(
    'temp_path',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _finalPathMeta = const VerificationMeta(
    'finalPath',
  );
  @override
  late final GeneratedColumn<String> finalPath = GeneratedColumn<String>(
    'final_path',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _sealedAtUtcMeta = const VerificationMeta(
    'sealedAtUtc',
  );
  @override
  late final GeneratedColumn<int> sealedAtUtc = GeneratedColumn<int>(
    'sealed_at_utc',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _capturedStartMsMeta = const VerificationMeta(
    'capturedStartMs',
  );
  @override
  late final GeneratedColumn<int> capturedStartMs = GeneratedColumn<int>(
    'captured_start_ms',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _capturedEndMsMeta = const VerificationMeta(
    'capturedEndMs',
  );
  @override
  late final GeneratedColumn<int> capturedEndMs = GeneratedColumn<int>(
    'captured_end_ms',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _durationMsMeta = const VerificationMeta(
    'durationMs',
  );
  @override
  late final GeneratedColumn<int> durationMs = GeneratedColumn<int>(
    'duration_ms',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _codecMeta = const VerificationMeta('codec');
  @override
  late final GeneratedColumn<String> codec = GeneratedColumn<String>(
    'codec',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _sampleRateMeta = const VerificationMeta(
    'sampleRate',
  );
  @override
  late final GeneratedColumn<int> sampleRate = GeneratedColumn<int>(
    'sample_rate',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _bitRateMeta = const VerificationMeta(
    'bitRate',
  );
  @override
  late final GeneratedColumn<int> bitRate = GeneratedColumn<int>(
    'bit_rate',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _checksumMeta = const VerificationMeta(
    'checksum',
  );
  @override
  late final GeneratedColumn<String> checksum = GeneratedColumn<String>(
    'checksum',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _failureStageMeta = const VerificationMeta(
    'failureStage',
  );
  @override
  late final GeneratedColumn<String> failureStage = GeneratedColumn<String>(
    'failure_stage',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    sessionId,
    chunkSeq,
    state,
    createdAtUtc,
    tempPath,
    finalPath,
    sealedAtUtc,
    capturedStartMs,
    capturedEndMs,
    durationMs,
    codec,
    sampleRate,
    bitRate,
    checksum,
    failureStage,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'audio_chunks';
  @override
  VerificationContext validateIntegrity(
    Insertable<AudioChunkRow> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('session_id')) {
      context.handle(
        _sessionIdMeta,
        sessionId.isAcceptableOrUnknown(data['session_id']!, _sessionIdMeta),
      );
    } else if (isInserting) {
      context.missing(_sessionIdMeta);
    }
    if (data.containsKey('chunk_seq')) {
      context.handle(
        _chunkSeqMeta,
        chunkSeq.isAcceptableOrUnknown(data['chunk_seq']!, _chunkSeqMeta),
      );
    } else if (isInserting) {
      context.missing(_chunkSeqMeta);
    }
    if (data.containsKey('state')) {
      context.handle(
        _stateMeta,
        state.isAcceptableOrUnknown(data['state']!, _stateMeta),
      );
    } else if (isInserting) {
      context.missing(_stateMeta);
    }
    if (data.containsKey('created_at_utc')) {
      context.handle(
        _createdAtUtcMeta,
        createdAtUtc.isAcceptableOrUnknown(
          data['created_at_utc']!,
          _createdAtUtcMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_createdAtUtcMeta);
    }
    if (data.containsKey('temp_path')) {
      context.handle(
        _tempPathMeta,
        tempPath.isAcceptableOrUnknown(data['temp_path']!, _tempPathMeta),
      );
    } else if (isInserting) {
      context.missing(_tempPathMeta);
    }
    if (data.containsKey('final_path')) {
      context.handle(
        _finalPathMeta,
        finalPath.isAcceptableOrUnknown(data['final_path']!, _finalPathMeta),
      );
    }
    if (data.containsKey('sealed_at_utc')) {
      context.handle(
        _sealedAtUtcMeta,
        sealedAtUtc.isAcceptableOrUnknown(
          data['sealed_at_utc']!,
          _sealedAtUtcMeta,
        ),
      );
    }
    if (data.containsKey('captured_start_ms')) {
      context.handle(
        _capturedStartMsMeta,
        capturedStartMs.isAcceptableOrUnknown(
          data['captured_start_ms']!,
          _capturedStartMsMeta,
        ),
      );
    }
    if (data.containsKey('captured_end_ms')) {
      context.handle(
        _capturedEndMsMeta,
        capturedEndMs.isAcceptableOrUnknown(
          data['captured_end_ms']!,
          _capturedEndMsMeta,
        ),
      );
    }
    if (data.containsKey('duration_ms')) {
      context.handle(
        _durationMsMeta,
        durationMs.isAcceptableOrUnknown(data['duration_ms']!, _durationMsMeta),
      );
    }
    if (data.containsKey('codec')) {
      context.handle(
        _codecMeta,
        codec.isAcceptableOrUnknown(data['codec']!, _codecMeta),
      );
    }
    if (data.containsKey('sample_rate')) {
      context.handle(
        _sampleRateMeta,
        sampleRate.isAcceptableOrUnknown(data['sample_rate']!, _sampleRateMeta),
      );
    }
    if (data.containsKey('bit_rate')) {
      context.handle(
        _bitRateMeta,
        bitRate.isAcceptableOrUnknown(data['bit_rate']!, _bitRateMeta),
      );
    }
    if (data.containsKey('checksum')) {
      context.handle(
        _checksumMeta,
        checksum.isAcceptableOrUnknown(data['checksum']!, _checksumMeta),
      );
    }
    if (data.containsKey('failure_stage')) {
      context.handle(
        _failureStageMeta,
        failureStage.isAcceptableOrUnknown(
          data['failure_stage']!,
          _failureStageMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  List<Set<GeneratedColumn>> get uniqueKeys => [
    {sessionId, chunkSeq},
  ];
  @override
  AudioChunkRow map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return AudioChunkRow(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      sessionId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}session_id'],
      )!,
      chunkSeq: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}chunk_seq'],
      )!,
      state: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}state'],
      )!,
      createdAtUtc: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}created_at_utc'],
      )!,
      tempPath: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}temp_path'],
      )!,
      finalPath: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}final_path'],
      ),
      sealedAtUtc: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}sealed_at_utc'],
      ),
      capturedStartMs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}captured_start_ms'],
      ),
      capturedEndMs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}captured_end_ms'],
      ),
      durationMs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}duration_ms'],
      ),
      codec: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}codec'],
      ),
      sampleRate: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}sample_rate'],
      ),
      bitRate: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}bit_rate'],
      ),
      checksum: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}checksum'],
      ),
      failureStage: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}failure_stage'],
      ),
    );
  }

  @override
  $AudioChunksTable createAlias(String alias) {
    return $AudioChunksTable(attachedDatabase, alias);
  }
}

class AudioChunkRow extends DataClass implements Insertable<AudioChunkRow> {
  final String id;
  final String sessionId;
  final int chunkSeq;
  final String state;
  final int createdAtUtc;
  final String tempPath;
  final String? finalPath;
  final int? sealedAtUtc;
  final int? capturedStartMs;
  final int? capturedEndMs;
  final int? durationMs;
  final String? codec;
  final int? sampleRate;
  final int? bitRate;
  final String? checksum;
  final String? failureStage;
  const AudioChunkRow({
    required this.id,
    required this.sessionId,
    required this.chunkSeq,
    required this.state,
    required this.createdAtUtc,
    required this.tempPath,
    this.finalPath,
    this.sealedAtUtc,
    this.capturedStartMs,
    this.capturedEndMs,
    this.durationMs,
    this.codec,
    this.sampleRate,
    this.bitRate,
    this.checksum,
    this.failureStage,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['session_id'] = Variable<String>(sessionId);
    map['chunk_seq'] = Variable<int>(chunkSeq);
    map['state'] = Variable<String>(state);
    map['created_at_utc'] = Variable<int>(createdAtUtc);
    map['temp_path'] = Variable<String>(tempPath);
    if (!nullToAbsent || finalPath != null) {
      map['final_path'] = Variable<String>(finalPath);
    }
    if (!nullToAbsent || sealedAtUtc != null) {
      map['sealed_at_utc'] = Variable<int>(sealedAtUtc);
    }
    if (!nullToAbsent || capturedStartMs != null) {
      map['captured_start_ms'] = Variable<int>(capturedStartMs);
    }
    if (!nullToAbsent || capturedEndMs != null) {
      map['captured_end_ms'] = Variable<int>(capturedEndMs);
    }
    if (!nullToAbsent || durationMs != null) {
      map['duration_ms'] = Variable<int>(durationMs);
    }
    if (!nullToAbsent || codec != null) {
      map['codec'] = Variable<String>(codec);
    }
    if (!nullToAbsent || sampleRate != null) {
      map['sample_rate'] = Variable<int>(sampleRate);
    }
    if (!nullToAbsent || bitRate != null) {
      map['bit_rate'] = Variable<int>(bitRate);
    }
    if (!nullToAbsent || checksum != null) {
      map['checksum'] = Variable<String>(checksum);
    }
    if (!nullToAbsent || failureStage != null) {
      map['failure_stage'] = Variable<String>(failureStage);
    }
    return map;
  }

  AudioChunksCompanion toCompanion(bool nullToAbsent) {
    return AudioChunksCompanion(
      id: Value(id),
      sessionId: Value(sessionId),
      chunkSeq: Value(chunkSeq),
      state: Value(state),
      createdAtUtc: Value(createdAtUtc),
      tempPath: Value(tempPath),
      finalPath: finalPath == null && nullToAbsent
          ? const Value.absent()
          : Value(finalPath),
      sealedAtUtc: sealedAtUtc == null && nullToAbsent
          ? const Value.absent()
          : Value(sealedAtUtc),
      capturedStartMs: capturedStartMs == null && nullToAbsent
          ? const Value.absent()
          : Value(capturedStartMs),
      capturedEndMs: capturedEndMs == null && nullToAbsent
          ? const Value.absent()
          : Value(capturedEndMs),
      durationMs: durationMs == null && nullToAbsent
          ? const Value.absent()
          : Value(durationMs),
      codec: codec == null && nullToAbsent
          ? const Value.absent()
          : Value(codec),
      sampleRate: sampleRate == null && nullToAbsent
          ? const Value.absent()
          : Value(sampleRate),
      bitRate: bitRate == null && nullToAbsent
          ? const Value.absent()
          : Value(bitRate),
      checksum: checksum == null && nullToAbsent
          ? const Value.absent()
          : Value(checksum),
      failureStage: failureStage == null && nullToAbsent
          ? const Value.absent()
          : Value(failureStage),
    );
  }

  factory AudioChunkRow.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return AudioChunkRow(
      id: serializer.fromJson<String>(json['id']),
      sessionId: serializer.fromJson<String>(json['sessionId']),
      chunkSeq: serializer.fromJson<int>(json['chunkSeq']),
      state: serializer.fromJson<String>(json['state']),
      createdAtUtc: serializer.fromJson<int>(json['createdAtUtc']),
      tempPath: serializer.fromJson<String>(json['tempPath']),
      finalPath: serializer.fromJson<String?>(json['finalPath']),
      sealedAtUtc: serializer.fromJson<int?>(json['sealedAtUtc']),
      capturedStartMs: serializer.fromJson<int?>(json['capturedStartMs']),
      capturedEndMs: serializer.fromJson<int?>(json['capturedEndMs']),
      durationMs: serializer.fromJson<int?>(json['durationMs']),
      codec: serializer.fromJson<String?>(json['codec']),
      sampleRate: serializer.fromJson<int?>(json['sampleRate']),
      bitRate: serializer.fromJson<int?>(json['bitRate']),
      checksum: serializer.fromJson<String?>(json['checksum']),
      failureStage: serializer.fromJson<String?>(json['failureStage']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'sessionId': serializer.toJson<String>(sessionId),
      'chunkSeq': serializer.toJson<int>(chunkSeq),
      'state': serializer.toJson<String>(state),
      'createdAtUtc': serializer.toJson<int>(createdAtUtc),
      'tempPath': serializer.toJson<String>(tempPath),
      'finalPath': serializer.toJson<String?>(finalPath),
      'sealedAtUtc': serializer.toJson<int?>(sealedAtUtc),
      'capturedStartMs': serializer.toJson<int?>(capturedStartMs),
      'capturedEndMs': serializer.toJson<int?>(capturedEndMs),
      'durationMs': serializer.toJson<int?>(durationMs),
      'codec': serializer.toJson<String?>(codec),
      'sampleRate': serializer.toJson<int?>(sampleRate),
      'bitRate': serializer.toJson<int?>(bitRate),
      'checksum': serializer.toJson<String?>(checksum),
      'failureStage': serializer.toJson<String?>(failureStage),
    };
  }

  AudioChunkRow copyWith({
    String? id,
    String? sessionId,
    int? chunkSeq,
    String? state,
    int? createdAtUtc,
    String? tempPath,
    Value<String?> finalPath = const Value.absent(),
    Value<int?> sealedAtUtc = const Value.absent(),
    Value<int?> capturedStartMs = const Value.absent(),
    Value<int?> capturedEndMs = const Value.absent(),
    Value<int?> durationMs = const Value.absent(),
    Value<String?> codec = const Value.absent(),
    Value<int?> sampleRate = const Value.absent(),
    Value<int?> bitRate = const Value.absent(),
    Value<String?> checksum = const Value.absent(),
    Value<String?> failureStage = const Value.absent(),
  }) => AudioChunkRow(
    id: id ?? this.id,
    sessionId: sessionId ?? this.sessionId,
    chunkSeq: chunkSeq ?? this.chunkSeq,
    state: state ?? this.state,
    createdAtUtc: createdAtUtc ?? this.createdAtUtc,
    tempPath: tempPath ?? this.tempPath,
    finalPath: finalPath.present ? finalPath.value : this.finalPath,
    sealedAtUtc: sealedAtUtc.present ? sealedAtUtc.value : this.sealedAtUtc,
    capturedStartMs: capturedStartMs.present
        ? capturedStartMs.value
        : this.capturedStartMs,
    capturedEndMs: capturedEndMs.present
        ? capturedEndMs.value
        : this.capturedEndMs,
    durationMs: durationMs.present ? durationMs.value : this.durationMs,
    codec: codec.present ? codec.value : this.codec,
    sampleRate: sampleRate.present ? sampleRate.value : this.sampleRate,
    bitRate: bitRate.present ? bitRate.value : this.bitRate,
    checksum: checksum.present ? checksum.value : this.checksum,
    failureStage: failureStage.present ? failureStage.value : this.failureStage,
  );
  AudioChunkRow copyWithCompanion(AudioChunksCompanion data) {
    return AudioChunkRow(
      id: data.id.present ? data.id.value : this.id,
      sessionId: data.sessionId.present ? data.sessionId.value : this.sessionId,
      chunkSeq: data.chunkSeq.present ? data.chunkSeq.value : this.chunkSeq,
      state: data.state.present ? data.state.value : this.state,
      createdAtUtc: data.createdAtUtc.present
          ? data.createdAtUtc.value
          : this.createdAtUtc,
      tempPath: data.tempPath.present ? data.tempPath.value : this.tempPath,
      finalPath: data.finalPath.present ? data.finalPath.value : this.finalPath,
      sealedAtUtc: data.sealedAtUtc.present
          ? data.sealedAtUtc.value
          : this.sealedAtUtc,
      capturedStartMs: data.capturedStartMs.present
          ? data.capturedStartMs.value
          : this.capturedStartMs,
      capturedEndMs: data.capturedEndMs.present
          ? data.capturedEndMs.value
          : this.capturedEndMs,
      durationMs: data.durationMs.present
          ? data.durationMs.value
          : this.durationMs,
      codec: data.codec.present ? data.codec.value : this.codec,
      sampleRate: data.sampleRate.present
          ? data.sampleRate.value
          : this.sampleRate,
      bitRate: data.bitRate.present ? data.bitRate.value : this.bitRate,
      checksum: data.checksum.present ? data.checksum.value : this.checksum,
      failureStage: data.failureStage.present
          ? data.failureStage.value
          : this.failureStage,
    );
  }

  @override
  String toString() {
    return (StringBuffer('AudioChunkRow(')
          ..write('id: $id, ')
          ..write('sessionId: $sessionId, ')
          ..write('chunkSeq: $chunkSeq, ')
          ..write('state: $state, ')
          ..write('createdAtUtc: $createdAtUtc, ')
          ..write('tempPath: $tempPath, ')
          ..write('finalPath: $finalPath, ')
          ..write('sealedAtUtc: $sealedAtUtc, ')
          ..write('capturedStartMs: $capturedStartMs, ')
          ..write('capturedEndMs: $capturedEndMs, ')
          ..write('durationMs: $durationMs, ')
          ..write('codec: $codec, ')
          ..write('sampleRate: $sampleRate, ')
          ..write('bitRate: $bitRate, ')
          ..write('checksum: $checksum, ')
          ..write('failureStage: $failureStage')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    sessionId,
    chunkSeq,
    state,
    createdAtUtc,
    tempPath,
    finalPath,
    sealedAtUtc,
    capturedStartMs,
    capturedEndMs,
    durationMs,
    codec,
    sampleRate,
    bitRate,
    checksum,
    failureStage,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is AudioChunkRow &&
          other.id == this.id &&
          other.sessionId == this.sessionId &&
          other.chunkSeq == this.chunkSeq &&
          other.state == this.state &&
          other.createdAtUtc == this.createdAtUtc &&
          other.tempPath == this.tempPath &&
          other.finalPath == this.finalPath &&
          other.sealedAtUtc == this.sealedAtUtc &&
          other.capturedStartMs == this.capturedStartMs &&
          other.capturedEndMs == this.capturedEndMs &&
          other.durationMs == this.durationMs &&
          other.codec == this.codec &&
          other.sampleRate == this.sampleRate &&
          other.bitRate == this.bitRate &&
          other.checksum == this.checksum &&
          other.failureStage == this.failureStage);
}

class AudioChunksCompanion extends UpdateCompanion<AudioChunkRow> {
  final Value<String> id;
  final Value<String> sessionId;
  final Value<int> chunkSeq;
  final Value<String> state;
  final Value<int> createdAtUtc;
  final Value<String> tempPath;
  final Value<String?> finalPath;
  final Value<int?> sealedAtUtc;
  final Value<int?> capturedStartMs;
  final Value<int?> capturedEndMs;
  final Value<int?> durationMs;
  final Value<String?> codec;
  final Value<int?> sampleRate;
  final Value<int?> bitRate;
  final Value<String?> checksum;
  final Value<String?> failureStage;
  final Value<int> rowid;
  const AudioChunksCompanion({
    this.id = const Value.absent(),
    this.sessionId = const Value.absent(),
    this.chunkSeq = const Value.absent(),
    this.state = const Value.absent(),
    this.createdAtUtc = const Value.absent(),
    this.tempPath = const Value.absent(),
    this.finalPath = const Value.absent(),
    this.sealedAtUtc = const Value.absent(),
    this.capturedStartMs = const Value.absent(),
    this.capturedEndMs = const Value.absent(),
    this.durationMs = const Value.absent(),
    this.codec = const Value.absent(),
    this.sampleRate = const Value.absent(),
    this.bitRate = const Value.absent(),
    this.checksum = const Value.absent(),
    this.failureStage = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  AudioChunksCompanion.insert({
    required String id,
    required String sessionId,
    required int chunkSeq,
    required String state,
    required int createdAtUtc,
    required String tempPath,
    this.finalPath = const Value.absent(),
    this.sealedAtUtc = const Value.absent(),
    this.capturedStartMs = const Value.absent(),
    this.capturedEndMs = const Value.absent(),
    this.durationMs = const Value.absent(),
    this.codec = const Value.absent(),
    this.sampleRate = const Value.absent(),
    this.bitRate = const Value.absent(),
    this.checksum = const Value.absent(),
    this.failureStage = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       sessionId = Value(sessionId),
       chunkSeq = Value(chunkSeq),
       state = Value(state),
       createdAtUtc = Value(createdAtUtc),
       tempPath = Value(tempPath);
  static Insertable<AudioChunkRow> custom({
    Expression<String>? id,
    Expression<String>? sessionId,
    Expression<int>? chunkSeq,
    Expression<String>? state,
    Expression<int>? createdAtUtc,
    Expression<String>? tempPath,
    Expression<String>? finalPath,
    Expression<int>? sealedAtUtc,
    Expression<int>? capturedStartMs,
    Expression<int>? capturedEndMs,
    Expression<int>? durationMs,
    Expression<String>? codec,
    Expression<int>? sampleRate,
    Expression<int>? bitRate,
    Expression<String>? checksum,
    Expression<String>? failureStage,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (sessionId != null) 'session_id': sessionId,
      if (chunkSeq != null) 'chunk_seq': chunkSeq,
      if (state != null) 'state': state,
      if (createdAtUtc != null) 'created_at_utc': createdAtUtc,
      if (tempPath != null) 'temp_path': tempPath,
      if (finalPath != null) 'final_path': finalPath,
      if (sealedAtUtc != null) 'sealed_at_utc': sealedAtUtc,
      if (capturedStartMs != null) 'captured_start_ms': capturedStartMs,
      if (capturedEndMs != null) 'captured_end_ms': capturedEndMs,
      if (durationMs != null) 'duration_ms': durationMs,
      if (codec != null) 'codec': codec,
      if (sampleRate != null) 'sample_rate': sampleRate,
      if (bitRate != null) 'bit_rate': bitRate,
      if (checksum != null) 'checksum': checksum,
      if (failureStage != null) 'failure_stage': failureStage,
      if (rowid != null) 'rowid': rowid,
    });
  }

  AudioChunksCompanion copyWith({
    Value<String>? id,
    Value<String>? sessionId,
    Value<int>? chunkSeq,
    Value<String>? state,
    Value<int>? createdAtUtc,
    Value<String>? tempPath,
    Value<String?>? finalPath,
    Value<int?>? sealedAtUtc,
    Value<int?>? capturedStartMs,
    Value<int?>? capturedEndMs,
    Value<int?>? durationMs,
    Value<String?>? codec,
    Value<int?>? sampleRate,
    Value<int?>? bitRate,
    Value<String?>? checksum,
    Value<String?>? failureStage,
    Value<int>? rowid,
  }) {
    return AudioChunksCompanion(
      id: id ?? this.id,
      sessionId: sessionId ?? this.sessionId,
      chunkSeq: chunkSeq ?? this.chunkSeq,
      state: state ?? this.state,
      createdAtUtc: createdAtUtc ?? this.createdAtUtc,
      tempPath: tempPath ?? this.tempPath,
      finalPath: finalPath ?? this.finalPath,
      sealedAtUtc: sealedAtUtc ?? this.sealedAtUtc,
      capturedStartMs: capturedStartMs ?? this.capturedStartMs,
      capturedEndMs: capturedEndMs ?? this.capturedEndMs,
      durationMs: durationMs ?? this.durationMs,
      codec: codec ?? this.codec,
      sampleRate: sampleRate ?? this.sampleRate,
      bitRate: bitRate ?? this.bitRate,
      checksum: checksum ?? this.checksum,
      failureStage: failureStage ?? this.failureStage,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (sessionId.present) {
      map['session_id'] = Variable<String>(sessionId.value);
    }
    if (chunkSeq.present) {
      map['chunk_seq'] = Variable<int>(chunkSeq.value);
    }
    if (state.present) {
      map['state'] = Variable<String>(state.value);
    }
    if (createdAtUtc.present) {
      map['created_at_utc'] = Variable<int>(createdAtUtc.value);
    }
    if (tempPath.present) {
      map['temp_path'] = Variable<String>(tempPath.value);
    }
    if (finalPath.present) {
      map['final_path'] = Variable<String>(finalPath.value);
    }
    if (sealedAtUtc.present) {
      map['sealed_at_utc'] = Variable<int>(sealedAtUtc.value);
    }
    if (capturedStartMs.present) {
      map['captured_start_ms'] = Variable<int>(capturedStartMs.value);
    }
    if (capturedEndMs.present) {
      map['captured_end_ms'] = Variable<int>(capturedEndMs.value);
    }
    if (durationMs.present) {
      map['duration_ms'] = Variable<int>(durationMs.value);
    }
    if (codec.present) {
      map['codec'] = Variable<String>(codec.value);
    }
    if (sampleRate.present) {
      map['sample_rate'] = Variable<int>(sampleRate.value);
    }
    if (bitRate.present) {
      map['bit_rate'] = Variable<int>(bitRate.value);
    }
    if (checksum.present) {
      map['checksum'] = Variable<String>(checksum.value);
    }
    if (failureStage.present) {
      map['failure_stage'] = Variable<String>(failureStage.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('AudioChunksCompanion(')
          ..write('id: $id, ')
          ..write('sessionId: $sessionId, ')
          ..write('chunkSeq: $chunkSeq, ')
          ..write('state: $state, ')
          ..write('createdAtUtc: $createdAtUtc, ')
          ..write('tempPath: $tempPath, ')
          ..write('finalPath: $finalPath, ')
          ..write('sealedAtUtc: $sealedAtUtc, ')
          ..write('capturedStartMs: $capturedStartMs, ')
          ..write('capturedEndMs: $capturedEndMs, ')
          ..write('durationMs: $durationMs, ')
          ..write('codec: $codec, ')
          ..write('sampleRate: $sampleRate, ')
          ..write('bitRate: $bitRate, ')
          ..write('checksum: $checksum, ')
          ..write('failureStage: $failureStage, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $FinalSourcesTable extends FinalSources
    with TableInfo<$FinalSourcesTable, FinalSourceRow> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $FinalSourcesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _sessionIdMeta = const VerificationMeta(
    'sessionId',
  );
  @override
  late final GeneratedColumn<String> sessionId = GeneratedColumn<String>(
    'session_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES sessions (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _text$Meta = const VerificationMeta('text\$');
  @override
  late final GeneratedColumn<String> text$ = GeneratedColumn<String>(
    'text',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _finalizedAtUtcMeta = const VerificationMeta(
    'finalizedAtUtc',
  );
  @override
  late final GeneratedColumn<int> finalizedAtUtc = GeneratedColumn<int>(
    'finalized_at_utc',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _captureStartedMonoNsMeta =
      const VerificationMeta('captureStartedMonoNs');
  @override
  late final GeneratedColumn<int> captureStartedMonoNs = GeneratedColumn<int>(
    'capture_started_mono_ns',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _captureEndedMonoNsMeta =
      const VerificationMeta('captureEndedMonoNs');
  @override
  late final GeneratedColumn<int> captureEndedMonoNs = GeneratedColumn<int>(
    'capture_ended_mono_ns',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _capturedStartMsMeta = const VerificationMeta(
    'capturedStartMs',
  );
  @override
  late final GeneratedColumn<int> capturedStartMs = GeneratedColumn<int>(
    'captured_start_ms',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _capturedEndMsMeta = const VerificationMeta(
    'capturedEndMs',
  );
  @override
  late final GeneratedColumn<int> capturedEndMs = GeneratedColumn<int>(
    'captured_end_ms',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _chunkIdsMeta = const VerificationMeta(
    'chunkIds',
  );
  @override
  late final GeneratedColumn<String> chunkIds = GeneratedColumn<String>(
    'chunk_ids',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _modelIdMeta = const VerificationMeta(
    'modelId',
  );
  @override
  late final GeneratedColumn<String> modelId = GeneratedColumn<String>(
    'model_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _modelVersionMeta = const VerificationMeta(
    'modelVersion',
  );
  @override
  late final GeneratedColumn<String> modelVersion = GeneratedColumn<String>(
    'model_version',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    sessionId,
    text$,
    finalizedAtUtc,
    captureStartedMonoNs,
    captureEndedMonoNs,
    capturedStartMs,
    capturedEndMs,
    chunkIds,
    modelId,
    modelVersion,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'final_sources';
  @override
  VerificationContext validateIntegrity(
    Insertable<FinalSourceRow> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('session_id')) {
      context.handle(
        _sessionIdMeta,
        sessionId.isAcceptableOrUnknown(data['session_id']!, _sessionIdMeta),
      );
    } else if (isInserting) {
      context.missing(_sessionIdMeta);
    }
    if (data.containsKey('text')) {
      context.handle(
        _text$Meta,
        text$.isAcceptableOrUnknown(data['text']!, _text$Meta),
      );
    } else if (isInserting) {
      context.missing(_text$Meta);
    }
    if (data.containsKey('finalized_at_utc')) {
      context.handle(
        _finalizedAtUtcMeta,
        finalizedAtUtc.isAcceptableOrUnknown(
          data['finalized_at_utc']!,
          _finalizedAtUtcMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_finalizedAtUtcMeta);
    }
    if (data.containsKey('capture_started_mono_ns')) {
      context.handle(
        _captureStartedMonoNsMeta,
        captureStartedMonoNs.isAcceptableOrUnknown(
          data['capture_started_mono_ns']!,
          _captureStartedMonoNsMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_captureStartedMonoNsMeta);
    }
    if (data.containsKey('capture_ended_mono_ns')) {
      context.handle(
        _captureEndedMonoNsMeta,
        captureEndedMonoNs.isAcceptableOrUnknown(
          data['capture_ended_mono_ns']!,
          _captureEndedMonoNsMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_captureEndedMonoNsMeta);
    }
    if (data.containsKey('captured_start_ms')) {
      context.handle(
        _capturedStartMsMeta,
        capturedStartMs.isAcceptableOrUnknown(
          data['captured_start_ms']!,
          _capturedStartMsMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_capturedStartMsMeta);
    }
    if (data.containsKey('captured_end_ms')) {
      context.handle(
        _capturedEndMsMeta,
        capturedEndMs.isAcceptableOrUnknown(
          data['captured_end_ms']!,
          _capturedEndMsMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_capturedEndMsMeta);
    }
    if (data.containsKey('chunk_ids')) {
      context.handle(
        _chunkIdsMeta,
        chunkIds.isAcceptableOrUnknown(data['chunk_ids']!, _chunkIdsMeta),
      );
    } else if (isInserting) {
      context.missing(_chunkIdsMeta);
    }
    if (data.containsKey('model_id')) {
      context.handle(
        _modelIdMeta,
        modelId.isAcceptableOrUnknown(data['model_id']!, _modelIdMeta),
      );
    } else if (isInserting) {
      context.missing(_modelIdMeta);
    }
    if (data.containsKey('model_version')) {
      context.handle(
        _modelVersionMeta,
        modelVersion.isAcceptableOrUnknown(
          data['model_version']!,
          _modelVersionMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_modelVersionMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  FinalSourceRow map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return FinalSourceRow(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      sessionId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}session_id'],
      )!,
      text$: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}text'],
      )!,
      finalizedAtUtc: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}finalized_at_utc'],
      )!,
      captureStartedMonoNs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}capture_started_mono_ns'],
      )!,
      captureEndedMonoNs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}capture_ended_mono_ns'],
      )!,
      capturedStartMs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}captured_start_ms'],
      )!,
      capturedEndMs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}captured_end_ms'],
      )!,
      chunkIds: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}chunk_ids'],
      )!,
      modelId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}model_id'],
      )!,
      modelVersion: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}model_version'],
      )!,
    );
  }

  @override
  $FinalSourcesTable createAlias(String alias) {
    return $FinalSourcesTable(attachedDatabase, alias);
  }
}

class FinalSourceRow extends DataClass implements Insertable<FinalSourceRow> {
  final String id;
  final String sessionId;

  /// 避开 Table.text 构建器；保留 SQLite 列名与 JSON 键。
  final String text$;
  final int finalizedAtUtc;
  final int captureStartedMonoNs;
  final int captureEndedMonoNs;
  final int capturedStartMs;
  final int capturedEndMs;
  final String chunkIds;
  final String modelId;
  final String modelVersion;
  const FinalSourceRow({
    required this.id,
    required this.sessionId,
    required this.text$,
    required this.finalizedAtUtc,
    required this.captureStartedMonoNs,
    required this.captureEndedMonoNs,
    required this.capturedStartMs,
    required this.capturedEndMs,
    required this.chunkIds,
    required this.modelId,
    required this.modelVersion,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['session_id'] = Variable<String>(sessionId);
    map['text'] = Variable<String>(text$);
    map['finalized_at_utc'] = Variable<int>(finalizedAtUtc);
    map['capture_started_mono_ns'] = Variable<int>(captureStartedMonoNs);
    map['capture_ended_mono_ns'] = Variable<int>(captureEndedMonoNs);
    map['captured_start_ms'] = Variable<int>(capturedStartMs);
    map['captured_end_ms'] = Variable<int>(capturedEndMs);
    map['chunk_ids'] = Variable<String>(chunkIds);
    map['model_id'] = Variable<String>(modelId);
    map['model_version'] = Variable<String>(modelVersion);
    return map;
  }

  FinalSourcesCompanion toCompanion(bool nullToAbsent) {
    return FinalSourcesCompanion(
      id: Value(id),
      sessionId: Value(sessionId),
      text$: Value(text$),
      finalizedAtUtc: Value(finalizedAtUtc),
      captureStartedMonoNs: Value(captureStartedMonoNs),
      captureEndedMonoNs: Value(captureEndedMonoNs),
      capturedStartMs: Value(capturedStartMs),
      capturedEndMs: Value(capturedEndMs),
      chunkIds: Value(chunkIds),
      modelId: Value(modelId),
      modelVersion: Value(modelVersion),
    );
  }

  factory FinalSourceRow.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return FinalSourceRow(
      id: serializer.fromJson<String>(json['id']),
      sessionId: serializer.fromJson<String>(json['sessionId']),
      text$: serializer.fromJson<String>(json['text']),
      finalizedAtUtc: serializer.fromJson<int>(json['finalizedAtUtc']),
      captureStartedMonoNs: serializer.fromJson<int>(
        json['captureStartedMonoNs'],
      ),
      captureEndedMonoNs: serializer.fromJson<int>(json['captureEndedMonoNs']),
      capturedStartMs: serializer.fromJson<int>(json['capturedStartMs']),
      capturedEndMs: serializer.fromJson<int>(json['capturedEndMs']),
      chunkIds: serializer.fromJson<String>(json['chunkIds']),
      modelId: serializer.fromJson<String>(json['modelId']),
      modelVersion: serializer.fromJson<String>(json['modelVersion']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'sessionId': serializer.toJson<String>(sessionId),
      'text': serializer.toJson<String>(text$),
      'finalizedAtUtc': serializer.toJson<int>(finalizedAtUtc),
      'captureStartedMonoNs': serializer.toJson<int>(captureStartedMonoNs),
      'captureEndedMonoNs': serializer.toJson<int>(captureEndedMonoNs),
      'capturedStartMs': serializer.toJson<int>(capturedStartMs),
      'capturedEndMs': serializer.toJson<int>(capturedEndMs),
      'chunkIds': serializer.toJson<String>(chunkIds),
      'modelId': serializer.toJson<String>(modelId),
      'modelVersion': serializer.toJson<String>(modelVersion),
    };
  }

  FinalSourceRow copyWith({
    String? id,
    String? sessionId,
    String? text$,
    int? finalizedAtUtc,
    int? captureStartedMonoNs,
    int? captureEndedMonoNs,
    int? capturedStartMs,
    int? capturedEndMs,
    String? chunkIds,
    String? modelId,
    String? modelVersion,
  }) => FinalSourceRow(
    id: id ?? this.id,
    sessionId: sessionId ?? this.sessionId,
    text$: text$ ?? this.text$,
    finalizedAtUtc: finalizedAtUtc ?? this.finalizedAtUtc,
    captureStartedMonoNs: captureStartedMonoNs ?? this.captureStartedMonoNs,
    captureEndedMonoNs: captureEndedMonoNs ?? this.captureEndedMonoNs,
    capturedStartMs: capturedStartMs ?? this.capturedStartMs,
    capturedEndMs: capturedEndMs ?? this.capturedEndMs,
    chunkIds: chunkIds ?? this.chunkIds,
    modelId: modelId ?? this.modelId,
    modelVersion: modelVersion ?? this.modelVersion,
  );
  FinalSourceRow copyWithCompanion(FinalSourcesCompanion data) {
    return FinalSourceRow(
      id: data.id.present ? data.id.value : this.id,
      sessionId: data.sessionId.present ? data.sessionId.value : this.sessionId,
      text$: data.text$.present ? data.text$.value : this.text$,
      finalizedAtUtc: data.finalizedAtUtc.present
          ? data.finalizedAtUtc.value
          : this.finalizedAtUtc,
      captureStartedMonoNs: data.captureStartedMonoNs.present
          ? data.captureStartedMonoNs.value
          : this.captureStartedMonoNs,
      captureEndedMonoNs: data.captureEndedMonoNs.present
          ? data.captureEndedMonoNs.value
          : this.captureEndedMonoNs,
      capturedStartMs: data.capturedStartMs.present
          ? data.capturedStartMs.value
          : this.capturedStartMs,
      capturedEndMs: data.capturedEndMs.present
          ? data.capturedEndMs.value
          : this.capturedEndMs,
      chunkIds: data.chunkIds.present ? data.chunkIds.value : this.chunkIds,
      modelId: data.modelId.present ? data.modelId.value : this.modelId,
      modelVersion: data.modelVersion.present
          ? data.modelVersion.value
          : this.modelVersion,
    );
  }

  @override
  String toString() {
    return (StringBuffer('FinalSourceRow(')
          ..write('id: $id, ')
          ..write('sessionId: $sessionId, ')
          ..write('text\$: ${text$}, ')
          ..write('finalizedAtUtc: $finalizedAtUtc, ')
          ..write('captureStartedMonoNs: $captureStartedMonoNs, ')
          ..write('captureEndedMonoNs: $captureEndedMonoNs, ')
          ..write('capturedStartMs: $capturedStartMs, ')
          ..write('capturedEndMs: $capturedEndMs, ')
          ..write('chunkIds: $chunkIds, ')
          ..write('modelId: $modelId, ')
          ..write('modelVersion: $modelVersion')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    sessionId,
    text$,
    finalizedAtUtc,
    captureStartedMonoNs,
    captureEndedMonoNs,
    capturedStartMs,
    capturedEndMs,
    chunkIds,
    modelId,
    modelVersion,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is FinalSourceRow &&
          other.id == this.id &&
          other.sessionId == this.sessionId &&
          other.text$ == this.text$ &&
          other.finalizedAtUtc == this.finalizedAtUtc &&
          other.captureStartedMonoNs == this.captureStartedMonoNs &&
          other.captureEndedMonoNs == this.captureEndedMonoNs &&
          other.capturedStartMs == this.capturedStartMs &&
          other.capturedEndMs == this.capturedEndMs &&
          other.chunkIds == this.chunkIds &&
          other.modelId == this.modelId &&
          other.modelVersion == this.modelVersion);
}

class FinalSourcesCompanion extends UpdateCompanion<FinalSourceRow> {
  final Value<String> id;
  final Value<String> sessionId;
  final Value<String> text$;
  final Value<int> finalizedAtUtc;
  final Value<int> captureStartedMonoNs;
  final Value<int> captureEndedMonoNs;
  final Value<int> capturedStartMs;
  final Value<int> capturedEndMs;
  final Value<String> chunkIds;
  final Value<String> modelId;
  final Value<String> modelVersion;
  final Value<int> rowid;
  const FinalSourcesCompanion({
    this.id = const Value.absent(),
    this.sessionId = const Value.absent(),
    this.text$ = const Value.absent(),
    this.finalizedAtUtc = const Value.absent(),
    this.captureStartedMonoNs = const Value.absent(),
    this.captureEndedMonoNs = const Value.absent(),
    this.capturedStartMs = const Value.absent(),
    this.capturedEndMs = const Value.absent(),
    this.chunkIds = const Value.absent(),
    this.modelId = const Value.absent(),
    this.modelVersion = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  FinalSourcesCompanion.insert({
    required String id,
    required String sessionId,
    required String text$,
    required int finalizedAtUtc,
    required int captureStartedMonoNs,
    required int captureEndedMonoNs,
    required int capturedStartMs,
    required int capturedEndMs,
    required String chunkIds,
    required String modelId,
    required String modelVersion,
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       sessionId = Value(sessionId),
       text$ = Value(text$),
       finalizedAtUtc = Value(finalizedAtUtc),
       captureStartedMonoNs = Value(captureStartedMonoNs),
       captureEndedMonoNs = Value(captureEndedMonoNs),
       capturedStartMs = Value(capturedStartMs),
       capturedEndMs = Value(capturedEndMs),
       chunkIds = Value(chunkIds),
       modelId = Value(modelId),
       modelVersion = Value(modelVersion);
  static Insertable<FinalSourceRow> custom({
    Expression<String>? id,
    Expression<String>? sessionId,
    Expression<String>? text$,
    Expression<int>? finalizedAtUtc,
    Expression<int>? captureStartedMonoNs,
    Expression<int>? captureEndedMonoNs,
    Expression<int>? capturedStartMs,
    Expression<int>? capturedEndMs,
    Expression<String>? chunkIds,
    Expression<String>? modelId,
    Expression<String>? modelVersion,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (sessionId != null) 'session_id': sessionId,
      if (text$ != null) 'text': text$,
      if (finalizedAtUtc != null) 'finalized_at_utc': finalizedAtUtc,
      if (captureStartedMonoNs != null)
        'capture_started_mono_ns': captureStartedMonoNs,
      if (captureEndedMonoNs != null)
        'capture_ended_mono_ns': captureEndedMonoNs,
      if (capturedStartMs != null) 'captured_start_ms': capturedStartMs,
      if (capturedEndMs != null) 'captured_end_ms': capturedEndMs,
      if (chunkIds != null) 'chunk_ids': chunkIds,
      if (modelId != null) 'model_id': modelId,
      if (modelVersion != null) 'model_version': modelVersion,
      if (rowid != null) 'rowid': rowid,
    });
  }

  FinalSourcesCompanion copyWith({
    Value<String>? id,
    Value<String>? sessionId,
    Value<String>? text$,
    Value<int>? finalizedAtUtc,
    Value<int>? captureStartedMonoNs,
    Value<int>? captureEndedMonoNs,
    Value<int>? capturedStartMs,
    Value<int>? capturedEndMs,
    Value<String>? chunkIds,
    Value<String>? modelId,
    Value<String>? modelVersion,
    Value<int>? rowid,
  }) {
    return FinalSourcesCompanion(
      id: id ?? this.id,
      sessionId: sessionId ?? this.sessionId,
      text$: text$ ?? this.text$,
      finalizedAtUtc: finalizedAtUtc ?? this.finalizedAtUtc,
      captureStartedMonoNs: captureStartedMonoNs ?? this.captureStartedMonoNs,
      captureEndedMonoNs: captureEndedMonoNs ?? this.captureEndedMonoNs,
      capturedStartMs: capturedStartMs ?? this.capturedStartMs,
      capturedEndMs: capturedEndMs ?? this.capturedEndMs,
      chunkIds: chunkIds ?? this.chunkIds,
      modelId: modelId ?? this.modelId,
      modelVersion: modelVersion ?? this.modelVersion,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (sessionId.present) {
      map['session_id'] = Variable<String>(sessionId.value);
    }
    if (text$.present) {
      map['text'] = Variable<String>(text$.value);
    }
    if (finalizedAtUtc.present) {
      map['finalized_at_utc'] = Variable<int>(finalizedAtUtc.value);
    }
    if (captureStartedMonoNs.present) {
      map['capture_started_mono_ns'] = Variable<int>(
        captureStartedMonoNs.value,
      );
    }
    if (captureEndedMonoNs.present) {
      map['capture_ended_mono_ns'] = Variable<int>(captureEndedMonoNs.value);
    }
    if (capturedStartMs.present) {
      map['captured_start_ms'] = Variable<int>(capturedStartMs.value);
    }
    if (capturedEndMs.present) {
      map['captured_end_ms'] = Variable<int>(capturedEndMs.value);
    }
    if (chunkIds.present) {
      map['chunk_ids'] = Variable<String>(chunkIds.value);
    }
    if (modelId.present) {
      map['model_id'] = Variable<String>(modelId.value);
    }
    if (modelVersion.present) {
      map['model_version'] = Variable<String>(modelVersion.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('FinalSourcesCompanion(')
          ..write('id: $id, ')
          ..write('sessionId: $sessionId, ')
          ..write('text\$: ${text$}, ')
          ..write('finalizedAtUtc: $finalizedAtUtc, ')
          ..write('captureStartedMonoNs: $captureStartedMonoNs, ')
          ..write('captureEndedMonoNs: $captureEndedMonoNs, ')
          ..write('capturedStartMs: $capturedStartMs, ')
          ..write('capturedEndMs: $capturedEndMs, ')
          ..write('chunkIds: $chunkIds, ')
          ..write('modelId: $modelId, ')
          ..write('modelVersion: $modelVersion, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $SegmentsTable extends Segments
    with TableInfo<$SegmentsTable, SegmentRow> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $SegmentsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _sessionIdMeta = const VerificationMeta(
    'sessionId',
  );
  @override
  late final GeneratedColumn<String> sessionId = GeneratedColumn<String>(
    'session_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES sessions (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _ordinalMeta = const VerificationMeta(
    'ordinal',
  );
  @override
  late final GeneratedColumn<int> ordinal = GeneratedColumn<int>(
    'ordinal',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _kindMeta = const VerificationMeta('kind');
  @override
  late final GeneratedColumn<String> kind = GeneratedColumn<String>(
    'kind',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _sourceFinalIdsMeta = const VerificationMeta(
    'sourceFinalIds',
  );
  @override
  late final GeneratedColumn<String> sourceFinalIds = GeneratedColumn<String>(
    'source_final_ids',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _capturedStartMsMeta = const VerificationMeta(
    'capturedStartMs',
  );
  @override
  late final GeneratedColumn<int> capturedStartMs = GeneratedColumn<int>(
    'captured_start_ms',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _capturedEndMsMeta = const VerificationMeta(
    'capturedEndMs',
  );
  @override
  late final GeneratedColumn<int> capturedEndMs = GeneratedColumn<int>(
    'captured_end_ms',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _chunkIdsMeta = const VerificationMeta(
    'chunkIds',
  );
  @override
  late final GeneratedColumn<String> chunkIds = GeneratedColumn<String>(
    'chunk_ids',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _gapReasonMeta = const VerificationMeta(
    'gapReason',
  );
  @override
  late final GeneratedColumn<String> gapReason = GeneratedColumn<String>(
    'gap_reason',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _foldDueAtUtcMeta = const VerificationMeta(
    'foldDueAtUtc',
  );
  @override
  late final GeneratedColumn<int> foldDueAtUtc = GeneratedColumn<int>(
    'fold_due_at_utc',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _defaultVersionIdMeta = const VerificationMeta(
    'defaultVersionId',
  );
  @override
  late final GeneratedColumn<String> defaultVersionId = GeneratedColumn<String>(
    'default_version_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    sessionId,
    ordinal,
    kind,
    sourceFinalIds,
    capturedStartMs,
    capturedEndMs,
    chunkIds,
    gapReason,
    foldDueAtUtc,
    defaultVersionId,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'segments';
  @override
  VerificationContext validateIntegrity(
    Insertable<SegmentRow> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('session_id')) {
      context.handle(
        _sessionIdMeta,
        sessionId.isAcceptableOrUnknown(data['session_id']!, _sessionIdMeta),
      );
    } else if (isInserting) {
      context.missing(_sessionIdMeta);
    }
    if (data.containsKey('ordinal')) {
      context.handle(
        _ordinalMeta,
        ordinal.isAcceptableOrUnknown(data['ordinal']!, _ordinalMeta),
      );
    } else if (isInserting) {
      context.missing(_ordinalMeta);
    }
    if (data.containsKey('kind')) {
      context.handle(
        _kindMeta,
        kind.isAcceptableOrUnknown(data['kind']!, _kindMeta),
      );
    } else if (isInserting) {
      context.missing(_kindMeta);
    }
    if (data.containsKey('source_final_ids')) {
      context.handle(
        _sourceFinalIdsMeta,
        sourceFinalIds.isAcceptableOrUnknown(
          data['source_final_ids']!,
          _sourceFinalIdsMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_sourceFinalIdsMeta);
    }
    if (data.containsKey('captured_start_ms')) {
      context.handle(
        _capturedStartMsMeta,
        capturedStartMs.isAcceptableOrUnknown(
          data['captured_start_ms']!,
          _capturedStartMsMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_capturedStartMsMeta);
    }
    if (data.containsKey('captured_end_ms')) {
      context.handle(
        _capturedEndMsMeta,
        capturedEndMs.isAcceptableOrUnknown(
          data['captured_end_ms']!,
          _capturedEndMsMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_capturedEndMsMeta);
    }
    if (data.containsKey('chunk_ids')) {
      context.handle(
        _chunkIdsMeta,
        chunkIds.isAcceptableOrUnknown(data['chunk_ids']!, _chunkIdsMeta),
      );
    } else if (isInserting) {
      context.missing(_chunkIdsMeta);
    }
    if (data.containsKey('gap_reason')) {
      context.handle(
        _gapReasonMeta,
        gapReason.isAcceptableOrUnknown(data['gap_reason']!, _gapReasonMeta),
      );
    }
    if (data.containsKey('fold_due_at_utc')) {
      context.handle(
        _foldDueAtUtcMeta,
        foldDueAtUtc.isAcceptableOrUnknown(
          data['fold_due_at_utc']!,
          _foldDueAtUtcMeta,
        ),
      );
    }
    if (data.containsKey('default_version_id')) {
      context.handle(
        _defaultVersionIdMeta,
        defaultVersionId.isAcceptableOrUnknown(
          data['default_version_id']!,
          _defaultVersionIdMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  List<Set<GeneratedColumn>> get uniqueKeys => [
    {sessionId, ordinal},
  ];
  @override
  SegmentRow map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return SegmentRow(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      sessionId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}session_id'],
      )!,
      ordinal: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}ordinal'],
      )!,
      kind: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}kind'],
      )!,
      sourceFinalIds: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}source_final_ids'],
      )!,
      capturedStartMs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}captured_start_ms'],
      )!,
      capturedEndMs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}captured_end_ms'],
      )!,
      chunkIds: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}chunk_ids'],
      )!,
      gapReason: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}gap_reason'],
      ),
      foldDueAtUtc: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}fold_due_at_utc'],
      ),
      defaultVersionId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}default_version_id'],
      ),
    );
  }

  @override
  $SegmentsTable createAlias(String alias) {
    return $SegmentsTable(attachedDatabase, alias);
  }
}

class SegmentRow extends DataClass implements Insertable<SegmentRow> {
  final String id;
  final String sessionId;
  final int ordinal;
  final String kind;

  /// 有序 FINAL ID JSON 数组；GAP 段为空数组。
  final String sourceFinalIds;
  final int capturedStartMs;
  final int capturedEndMs;

  /// 有序音频块 ID JSON 数组。
  final String chunkIds;
  final String? gapReason;
  final int? foldDueAtUtc;
  final String? defaultVersionId;
  const SegmentRow({
    required this.id,
    required this.sessionId,
    required this.ordinal,
    required this.kind,
    required this.sourceFinalIds,
    required this.capturedStartMs,
    required this.capturedEndMs,
    required this.chunkIds,
    this.gapReason,
    this.foldDueAtUtc,
    this.defaultVersionId,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['session_id'] = Variable<String>(sessionId);
    map['ordinal'] = Variable<int>(ordinal);
    map['kind'] = Variable<String>(kind);
    map['source_final_ids'] = Variable<String>(sourceFinalIds);
    map['captured_start_ms'] = Variable<int>(capturedStartMs);
    map['captured_end_ms'] = Variable<int>(capturedEndMs);
    map['chunk_ids'] = Variable<String>(chunkIds);
    if (!nullToAbsent || gapReason != null) {
      map['gap_reason'] = Variable<String>(gapReason);
    }
    if (!nullToAbsent || foldDueAtUtc != null) {
      map['fold_due_at_utc'] = Variable<int>(foldDueAtUtc);
    }
    if (!nullToAbsent || defaultVersionId != null) {
      map['default_version_id'] = Variable<String>(defaultVersionId);
    }
    return map;
  }

  SegmentsCompanion toCompanion(bool nullToAbsent) {
    return SegmentsCompanion(
      id: Value(id),
      sessionId: Value(sessionId),
      ordinal: Value(ordinal),
      kind: Value(kind),
      sourceFinalIds: Value(sourceFinalIds),
      capturedStartMs: Value(capturedStartMs),
      capturedEndMs: Value(capturedEndMs),
      chunkIds: Value(chunkIds),
      gapReason: gapReason == null && nullToAbsent
          ? const Value.absent()
          : Value(gapReason),
      foldDueAtUtc: foldDueAtUtc == null && nullToAbsent
          ? const Value.absent()
          : Value(foldDueAtUtc),
      defaultVersionId: defaultVersionId == null && nullToAbsent
          ? const Value.absent()
          : Value(defaultVersionId),
    );
  }

  factory SegmentRow.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return SegmentRow(
      id: serializer.fromJson<String>(json['id']),
      sessionId: serializer.fromJson<String>(json['sessionId']),
      ordinal: serializer.fromJson<int>(json['ordinal']),
      kind: serializer.fromJson<String>(json['kind']),
      sourceFinalIds: serializer.fromJson<String>(json['sourceFinalIds']),
      capturedStartMs: serializer.fromJson<int>(json['capturedStartMs']),
      capturedEndMs: serializer.fromJson<int>(json['capturedEndMs']),
      chunkIds: serializer.fromJson<String>(json['chunkIds']),
      gapReason: serializer.fromJson<String?>(json['gapReason']),
      foldDueAtUtc: serializer.fromJson<int?>(json['foldDueAtUtc']),
      defaultVersionId: serializer.fromJson<String?>(json['defaultVersionId']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'sessionId': serializer.toJson<String>(sessionId),
      'ordinal': serializer.toJson<int>(ordinal),
      'kind': serializer.toJson<String>(kind),
      'sourceFinalIds': serializer.toJson<String>(sourceFinalIds),
      'capturedStartMs': serializer.toJson<int>(capturedStartMs),
      'capturedEndMs': serializer.toJson<int>(capturedEndMs),
      'chunkIds': serializer.toJson<String>(chunkIds),
      'gapReason': serializer.toJson<String?>(gapReason),
      'foldDueAtUtc': serializer.toJson<int?>(foldDueAtUtc),
      'defaultVersionId': serializer.toJson<String?>(defaultVersionId),
    };
  }

  SegmentRow copyWith({
    String? id,
    String? sessionId,
    int? ordinal,
    String? kind,
    String? sourceFinalIds,
    int? capturedStartMs,
    int? capturedEndMs,
    String? chunkIds,
    Value<String?> gapReason = const Value.absent(),
    Value<int?> foldDueAtUtc = const Value.absent(),
    Value<String?> defaultVersionId = const Value.absent(),
  }) => SegmentRow(
    id: id ?? this.id,
    sessionId: sessionId ?? this.sessionId,
    ordinal: ordinal ?? this.ordinal,
    kind: kind ?? this.kind,
    sourceFinalIds: sourceFinalIds ?? this.sourceFinalIds,
    capturedStartMs: capturedStartMs ?? this.capturedStartMs,
    capturedEndMs: capturedEndMs ?? this.capturedEndMs,
    chunkIds: chunkIds ?? this.chunkIds,
    gapReason: gapReason.present ? gapReason.value : this.gapReason,
    foldDueAtUtc: foldDueAtUtc.present ? foldDueAtUtc.value : this.foldDueAtUtc,
    defaultVersionId: defaultVersionId.present
        ? defaultVersionId.value
        : this.defaultVersionId,
  );
  SegmentRow copyWithCompanion(SegmentsCompanion data) {
    return SegmentRow(
      id: data.id.present ? data.id.value : this.id,
      sessionId: data.sessionId.present ? data.sessionId.value : this.sessionId,
      ordinal: data.ordinal.present ? data.ordinal.value : this.ordinal,
      kind: data.kind.present ? data.kind.value : this.kind,
      sourceFinalIds: data.sourceFinalIds.present
          ? data.sourceFinalIds.value
          : this.sourceFinalIds,
      capturedStartMs: data.capturedStartMs.present
          ? data.capturedStartMs.value
          : this.capturedStartMs,
      capturedEndMs: data.capturedEndMs.present
          ? data.capturedEndMs.value
          : this.capturedEndMs,
      chunkIds: data.chunkIds.present ? data.chunkIds.value : this.chunkIds,
      gapReason: data.gapReason.present ? data.gapReason.value : this.gapReason,
      foldDueAtUtc: data.foldDueAtUtc.present
          ? data.foldDueAtUtc.value
          : this.foldDueAtUtc,
      defaultVersionId: data.defaultVersionId.present
          ? data.defaultVersionId.value
          : this.defaultVersionId,
    );
  }

  @override
  String toString() {
    return (StringBuffer('SegmentRow(')
          ..write('id: $id, ')
          ..write('sessionId: $sessionId, ')
          ..write('ordinal: $ordinal, ')
          ..write('kind: $kind, ')
          ..write('sourceFinalIds: $sourceFinalIds, ')
          ..write('capturedStartMs: $capturedStartMs, ')
          ..write('capturedEndMs: $capturedEndMs, ')
          ..write('chunkIds: $chunkIds, ')
          ..write('gapReason: $gapReason, ')
          ..write('foldDueAtUtc: $foldDueAtUtc, ')
          ..write('defaultVersionId: $defaultVersionId')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    sessionId,
    ordinal,
    kind,
    sourceFinalIds,
    capturedStartMs,
    capturedEndMs,
    chunkIds,
    gapReason,
    foldDueAtUtc,
    defaultVersionId,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is SegmentRow &&
          other.id == this.id &&
          other.sessionId == this.sessionId &&
          other.ordinal == this.ordinal &&
          other.kind == this.kind &&
          other.sourceFinalIds == this.sourceFinalIds &&
          other.capturedStartMs == this.capturedStartMs &&
          other.capturedEndMs == this.capturedEndMs &&
          other.chunkIds == this.chunkIds &&
          other.gapReason == this.gapReason &&
          other.foldDueAtUtc == this.foldDueAtUtc &&
          other.defaultVersionId == this.defaultVersionId);
}

class SegmentsCompanion extends UpdateCompanion<SegmentRow> {
  final Value<String> id;
  final Value<String> sessionId;
  final Value<int> ordinal;
  final Value<String> kind;
  final Value<String> sourceFinalIds;
  final Value<int> capturedStartMs;
  final Value<int> capturedEndMs;
  final Value<String> chunkIds;
  final Value<String?> gapReason;
  final Value<int?> foldDueAtUtc;
  final Value<String?> defaultVersionId;
  final Value<int> rowid;
  const SegmentsCompanion({
    this.id = const Value.absent(),
    this.sessionId = const Value.absent(),
    this.ordinal = const Value.absent(),
    this.kind = const Value.absent(),
    this.sourceFinalIds = const Value.absent(),
    this.capturedStartMs = const Value.absent(),
    this.capturedEndMs = const Value.absent(),
    this.chunkIds = const Value.absent(),
    this.gapReason = const Value.absent(),
    this.foldDueAtUtc = const Value.absent(),
    this.defaultVersionId = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  SegmentsCompanion.insert({
    required String id,
    required String sessionId,
    required int ordinal,
    required String kind,
    required String sourceFinalIds,
    required int capturedStartMs,
    required int capturedEndMs,
    required String chunkIds,
    this.gapReason = const Value.absent(),
    this.foldDueAtUtc = const Value.absent(),
    this.defaultVersionId = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       sessionId = Value(sessionId),
       ordinal = Value(ordinal),
       kind = Value(kind),
       sourceFinalIds = Value(sourceFinalIds),
       capturedStartMs = Value(capturedStartMs),
       capturedEndMs = Value(capturedEndMs),
       chunkIds = Value(chunkIds);
  static Insertable<SegmentRow> custom({
    Expression<String>? id,
    Expression<String>? sessionId,
    Expression<int>? ordinal,
    Expression<String>? kind,
    Expression<String>? sourceFinalIds,
    Expression<int>? capturedStartMs,
    Expression<int>? capturedEndMs,
    Expression<String>? chunkIds,
    Expression<String>? gapReason,
    Expression<int>? foldDueAtUtc,
    Expression<String>? defaultVersionId,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (sessionId != null) 'session_id': sessionId,
      if (ordinal != null) 'ordinal': ordinal,
      if (kind != null) 'kind': kind,
      if (sourceFinalIds != null) 'source_final_ids': sourceFinalIds,
      if (capturedStartMs != null) 'captured_start_ms': capturedStartMs,
      if (capturedEndMs != null) 'captured_end_ms': capturedEndMs,
      if (chunkIds != null) 'chunk_ids': chunkIds,
      if (gapReason != null) 'gap_reason': gapReason,
      if (foldDueAtUtc != null) 'fold_due_at_utc': foldDueAtUtc,
      if (defaultVersionId != null) 'default_version_id': defaultVersionId,
      if (rowid != null) 'rowid': rowid,
    });
  }

  SegmentsCompanion copyWith({
    Value<String>? id,
    Value<String>? sessionId,
    Value<int>? ordinal,
    Value<String>? kind,
    Value<String>? sourceFinalIds,
    Value<int>? capturedStartMs,
    Value<int>? capturedEndMs,
    Value<String>? chunkIds,
    Value<String?>? gapReason,
    Value<int?>? foldDueAtUtc,
    Value<String?>? defaultVersionId,
    Value<int>? rowid,
  }) {
    return SegmentsCompanion(
      id: id ?? this.id,
      sessionId: sessionId ?? this.sessionId,
      ordinal: ordinal ?? this.ordinal,
      kind: kind ?? this.kind,
      sourceFinalIds: sourceFinalIds ?? this.sourceFinalIds,
      capturedStartMs: capturedStartMs ?? this.capturedStartMs,
      capturedEndMs: capturedEndMs ?? this.capturedEndMs,
      chunkIds: chunkIds ?? this.chunkIds,
      gapReason: gapReason ?? this.gapReason,
      foldDueAtUtc: foldDueAtUtc ?? this.foldDueAtUtc,
      defaultVersionId: defaultVersionId ?? this.defaultVersionId,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (sessionId.present) {
      map['session_id'] = Variable<String>(sessionId.value);
    }
    if (ordinal.present) {
      map['ordinal'] = Variable<int>(ordinal.value);
    }
    if (kind.present) {
      map['kind'] = Variable<String>(kind.value);
    }
    if (sourceFinalIds.present) {
      map['source_final_ids'] = Variable<String>(sourceFinalIds.value);
    }
    if (capturedStartMs.present) {
      map['captured_start_ms'] = Variable<int>(capturedStartMs.value);
    }
    if (capturedEndMs.present) {
      map['captured_end_ms'] = Variable<int>(capturedEndMs.value);
    }
    if (chunkIds.present) {
      map['chunk_ids'] = Variable<String>(chunkIds.value);
    }
    if (gapReason.present) {
      map['gap_reason'] = Variable<String>(gapReason.value);
    }
    if (foldDueAtUtc.present) {
      map['fold_due_at_utc'] = Variable<int>(foldDueAtUtc.value);
    }
    if (defaultVersionId.present) {
      map['default_version_id'] = Variable<String>(defaultVersionId.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('SegmentsCompanion(')
          ..write('id: $id, ')
          ..write('sessionId: $sessionId, ')
          ..write('ordinal: $ordinal, ')
          ..write('kind: $kind, ')
          ..write('sourceFinalIds: $sourceFinalIds, ')
          ..write('capturedStartMs: $capturedStartMs, ')
          ..write('capturedEndMs: $capturedEndMs, ')
          ..write('chunkIds: $chunkIds, ')
          ..write('gapReason: $gapReason, ')
          ..write('foldDueAtUtc: $foldDueAtUtc, ')
          ..write('defaultVersionId: $defaultVersionId, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $VersionsTable extends Versions
    with TableInfo<$VersionsTable, VersionRow> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $VersionsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _segmentIdMeta = const VerificationMeta(
    'segmentId',
  );
  @override
  late final GeneratedColumn<String> segmentId = GeneratedColumn<String>(
    'segment_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES segments (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _kindMeta = const VerificationMeta('kind');
  @override
  late final GeneratedColumn<String> kind = GeneratedColumn<String>(
    'kind',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _layerMeta = const VerificationMeta('layer');
  @override
  late final GeneratedColumn<int> layer = GeneratedColumn<int>(
    'layer',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _text$Meta = const VerificationMeta('text\$');
  @override
  late final GeneratedColumn<String> text$ = GeneratedColumn<String>(
    'text',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _parentVersionIdMeta = const VerificationMeta(
    'parentVersionId',
  );
  @override
  late final GeneratedColumn<String> parentVersionId = GeneratedColumn<String>(
    'parent_version_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _modelIdMeta = const VerificationMeta(
    'modelId',
  );
  @override
  late final GeneratedColumn<String> modelId = GeneratedColumn<String>(
    'model_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _modelVersionMeta = const VerificationMeta(
    'modelVersion',
  );
  @override
  late final GeneratedColumn<String> modelVersion = GeneratedColumn<String>(
    'model_version',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _createdAtUtcMeta = const VerificationMeta(
    'createdAtUtc',
  );
  @override
  late final GeneratedColumn<int> createdAtUtc = GeneratedColumn<int>(
    'created_at_utc',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _finalizedAtUtcMeta = const VerificationMeta(
    'finalizedAtUtc',
  );
  @override
  late final GeneratedColumn<int> finalizedAtUtc = GeneratedColumn<int>(
    'finalized_at_utc',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _captureStartMsMeta = const VerificationMeta(
    'captureStartMs',
  );
  @override
  late final GeneratedColumn<int> captureStartMs = GeneratedColumn<int>(
    'capture_start_ms',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _captureEndMsMeta = const VerificationMeta(
    'captureEndMs',
  );
  @override
  late final GeneratedColumn<int> captureEndMs = GeneratedColumn<int>(
    'capture_end_ms',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _sourceVersionsMeta = const VerificationMeta(
    'sourceVersions',
  );
  @override
  late final GeneratedColumn<String> sourceVersions = GeneratedColumn<String>(
    'source_versions',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('[]'),
  );
  static const VerificationMeta _sourceSetHashMeta = const VerificationMeta(
    'sourceSetHash',
  );
  @override
  late final GeneratedColumn<String> sourceSetHash = GeneratedColumn<String>(
    'source_set_hash',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    segmentId,
    kind,
    layer,
    text$,
    parentVersionId,
    modelId,
    modelVersion,
    createdAtUtc,
    finalizedAtUtc,
    captureStartMs,
    captureEndMs,
    sourceVersions,
    sourceSetHash,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'versions';
  @override
  VerificationContext validateIntegrity(
    Insertable<VersionRow> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('segment_id')) {
      context.handle(
        _segmentIdMeta,
        segmentId.isAcceptableOrUnknown(data['segment_id']!, _segmentIdMeta),
      );
    } else if (isInserting) {
      context.missing(_segmentIdMeta);
    }
    if (data.containsKey('kind')) {
      context.handle(
        _kindMeta,
        kind.isAcceptableOrUnknown(data['kind']!, _kindMeta),
      );
    } else if (isInserting) {
      context.missing(_kindMeta);
    }
    if (data.containsKey('layer')) {
      context.handle(
        _layerMeta,
        layer.isAcceptableOrUnknown(data['layer']!, _layerMeta),
      );
    } else if (isInserting) {
      context.missing(_layerMeta);
    }
    if (data.containsKey('text')) {
      context.handle(
        _text$Meta,
        text$.isAcceptableOrUnknown(data['text']!, _text$Meta),
      );
    } else if (isInserting) {
      context.missing(_text$Meta);
    }
    if (data.containsKey('parent_version_id')) {
      context.handle(
        _parentVersionIdMeta,
        parentVersionId.isAcceptableOrUnknown(
          data['parent_version_id']!,
          _parentVersionIdMeta,
        ),
      );
    }
    if (data.containsKey('model_id')) {
      context.handle(
        _modelIdMeta,
        modelId.isAcceptableOrUnknown(data['model_id']!, _modelIdMeta),
      );
    }
    if (data.containsKey('model_version')) {
      context.handle(
        _modelVersionMeta,
        modelVersion.isAcceptableOrUnknown(
          data['model_version']!,
          _modelVersionMeta,
        ),
      );
    }
    if (data.containsKey('created_at_utc')) {
      context.handle(
        _createdAtUtcMeta,
        createdAtUtc.isAcceptableOrUnknown(
          data['created_at_utc']!,
          _createdAtUtcMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_createdAtUtcMeta);
    }
    if (data.containsKey('finalized_at_utc')) {
      context.handle(
        _finalizedAtUtcMeta,
        finalizedAtUtc.isAcceptableOrUnknown(
          data['finalized_at_utc']!,
          _finalizedAtUtcMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_finalizedAtUtcMeta);
    }
    if (data.containsKey('capture_start_ms')) {
      context.handle(
        _captureStartMsMeta,
        captureStartMs.isAcceptableOrUnknown(
          data['capture_start_ms']!,
          _captureStartMsMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_captureStartMsMeta);
    }
    if (data.containsKey('capture_end_ms')) {
      context.handle(
        _captureEndMsMeta,
        captureEndMs.isAcceptableOrUnknown(
          data['capture_end_ms']!,
          _captureEndMsMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_captureEndMsMeta);
    }
    if (data.containsKey('source_versions')) {
      context.handle(
        _sourceVersionsMeta,
        sourceVersions.isAcceptableOrUnknown(
          data['source_versions']!,
          _sourceVersionsMeta,
        ),
      );
    }
    if (data.containsKey('source_set_hash')) {
      context.handle(
        _sourceSetHashMeta,
        sourceSetHash.isAcceptableOrUnknown(
          data['source_set_hash']!,
          _sourceSetHashMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  VersionRow map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return VersionRow(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      segmentId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}segment_id'],
      )!,
      kind: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}kind'],
      )!,
      layer: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}layer'],
      )!,
      text$: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}text'],
      )!,
      parentVersionId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}parent_version_id'],
      ),
      modelId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}model_id'],
      ),
      modelVersion: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}model_version'],
      ),
      createdAtUtc: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}created_at_utc'],
      )!,
      finalizedAtUtc: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}finalized_at_utc'],
      )!,
      captureStartMs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}capture_start_ms'],
      )!,
      captureEndMs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}capture_end_ms'],
      )!,
      sourceVersions: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}source_versions'],
      )!,
      sourceSetHash: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}source_set_hash'],
      ),
    );
  }

  @override
  $VersionsTable createAlias(String alias) {
    return $VersionsTable(attachedDatabase, alias);
  }
}

class VersionRow extends DataClass implements Insertable<VersionRow> {
  final String id;
  final String segmentId;
  final String kind;
  final int layer;

  /// 避开 Table.text 构建器；保留 SQLite 列名与 JSON 键。
  final String text$;
  final String? parentVersionId;
  final String? modelId;
  final String? modelVersion;
  final int createdAtUtc;
  final int finalizedAtUtc;
  final int captureStartMs;
  final int captureEndMs;

  /// 有序 `SourceVersionRef[]` JSON。
  final String sourceVersions;
  final String? sourceSetHash;
  const VersionRow({
    required this.id,
    required this.segmentId,
    required this.kind,
    required this.layer,
    required this.text$,
    this.parentVersionId,
    this.modelId,
    this.modelVersion,
    required this.createdAtUtc,
    required this.finalizedAtUtc,
    required this.captureStartMs,
    required this.captureEndMs,
    required this.sourceVersions,
    this.sourceSetHash,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['segment_id'] = Variable<String>(segmentId);
    map['kind'] = Variable<String>(kind);
    map['layer'] = Variable<int>(layer);
    map['text'] = Variable<String>(text$);
    if (!nullToAbsent || parentVersionId != null) {
      map['parent_version_id'] = Variable<String>(parentVersionId);
    }
    if (!nullToAbsent || modelId != null) {
      map['model_id'] = Variable<String>(modelId);
    }
    if (!nullToAbsent || modelVersion != null) {
      map['model_version'] = Variable<String>(modelVersion);
    }
    map['created_at_utc'] = Variable<int>(createdAtUtc);
    map['finalized_at_utc'] = Variable<int>(finalizedAtUtc);
    map['capture_start_ms'] = Variable<int>(captureStartMs);
    map['capture_end_ms'] = Variable<int>(captureEndMs);
    map['source_versions'] = Variable<String>(sourceVersions);
    if (!nullToAbsent || sourceSetHash != null) {
      map['source_set_hash'] = Variable<String>(sourceSetHash);
    }
    return map;
  }

  VersionsCompanion toCompanion(bool nullToAbsent) {
    return VersionsCompanion(
      id: Value(id),
      segmentId: Value(segmentId),
      kind: Value(kind),
      layer: Value(layer),
      text$: Value(text$),
      parentVersionId: parentVersionId == null && nullToAbsent
          ? const Value.absent()
          : Value(parentVersionId),
      modelId: modelId == null && nullToAbsent
          ? const Value.absent()
          : Value(modelId),
      modelVersion: modelVersion == null && nullToAbsent
          ? const Value.absent()
          : Value(modelVersion),
      createdAtUtc: Value(createdAtUtc),
      finalizedAtUtc: Value(finalizedAtUtc),
      captureStartMs: Value(captureStartMs),
      captureEndMs: Value(captureEndMs),
      sourceVersions: Value(sourceVersions),
      sourceSetHash: sourceSetHash == null && nullToAbsent
          ? const Value.absent()
          : Value(sourceSetHash),
    );
  }

  factory VersionRow.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return VersionRow(
      id: serializer.fromJson<String>(json['id']),
      segmentId: serializer.fromJson<String>(json['segmentId']),
      kind: serializer.fromJson<String>(json['kind']),
      layer: serializer.fromJson<int>(json['layer']),
      text$: serializer.fromJson<String>(json['text']),
      parentVersionId: serializer.fromJson<String?>(json['parentVersionId']),
      modelId: serializer.fromJson<String?>(json['modelId']),
      modelVersion: serializer.fromJson<String?>(json['modelVersion']),
      createdAtUtc: serializer.fromJson<int>(json['createdAtUtc']),
      finalizedAtUtc: serializer.fromJson<int>(json['finalizedAtUtc']),
      captureStartMs: serializer.fromJson<int>(json['captureStartMs']),
      captureEndMs: serializer.fromJson<int>(json['captureEndMs']),
      sourceVersions: serializer.fromJson<String>(json['sourceVersions']),
      sourceSetHash: serializer.fromJson<String?>(json['sourceSetHash']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'segmentId': serializer.toJson<String>(segmentId),
      'kind': serializer.toJson<String>(kind),
      'layer': serializer.toJson<int>(layer),
      'text': serializer.toJson<String>(text$),
      'parentVersionId': serializer.toJson<String?>(parentVersionId),
      'modelId': serializer.toJson<String?>(modelId),
      'modelVersion': serializer.toJson<String?>(modelVersion),
      'createdAtUtc': serializer.toJson<int>(createdAtUtc),
      'finalizedAtUtc': serializer.toJson<int>(finalizedAtUtc),
      'captureStartMs': serializer.toJson<int>(captureStartMs),
      'captureEndMs': serializer.toJson<int>(captureEndMs),
      'sourceVersions': serializer.toJson<String>(sourceVersions),
      'sourceSetHash': serializer.toJson<String?>(sourceSetHash),
    };
  }

  VersionRow copyWith({
    String? id,
    String? segmentId,
    String? kind,
    int? layer,
    String? text$,
    Value<String?> parentVersionId = const Value.absent(),
    Value<String?> modelId = const Value.absent(),
    Value<String?> modelVersion = const Value.absent(),
    int? createdAtUtc,
    int? finalizedAtUtc,
    int? captureStartMs,
    int? captureEndMs,
    String? sourceVersions,
    Value<String?> sourceSetHash = const Value.absent(),
  }) => VersionRow(
    id: id ?? this.id,
    segmentId: segmentId ?? this.segmentId,
    kind: kind ?? this.kind,
    layer: layer ?? this.layer,
    text$: text$ ?? this.text$,
    parentVersionId: parentVersionId.present
        ? parentVersionId.value
        : this.parentVersionId,
    modelId: modelId.present ? modelId.value : this.modelId,
    modelVersion: modelVersion.present ? modelVersion.value : this.modelVersion,
    createdAtUtc: createdAtUtc ?? this.createdAtUtc,
    finalizedAtUtc: finalizedAtUtc ?? this.finalizedAtUtc,
    captureStartMs: captureStartMs ?? this.captureStartMs,
    captureEndMs: captureEndMs ?? this.captureEndMs,
    sourceVersions: sourceVersions ?? this.sourceVersions,
    sourceSetHash: sourceSetHash.present
        ? sourceSetHash.value
        : this.sourceSetHash,
  );
  VersionRow copyWithCompanion(VersionsCompanion data) {
    return VersionRow(
      id: data.id.present ? data.id.value : this.id,
      segmentId: data.segmentId.present ? data.segmentId.value : this.segmentId,
      kind: data.kind.present ? data.kind.value : this.kind,
      layer: data.layer.present ? data.layer.value : this.layer,
      text$: data.text$.present ? data.text$.value : this.text$,
      parentVersionId: data.parentVersionId.present
          ? data.parentVersionId.value
          : this.parentVersionId,
      modelId: data.modelId.present ? data.modelId.value : this.modelId,
      modelVersion: data.modelVersion.present
          ? data.modelVersion.value
          : this.modelVersion,
      createdAtUtc: data.createdAtUtc.present
          ? data.createdAtUtc.value
          : this.createdAtUtc,
      finalizedAtUtc: data.finalizedAtUtc.present
          ? data.finalizedAtUtc.value
          : this.finalizedAtUtc,
      captureStartMs: data.captureStartMs.present
          ? data.captureStartMs.value
          : this.captureStartMs,
      captureEndMs: data.captureEndMs.present
          ? data.captureEndMs.value
          : this.captureEndMs,
      sourceVersions: data.sourceVersions.present
          ? data.sourceVersions.value
          : this.sourceVersions,
      sourceSetHash: data.sourceSetHash.present
          ? data.sourceSetHash.value
          : this.sourceSetHash,
    );
  }

  @override
  String toString() {
    return (StringBuffer('VersionRow(')
          ..write('id: $id, ')
          ..write('segmentId: $segmentId, ')
          ..write('kind: $kind, ')
          ..write('layer: $layer, ')
          ..write('text\$: ${text$}, ')
          ..write('parentVersionId: $parentVersionId, ')
          ..write('modelId: $modelId, ')
          ..write('modelVersion: $modelVersion, ')
          ..write('createdAtUtc: $createdAtUtc, ')
          ..write('finalizedAtUtc: $finalizedAtUtc, ')
          ..write('captureStartMs: $captureStartMs, ')
          ..write('captureEndMs: $captureEndMs, ')
          ..write('sourceVersions: $sourceVersions, ')
          ..write('sourceSetHash: $sourceSetHash')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    segmentId,
    kind,
    layer,
    text$,
    parentVersionId,
    modelId,
    modelVersion,
    createdAtUtc,
    finalizedAtUtc,
    captureStartMs,
    captureEndMs,
    sourceVersions,
    sourceSetHash,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is VersionRow &&
          other.id == this.id &&
          other.segmentId == this.segmentId &&
          other.kind == this.kind &&
          other.layer == this.layer &&
          other.text$ == this.text$ &&
          other.parentVersionId == this.parentVersionId &&
          other.modelId == this.modelId &&
          other.modelVersion == this.modelVersion &&
          other.createdAtUtc == this.createdAtUtc &&
          other.finalizedAtUtc == this.finalizedAtUtc &&
          other.captureStartMs == this.captureStartMs &&
          other.captureEndMs == this.captureEndMs &&
          other.sourceVersions == this.sourceVersions &&
          other.sourceSetHash == this.sourceSetHash);
}

class VersionsCompanion extends UpdateCompanion<VersionRow> {
  final Value<String> id;
  final Value<String> segmentId;
  final Value<String> kind;
  final Value<int> layer;
  final Value<String> text$;
  final Value<String?> parentVersionId;
  final Value<String?> modelId;
  final Value<String?> modelVersion;
  final Value<int> createdAtUtc;
  final Value<int> finalizedAtUtc;
  final Value<int> captureStartMs;
  final Value<int> captureEndMs;
  final Value<String> sourceVersions;
  final Value<String?> sourceSetHash;
  final Value<int> rowid;
  const VersionsCompanion({
    this.id = const Value.absent(),
    this.segmentId = const Value.absent(),
    this.kind = const Value.absent(),
    this.layer = const Value.absent(),
    this.text$ = const Value.absent(),
    this.parentVersionId = const Value.absent(),
    this.modelId = const Value.absent(),
    this.modelVersion = const Value.absent(),
    this.createdAtUtc = const Value.absent(),
    this.finalizedAtUtc = const Value.absent(),
    this.captureStartMs = const Value.absent(),
    this.captureEndMs = const Value.absent(),
    this.sourceVersions = const Value.absent(),
    this.sourceSetHash = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  VersionsCompanion.insert({
    required String id,
    required String segmentId,
    required String kind,
    required int layer,
    required String text$,
    this.parentVersionId = const Value.absent(),
    this.modelId = const Value.absent(),
    this.modelVersion = const Value.absent(),
    required int createdAtUtc,
    required int finalizedAtUtc,
    required int captureStartMs,
    required int captureEndMs,
    this.sourceVersions = const Value.absent(),
    this.sourceSetHash = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       segmentId = Value(segmentId),
       kind = Value(kind),
       layer = Value(layer),
       text$ = Value(text$),
       createdAtUtc = Value(createdAtUtc),
       finalizedAtUtc = Value(finalizedAtUtc),
       captureStartMs = Value(captureStartMs),
       captureEndMs = Value(captureEndMs);
  static Insertable<VersionRow> custom({
    Expression<String>? id,
    Expression<String>? segmentId,
    Expression<String>? kind,
    Expression<int>? layer,
    Expression<String>? text$,
    Expression<String>? parentVersionId,
    Expression<String>? modelId,
    Expression<String>? modelVersion,
    Expression<int>? createdAtUtc,
    Expression<int>? finalizedAtUtc,
    Expression<int>? captureStartMs,
    Expression<int>? captureEndMs,
    Expression<String>? sourceVersions,
    Expression<String>? sourceSetHash,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (segmentId != null) 'segment_id': segmentId,
      if (kind != null) 'kind': kind,
      if (layer != null) 'layer': layer,
      if (text$ != null) 'text': text$,
      if (parentVersionId != null) 'parent_version_id': parentVersionId,
      if (modelId != null) 'model_id': modelId,
      if (modelVersion != null) 'model_version': modelVersion,
      if (createdAtUtc != null) 'created_at_utc': createdAtUtc,
      if (finalizedAtUtc != null) 'finalized_at_utc': finalizedAtUtc,
      if (captureStartMs != null) 'capture_start_ms': captureStartMs,
      if (captureEndMs != null) 'capture_end_ms': captureEndMs,
      if (sourceVersions != null) 'source_versions': sourceVersions,
      if (sourceSetHash != null) 'source_set_hash': sourceSetHash,
      if (rowid != null) 'rowid': rowid,
    });
  }

  VersionsCompanion copyWith({
    Value<String>? id,
    Value<String>? segmentId,
    Value<String>? kind,
    Value<int>? layer,
    Value<String>? text$,
    Value<String?>? parentVersionId,
    Value<String?>? modelId,
    Value<String?>? modelVersion,
    Value<int>? createdAtUtc,
    Value<int>? finalizedAtUtc,
    Value<int>? captureStartMs,
    Value<int>? captureEndMs,
    Value<String>? sourceVersions,
    Value<String?>? sourceSetHash,
    Value<int>? rowid,
  }) {
    return VersionsCompanion(
      id: id ?? this.id,
      segmentId: segmentId ?? this.segmentId,
      kind: kind ?? this.kind,
      layer: layer ?? this.layer,
      text$: text$ ?? this.text$,
      parentVersionId: parentVersionId ?? this.parentVersionId,
      modelId: modelId ?? this.modelId,
      modelVersion: modelVersion ?? this.modelVersion,
      createdAtUtc: createdAtUtc ?? this.createdAtUtc,
      finalizedAtUtc: finalizedAtUtc ?? this.finalizedAtUtc,
      captureStartMs: captureStartMs ?? this.captureStartMs,
      captureEndMs: captureEndMs ?? this.captureEndMs,
      sourceVersions: sourceVersions ?? this.sourceVersions,
      sourceSetHash: sourceSetHash ?? this.sourceSetHash,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (segmentId.present) {
      map['segment_id'] = Variable<String>(segmentId.value);
    }
    if (kind.present) {
      map['kind'] = Variable<String>(kind.value);
    }
    if (layer.present) {
      map['layer'] = Variable<int>(layer.value);
    }
    if (text$.present) {
      map['text'] = Variable<String>(text$.value);
    }
    if (parentVersionId.present) {
      map['parent_version_id'] = Variable<String>(parentVersionId.value);
    }
    if (modelId.present) {
      map['model_id'] = Variable<String>(modelId.value);
    }
    if (modelVersion.present) {
      map['model_version'] = Variable<String>(modelVersion.value);
    }
    if (createdAtUtc.present) {
      map['created_at_utc'] = Variable<int>(createdAtUtc.value);
    }
    if (finalizedAtUtc.present) {
      map['finalized_at_utc'] = Variable<int>(finalizedAtUtc.value);
    }
    if (captureStartMs.present) {
      map['capture_start_ms'] = Variable<int>(captureStartMs.value);
    }
    if (captureEndMs.present) {
      map['capture_end_ms'] = Variable<int>(captureEndMs.value);
    }
    if (sourceVersions.present) {
      map['source_versions'] = Variable<String>(sourceVersions.value);
    }
    if (sourceSetHash.present) {
      map['source_set_hash'] = Variable<String>(sourceSetHash.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('VersionsCompanion(')
          ..write('id: $id, ')
          ..write('segmentId: $segmentId, ')
          ..write('kind: $kind, ')
          ..write('layer: $layer, ')
          ..write('text\$: ${text$}, ')
          ..write('parentVersionId: $parentVersionId, ')
          ..write('modelId: $modelId, ')
          ..write('modelVersion: $modelVersion, ')
          ..write('createdAtUtc: $createdAtUtc, ')
          ..write('finalizedAtUtc: $finalizedAtUtc, ')
          ..write('captureStartMs: $captureStartMs, ')
          ..write('captureEndMs: $captureEndMs, ')
          ..write('sourceVersions: $sourceVersions, ')
          ..write('sourceSetHash: $sourceSetHash, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $ArtifactsTable extends Artifacts
    with TableInfo<$ArtifactsTable, ArtifactRow> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $ArtifactsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _sessionIdMeta = const VerificationMeta(
    'sessionId',
  );
  @override
  late final GeneratedColumn<String> sessionId = GeneratedColumn<String>(
    'session_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES sessions (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _segmentIdMeta = const VerificationMeta(
    'segmentId',
  );
  @override
  late final GeneratedColumn<String> segmentId = GeneratedColumn<String>(
    'segment_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES segments (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _layerMeta = const VerificationMeta('layer');
  @override
  late final GeneratedColumn<int> layer = GeneratedColumn<int>(
    'layer',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _stateMeta = const VerificationMeta('state');
  @override
  late final GeneratedColumn<String> state = GeneratedColumn<String>(
    'state',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _text$Meta = const VerificationMeta('text\$');
  @override
  late final GeneratedColumn<String> text$ = GeneratedColumn<String>(
    'text',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _sourceVersionsMeta = const VerificationMeta(
    'sourceVersions',
  );
  @override
  late final GeneratedColumn<String> sourceVersions = GeneratedColumn<String>(
    'source_versions',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _sourceSetHashMeta = const VerificationMeta(
    'sourceSetHash',
  );
  @override
  late final GeneratedColumn<String> sourceSetHash = GeneratedColumn<String>(
    'source_set_hash',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _promptVersionMeta = const VerificationMeta(
    'promptVersion',
  );
  @override
  late final GeneratedColumn<String> promptVersion = GeneratedColumn<String>(
    'prompt_version',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _modelMeta = const VerificationMeta('model');
  @override
  late final GeneratedColumn<String> model = GeneratedColumn<String>(
    'model',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _createdAtUtcMeta = const VerificationMeta(
    'createdAtUtc',
  );
  @override
  late final GeneratedColumn<int> createdAtUtc = GeneratedColumn<int>(
    'created_at_utc',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _parentVersionIdMeta = const VerificationMeta(
    'parentVersionId',
  );
  @override
  late final GeneratedColumn<String> parentVersionId = GeneratedColumn<String>(
    'parent_version_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _citationsMeta = const VerificationMeta(
    'citations',
  );
  @override
  late final GeneratedColumn<String> citations = GeneratedColumn<String>(
    'citations',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('[]'),
  );
  static const VerificationMeta _errorCategoryMeta = const VerificationMeta(
    'errorCategory',
  );
  @override
  late final GeneratedColumn<String> errorCategory = GeneratedColumn<String>(
    'error_category',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    sessionId,
    segmentId,
    layer,
    state,
    text$,
    sourceVersions,
    sourceSetHash,
    promptVersion,
    model,
    createdAtUtc,
    parentVersionId,
    citations,
    errorCategory,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'artifacts';
  @override
  VerificationContext validateIntegrity(
    Insertable<ArtifactRow> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('session_id')) {
      context.handle(
        _sessionIdMeta,
        sessionId.isAcceptableOrUnknown(data['session_id']!, _sessionIdMeta),
      );
    } else if (isInserting) {
      context.missing(_sessionIdMeta);
    }
    if (data.containsKey('segment_id')) {
      context.handle(
        _segmentIdMeta,
        segmentId.isAcceptableOrUnknown(data['segment_id']!, _segmentIdMeta),
      );
    }
    if (data.containsKey('layer')) {
      context.handle(
        _layerMeta,
        layer.isAcceptableOrUnknown(data['layer']!, _layerMeta),
      );
    } else if (isInserting) {
      context.missing(_layerMeta);
    }
    if (data.containsKey('state')) {
      context.handle(
        _stateMeta,
        state.isAcceptableOrUnknown(data['state']!, _stateMeta),
      );
    } else if (isInserting) {
      context.missing(_stateMeta);
    }
    if (data.containsKey('text')) {
      context.handle(
        _text$Meta,
        text$.isAcceptableOrUnknown(data['text']!, _text$Meta),
      );
    } else if (isInserting) {
      context.missing(_text$Meta);
    }
    if (data.containsKey('source_versions')) {
      context.handle(
        _sourceVersionsMeta,
        sourceVersions.isAcceptableOrUnknown(
          data['source_versions']!,
          _sourceVersionsMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_sourceVersionsMeta);
    }
    if (data.containsKey('source_set_hash')) {
      context.handle(
        _sourceSetHashMeta,
        sourceSetHash.isAcceptableOrUnknown(
          data['source_set_hash']!,
          _sourceSetHashMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_sourceSetHashMeta);
    }
    if (data.containsKey('prompt_version')) {
      context.handle(
        _promptVersionMeta,
        promptVersion.isAcceptableOrUnknown(
          data['prompt_version']!,
          _promptVersionMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_promptVersionMeta);
    }
    if (data.containsKey('model')) {
      context.handle(
        _modelMeta,
        model.isAcceptableOrUnknown(data['model']!, _modelMeta),
      );
    } else if (isInserting) {
      context.missing(_modelMeta);
    }
    if (data.containsKey('created_at_utc')) {
      context.handle(
        _createdAtUtcMeta,
        createdAtUtc.isAcceptableOrUnknown(
          data['created_at_utc']!,
          _createdAtUtcMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_createdAtUtcMeta);
    }
    if (data.containsKey('parent_version_id')) {
      context.handle(
        _parentVersionIdMeta,
        parentVersionId.isAcceptableOrUnknown(
          data['parent_version_id']!,
          _parentVersionIdMeta,
        ),
      );
    }
    if (data.containsKey('citations')) {
      context.handle(
        _citationsMeta,
        citations.isAcceptableOrUnknown(data['citations']!, _citationsMeta),
      );
    }
    if (data.containsKey('error_category')) {
      context.handle(
        _errorCategoryMeta,
        errorCategory.isAcceptableOrUnknown(
          data['error_category']!,
          _errorCategoryMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  ArtifactRow map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return ArtifactRow(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      sessionId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}session_id'],
      )!,
      segmentId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}segment_id'],
      ),
      layer: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}layer'],
      )!,
      state: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}state'],
      )!,
      text$: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}text'],
      )!,
      sourceVersions: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}source_versions'],
      )!,
      sourceSetHash: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}source_set_hash'],
      )!,
      promptVersion: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}prompt_version'],
      )!,
      model: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}model'],
      )!,
      createdAtUtc: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}created_at_utc'],
      )!,
      parentVersionId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}parent_version_id'],
      ),
      citations: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}citations'],
      )!,
      errorCategory: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}error_category'],
      ),
    );
  }

  @override
  $ArtifactsTable createAlias(String alias) {
    return $ArtifactsTable(attachedDatabase, alias);
  }
}

class ArtifactRow extends DataClass implements Insertable<ArtifactRow> {
  final String id;
  final String sessionId;
  final String? segmentId;
  final int layer;
  final String state;

  /// 避开 Table.text 构建器；保留 SQLite 列名与 JSON 键。
  final String text$;

  /// 有序 `SourceVersionRef[]` JSON。
  final String sourceVersions;
  final String sourceSetHash;
  final String promptVersion;
  final String model;
  final int createdAtUtc;
  final String? parentVersionId;
  final String citations;
  final String? errorCategory;
  const ArtifactRow({
    required this.id,
    required this.sessionId,
    this.segmentId,
    required this.layer,
    required this.state,
    required this.text$,
    required this.sourceVersions,
    required this.sourceSetHash,
    required this.promptVersion,
    required this.model,
    required this.createdAtUtc,
    this.parentVersionId,
    required this.citations,
    this.errorCategory,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['session_id'] = Variable<String>(sessionId);
    if (!nullToAbsent || segmentId != null) {
      map['segment_id'] = Variable<String>(segmentId);
    }
    map['layer'] = Variable<int>(layer);
    map['state'] = Variable<String>(state);
    map['text'] = Variable<String>(text$);
    map['source_versions'] = Variable<String>(sourceVersions);
    map['source_set_hash'] = Variable<String>(sourceSetHash);
    map['prompt_version'] = Variable<String>(promptVersion);
    map['model'] = Variable<String>(model);
    map['created_at_utc'] = Variable<int>(createdAtUtc);
    if (!nullToAbsent || parentVersionId != null) {
      map['parent_version_id'] = Variable<String>(parentVersionId);
    }
    map['citations'] = Variable<String>(citations);
    if (!nullToAbsent || errorCategory != null) {
      map['error_category'] = Variable<String>(errorCategory);
    }
    return map;
  }

  ArtifactsCompanion toCompanion(bool nullToAbsent) {
    return ArtifactsCompanion(
      id: Value(id),
      sessionId: Value(sessionId),
      segmentId: segmentId == null && nullToAbsent
          ? const Value.absent()
          : Value(segmentId),
      layer: Value(layer),
      state: Value(state),
      text$: Value(text$),
      sourceVersions: Value(sourceVersions),
      sourceSetHash: Value(sourceSetHash),
      promptVersion: Value(promptVersion),
      model: Value(model),
      createdAtUtc: Value(createdAtUtc),
      parentVersionId: parentVersionId == null && nullToAbsent
          ? const Value.absent()
          : Value(parentVersionId),
      citations: Value(citations),
      errorCategory: errorCategory == null && nullToAbsent
          ? const Value.absent()
          : Value(errorCategory),
    );
  }

  factory ArtifactRow.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return ArtifactRow(
      id: serializer.fromJson<String>(json['id']),
      sessionId: serializer.fromJson<String>(json['sessionId']),
      segmentId: serializer.fromJson<String?>(json['segmentId']),
      layer: serializer.fromJson<int>(json['layer']),
      state: serializer.fromJson<String>(json['state']),
      text$: serializer.fromJson<String>(json['text']),
      sourceVersions: serializer.fromJson<String>(json['sourceVersions']),
      sourceSetHash: serializer.fromJson<String>(json['sourceSetHash']),
      promptVersion: serializer.fromJson<String>(json['promptVersion']),
      model: serializer.fromJson<String>(json['model']),
      createdAtUtc: serializer.fromJson<int>(json['createdAtUtc']),
      parentVersionId: serializer.fromJson<String?>(json['parentVersionId']),
      citations: serializer.fromJson<String>(json['citations']),
      errorCategory: serializer.fromJson<String?>(json['errorCategory']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'sessionId': serializer.toJson<String>(sessionId),
      'segmentId': serializer.toJson<String?>(segmentId),
      'layer': serializer.toJson<int>(layer),
      'state': serializer.toJson<String>(state),
      'text': serializer.toJson<String>(text$),
      'sourceVersions': serializer.toJson<String>(sourceVersions),
      'sourceSetHash': serializer.toJson<String>(sourceSetHash),
      'promptVersion': serializer.toJson<String>(promptVersion),
      'model': serializer.toJson<String>(model),
      'createdAtUtc': serializer.toJson<int>(createdAtUtc),
      'parentVersionId': serializer.toJson<String?>(parentVersionId),
      'citations': serializer.toJson<String>(citations),
      'errorCategory': serializer.toJson<String?>(errorCategory),
    };
  }

  ArtifactRow copyWith({
    String? id,
    String? sessionId,
    Value<String?> segmentId = const Value.absent(),
    int? layer,
    String? state,
    String? text$,
    String? sourceVersions,
    String? sourceSetHash,
    String? promptVersion,
    String? model,
    int? createdAtUtc,
    Value<String?> parentVersionId = const Value.absent(),
    String? citations,
    Value<String?> errorCategory = const Value.absent(),
  }) => ArtifactRow(
    id: id ?? this.id,
    sessionId: sessionId ?? this.sessionId,
    segmentId: segmentId.present ? segmentId.value : this.segmentId,
    layer: layer ?? this.layer,
    state: state ?? this.state,
    text$: text$ ?? this.text$,
    sourceVersions: sourceVersions ?? this.sourceVersions,
    sourceSetHash: sourceSetHash ?? this.sourceSetHash,
    promptVersion: promptVersion ?? this.promptVersion,
    model: model ?? this.model,
    createdAtUtc: createdAtUtc ?? this.createdAtUtc,
    parentVersionId: parentVersionId.present
        ? parentVersionId.value
        : this.parentVersionId,
    citations: citations ?? this.citations,
    errorCategory: errorCategory.present
        ? errorCategory.value
        : this.errorCategory,
  );
  ArtifactRow copyWithCompanion(ArtifactsCompanion data) {
    return ArtifactRow(
      id: data.id.present ? data.id.value : this.id,
      sessionId: data.sessionId.present ? data.sessionId.value : this.sessionId,
      segmentId: data.segmentId.present ? data.segmentId.value : this.segmentId,
      layer: data.layer.present ? data.layer.value : this.layer,
      state: data.state.present ? data.state.value : this.state,
      text$: data.text$.present ? data.text$.value : this.text$,
      sourceVersions: data.sourceVersions.present
          ? data.sourceVersions.value
          : this.sourceVersions,
      sourceSetHash: data.sourceSetHash.present
          ? data.sourceSetHash.value
          : this.sourceSetHash,
      promptVersion: data.promptVersion.present
          ? data.promptVersion.value
          : this.promptVersion,
      model: data.model.present ? data.model.value : this.model,
      createdAtUtc: data.createdAtUtc.present
          ? data.createdAtUtc.value
          : this.createdAtUtc,
      parentVersionId: data.parentVersionId.present
          ? data.parentVersionId.value
          : this.parentVersionId,
      citations: data.citations.present ? data.citations.value : this.citations,
      errorCategory: data.errorCategory.present
          ? data.errorCategory.value
          : this.errorCategory,
    );
  }

  @override
  String toString() {
    return (StringBuffer('ArtifactRow(')
          ..write('id: $id, ')
          ..write('sessionId: $sessionId, ')
          ..write('segmentId: $segmentId, ')
          ..write('layer: $layer, ')
          ..write('state: $state, ')
          ..write('text\$: ${text$}, ')
          ..write('sourceVersions: $sourceVersions, ')
          ..write('sourceSetHash: $sourceSetHash, ')
          ..write('promptVersion: $promptVersion, ')
          ..write('model: $model, ')
          ..write('createdAtUtc: $createdAtUtc, ')
          ..write('parentVersionId: $parentVersionId, ')
          ..write('citations: $citations, ')
          ..write('errorCategory: $errorCategory')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    sessionId,
    segmentId,
    layer,
    state,
    text$,
    sourceVersions,
    sourceSetHash,
    promptVersion,
    model,
    createdAtUtc,
    parentVersionId,
    citations,
    errorCategory,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is ArtifactRow &&
          other.id == this.id &&
          other.sessionId == this.sessionId &&
          other.segmentId == this.segmentId &&
          other.layer == this.layer &&
          other.state == this.state &&
          other.text$ == this.text$ &&
          other.sourceVersions == this.sourceVersions &&
          other.sourceSetHash == this.sourceSetHash &&
          other.promptVersion == this.promptVersion &&
          other.model == this.model &&
          other.createdAtUtc == this.createdAtUtc &&
          other.parentVersionId == this.parentVersionId &&
          other.citations == this.citations &&
          other.errorCategory == this.errorCategory);
}

class ArtifactsCompanion extends UpdateCompanion<ArtifactRow> {
  final Value<String> id;
  final Value<String> sessionId;
  final Value<String?> segmentId;
  final Value<int> layer;
  final Value<String> state;
  final Value<String> text$;
  final Value<String> sourceVersions;
  final Value<String> sourceSetHash;
  final Value<String> promptVersion;
  final Value<String> model;
  final Value<int> createdAtUtc;
  final Value<String?> parentVersionId;
  final Value<String> citations;
  final Value<String?> errorCategory;
  final Value<int> rowid;
  const ArtifactsCompanion({
    this.id = const Value.absent(),
    this.sessionId = const Value.absent(),
    this.segmentId = const Value.absent(),
    this.layer = const Value.absent(),
    this.state = const Value.absent(),
    this.text$ = const Value.absent(),
    this.sourceVersions = const Value.absent(),
    this.sourceSetHash = const Value.absent(),
    this.promptVersion = const Value.absent(),
    this.model = const Value.absent(),
    this.createdAtUtc = const Value.absent(),
    this.parentVersionId = const Value.absent(),
    this.citations = const Value.absent(),
    this.errorCategory = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  ArtifactsCompanion.insert({
    required String id,
    required String sessionId,
    this.segmentId = const Value.absent(),
    required int layer,
    required String state,
    required String text$,
    required String sourceVersions,
    required String sourceSetHash,
    required String promptVersion,
    required String model,
    required int createdAtUtc,
    this.parentVersionId = const Value.absent(),
    this.citations = const Value.absent(),
    this.errorCategory = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       sessionId = Value(sessionId),
       layer = Value(layer),
       state = Value(state),
       text$ = Value(text$),
       sourceVersions = Value(sourceVersions),
       sourceSetHash = Value(sourceSetHash),
       promptVersion = Value(promptVersion),
       model = Value(model),
       createdAtUtc = Value(createdAtUtc);
  static Insertable<ArtifactRow> custom({
    Expression<String>? id,
    Expression<String>? sessionId,
    Expression<String>? segmentId,
    Expression<int>? layer,
    Expression<String>? state,
    Expression<String>? text$,
    Expression<String>? sourceVersions,
    Expression<String>? sourceSetHash,
    Expression<String>? promptVersion,
    Expression<String>? model,
    Expression<int>? createdAtUtc,
    Expression<String>? parentVersionId,
    Expression<String>? citations,
    Expression<String>? errorCategory,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (sessionId != null) 'session_id': sessionId,
      if (segmentId != null) 'segment_id': segmentId,
      if (layer != null) 'layer': layer,
      if (state != null) 'state': state,
      if (text$ != null) 'text': text$,
      if (sourceVersions != null) 'source_versions': sourceVersions,
      if (sourceSetHash != null) 'source_set_hash': sourceSetHash,
      if (promptVersion != null) 'prompt_version': promptVersion,
      if (model != null) 'model': model,
      if (createdAtUtc != null) 'created_at_utc': createdAtUtc,
      if (parentVersionId != null) 'parent_version_id': parentVersionId,
      if (citations != null) 'citations': citations,
      if (errorCategory != null) 'error_category': errorCategory,
      if (rowid != null) 'rowid': rowid,
    });
  }

  ArtifactsCompanion copyWith({
    Value<String>? id,
    Value<String>? sessionId,
    Value<String?>? segmentId,
    Value<int>? layer,
    Value<String>? state,
    Value<String>? text$,
    Value<String>? sourceVersions,
    Value<String>? sourceSetHash,
    Value<String>? promptVersion,
    Value<String>? model,
    Value<int>? createdAtUtc,
    Value<String?>? parentVersionId,
    Value<String>? citations,
    Value<String?>? errorCategory,
    Value<int>? rowid,
  }) {
    return ArtifactsCompanion(
      id: id ?? this.id,
      sessionId: sessionId ?? this.sessionId,
      segmentId: segmentId ?? this.segmentId,
      layer: layer ?? this.layer,
      state: state ?? this.state,
      text$: text$ ?? this.text$,
      sourceVersions: sourceVersions ?? this.sourceVersions,
      sourceSetHash: sourceSetHash ?? this.sourceSetHash,
      promptVersion: promptVersion ?? this.promptVersion,
      model: model ?? this.model,
      createdAtUtc: createdAtUtc ?? this.createdAtUtc,
      parentVersionId: parentVersionId ?? this.parentVersionId,
      citations: citations ?? this.citations,
      errorCategory: errorCategory ?? this.errorCategory,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (sessionId.present) {
      map['session_id'] = Variable<String>(sessionId.value);
    }
    if (segmentId.present) {
      map['segment_id'] = Variable<String>(segmentId.value);
    }
    if (layer.present) {
      map['layer'] = Variable<int>(layer.value);
    }
    if (state.present) {
      map['state'] = Variable<String>(state.value);
    }
    if (text$.present) {
      map['text'] = Variable<String>(text$.value);
    }
    if (sourceVersions.present) {
      map['source_versions'] = Variable<String>(sourceVersions.value);
    }
    if (sourceSetHash.present) {
      map['source_set_hash'] = Variable<String>(sourceSetHash.value);
    }
    if (promptVersion.present) {
      map['prompt_version'] = Variable<String>(promptVersion.value);
    }
    if (model.present) {
      map['model'] = Variable<String>(model.value);
    }
    if (createdAtUtc.present) {
      map['created_at_utc'] = Variable<int>(createdAtUtc.value);
    }
    if (parentVersionId.present) {
      map['parent_version_id'] = Variable<String>(parentVersionId.value);
    }
    if (citations.present) {
      map['citations'] = Variable<String>(citations.value);
    }
    if (errorCategory.present) {
      map['error_category'] = Variable<String>(errorCategory.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('ArtifactsCompanion(')
          ..write('id: $id, ')
          ..write('sessionId: $sessionId, ')
          ..write('segmentId: $segmentId, ')
          ..write('layer: $layer, ')
          ..write('state: $state, ')
          ..write('text\$: ${text$}, ')
          ..write('sourceVersions: $sourceVersions, ')
          ..write('sourceSetHash: $sourceSetHash, ')
          ..write('promptVersion: $promptVersion, ')
          ..write('model: $model, ')
          ..write('createdAtUtc: $createdAtUtc, ')
          ..write('parentVersionId: $parentVersionId, ')
          ..write('citations: $citations, ')
          ..write('errorCategory: $errorCategory, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $JobsTable extends Jobs with TableInfo<$JobsTable, JobRow> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $JobsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _idempotencyKeyMeta = const VerificationMeta(
    'idempotencyKey',
  );
  @override
  late final GeneratedColumn<String> idempotencyKey = GeneratedColumn<String>(
    'idempotency_key',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways('UNIQUE'),
  );
  static const VerificationMeta _sessionIdMeta = const VerificationMeta(
    'sessionId',
  );
  @override
  late final GeneratedColumn<String> sessionId = GeneratedColumn<String>(
    'session_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES sessions (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _targetIdMeta = const VerificationMeta(
    'targetId',
  );
  @override
  late final GeneratedColumn<String> targetId = GeneratedColumn<String>(
    'target_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _layerMeta = const VerificationMeta('layer');
  @override
  late final GeneratedColumn<int> layer = GeneratedColumn<int>(
    'layer',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _stateMeta = const VerificationMeta('state');
  @override
  late final GeneratedColumn<String> state = GeneratedColumn<String>(
    'state',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _sourceVersionsMeta = const VerificationMeta(
    'sourceVersions',
  );
  @override
  late final GeneratedColumn<String> sourceVersions = GeneratedColumn<String>(
    'source_versions',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _sourceSetHashMeta = const VerificationMeta(
    'sourceSetHash',
  );
  @override
  late final GeneratedColumn<String> sourceSetHash = GeneratedColumn<String>(
    'source_set_hash',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _promptVersionMeta = const VerificationMeta(
    'promptVersion',
  );
  @override
  late final GeneratedColumn<String> promptVersion = GeneratedColumn<String>(
    'prompt_version',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _modelMeta = const VerificationMeta('model');
  @override
  late final GeneratedColumn<String> model = GeneratedColumn<String>(
    'model',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _createdAtUtcMeta = const VerificationMeta(
    'createdAtUtc',
  );
  @override
  late final GeneratedColumn<int> createdAtUtc = GeneratedColumn<int>(
    'created_at_utc',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _updatedAtUtcMeta = const VerificationMeta(
    'updatedAtUtc',
  );
  @override
  late final GeneratedColumn<int> updatedAtUtc = GeneratedColumn<int>(
    'updated_at_utc',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _attemptsMeta = const VerificationMeta(
    'attempts',
  );
  @override
  late final GeneratedColumn<int> attempts = GeneratedColumn<int>(
    'attempts',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _errorCategoryMeta = const VerificationMeta(
    'errorCategory',
  );
  @override
  late final GeneratedColumn<String> errorCategory = GeneratedColumn<String>(
    'error_category',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    idempotencyKey,
    sessionId,
    targetId,
    layer,
    state,
    sourceVersions,
    sourceSetHash,
    promptVersion,
    model,
    createdAtUtc,
    updatedAtUtc,
    attempts,
    errorCategory,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'jobs';
  @override
  VerificationContext validateIntegrity(
    Insertable<JobRow> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('idempotency_key')) {
      context.handle(
        _idempotencyKeyMeta,
        idempotencyKey.isAcceptableOrUnknown(
          data['idempotency_key']!,
          _idempotencyKeyMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_idempotencyKeyMeta);
    }
    if (data.containsKey('session_id')) {
      context.handle(
        _sessionIdMeta,
        sessionId.isAcceptableOrUnknown(data['session_id']!, _sessionIdMeta),
      );
    } else if (isInserting) {
      context.missing(_sessionIdMeta);
    }
    if (data.containsKey('target_id')) {
      context.handle(
        _targetIdMeta,
        targetId.isAcceptableOrUnknown(data['target_id']!, _targetIdMeta),
      );
    } else if (isInserting) {
      context.missing(_targetIdMeta);
    }
    if (data.containsKey('layer')) {
      context.handle(
        _layerMeta,
        layer.isAcceptableOrUnknown(data['layer']!, _layerMeta),
      );
    } else if (isInserting) {
      context.missing(_layerMeta);
    }
    if (data.containsKey('state')) {
      context.handle(
        _stateMeta,
        state.isAcceptableOrUnknown(data['state']!, _stateMeta),
      );
    } else if (isInserting) {
      context.missing(_stateMeta);
    }
    if (data.containsKey('source_versions')) {
      context.handle(
        _sourceVersionsMeta,
        sourceVersions.isAcceptableOrUnknown(
          data['source_versions']!,
          _sourceVersionsMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_sourceVersionsMeta);
    }
    if (data.containsKey('source_set_hash')) {
      context.handle(
        _sourceSetHashMeta,
        sourceSetHash.isAcceptableOrUnknown(
          data['source_set_hash']!,
          _sourceSetHashMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_sourceSetHashMeta);
    }
    if (data.containsKey('prompt_version')) {
      context.handle(
        _promptVersionMeta,
        promptVersion.isAcceptableOrUnknown(
          data['prompt_version']!,
          _promptVersionMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_promptVersionMeta);
    }
    if (data.containsKey('model')) {
      context.handle(
        _modelMeta,
        model.isAcceptableOrUnknown(data['model']!, _modelMeta),
      );
    } else if (isInserting) {
      context.missing(_modelMeta);
    }
    if (data.containsKey('created_at_utc')) {
      context.handle(
        _createdAtUtcMeta,
        createdAtUtc.isAcceptableOrUnknown(
          data['created_at_utc']!,
          _createdAtUtcMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_createdAtUtcMeta);
    }
    if (data.containsKey('updated_at_utc')) {
      context.handle(
        _updatedAtUtcMeta,
        updatedAtUtc.isAcceptableOrUnknown(
          data['updated_at_utc']!,
          _updatedAtUtcMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_updatedAtUtcMeta);
    }
    if (data.containsKey('attempts')) {
      context.handle(
        _attemptsMeta,
        attempts.isAcceptableOrUnknown(data['attempts']!, _attemptsMeta),
      );
    }
    if (data.containsKey('error_category')) {
      context.handle(
        _errorCategoryMeta,
        errorCategory.isAcceptableOrUnknown(
          data['error_category']!,
          _errorCategoryMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  JobRow map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return JobRow(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      idempotencyKey: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}idempotency_key'],
      )!,
      sessionId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}session_id'],
      )!,
      targetId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}target_id'],
      )!,
      layer: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}layer'],
      )!,
      state: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}state'],
      )!,
      sourceVersions: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}source_versions'],
      )!,
      sourceSetHash: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}source_set_hash'],
      )!,
      promptVersion: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}prompt_version'],
      )!,
      model: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}model'],
      )!,
      createdAtUtc: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}created_at_utc'],
      )!,
      updatedAtUtc: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}updated_at_utc'],
      )!,
      attempts: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}attempts'],
      )!,
      errorCategory: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}error_category'],
      ),
    );
  }

  @override
  $JobsTable createAlias(String alias) {
    return $JobsTable(attachedDatabase, alias);
  }
}

class JobRow extends DataClass implements Insertable<JobRow> {
  final String id;
  final String idempotencyKey;
  final String sessionId;
  final String targetId;
  final int layer;
  final String state;

  /// 有序 `SourceVersionRef[]` JSON。
  final String sourceVersions;
  final String sourceSetHash;
  final String promptVersion;
  final String model;
  final int createdAtUtc;
  final int updatedAtUtc;
  final int attempts;
  final String? errorCategory;
  const JobRow({
    required this.id,
    required this.idempotencyKey,
    required this.sessionId,
    required this.targetId,
    required this.layer,
    required this.state,
    required this.sourceVersions,
    required this.sourceSetHash,
    required this.promptVersion,
    required this.model,
    required this.createdAtUtc,
    required this.updatedAtUtc,
    required this.attempts,
    this.errorCategory,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['idempotency_key'] = Variable<String>(idempotencyKey);
    map['session_id'] = Variable<String>(sessionId);
    map['target_id'] = Variable<String>(targetId);
    map['layer'] = Variable<int>(layer);
    map['state'] = Variable<String>(state);
    map['source_versions'] = Variable<String>(sourceVersions);
    map['source_set_hash'] = Variable<String>(sourceSetHash);
    map['prompt_version'] = Variable<String>(promptVersion);
    map['model'] = Variable<String>(model);
    map['created_at_utc'] = Variable<int>(createdAtUtc);
    map['updated_at_utc'] = Variable<int>(updatedAtUtc);
    map['attempts'] = Variable<int>(attempts);
    if (!nullToAbsent || errorCategory != null) {
      map['error_category'] = Variable<String>(errorCategory);
    }
    return map;
  }

  JobsCompanion toCompanion(bool nullToAbsent) {
    return JobsCompanion(
      id: Value(id),
      idempotencyKey: Value(idempotencyKey),
      sessionId: Value(sessionId),
      targetId: Value(targetId),
      layer: Value(layer),
      state: Value(state),
      sourceVersions: Value(sourceVersions),
      sourceSetHash: Value(sourceSetHash),
      promptVersion: Value(promptVersion),
      model: Value(model),
      createdAtUtc: Value(createdAtUtc),
      updatedAtUtc: Value(updatedAtUtc),
      attempts: Value(attempts),
      errorCategory: errorCategory == null && nullToAbsent
          ? const Value.absent()
          : Value(errorCategory),
    );
  }

  factory JobRow.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return JobRow(
      id: serializer.fromJson<String>(json['id']),
      idempotencyKey: serializer.fromJson<String>(json['idempotencyKey']),
      sessionId: serializer.fromJson<String>(json['sessionId']),
      targetId: serializer.fromJson<String>(json['targetId']),
      layer: serializer.fromJson<int>(json['layer']),
      state: serializer.fromJson<String>(json['state']),
      sourceVersions: serializer.fromJson<String>(json['sourceVersions']),
      sourceSetHash: serializer.fromJson<String>(json['sourceSetHash']),
      promptVersion: serializer.fromJson<String>(json['promptVersion']),
      model: serializer.fromJson<String>(json['model']),
      createdAtUtc: serializer.fromJson<int>(json['createdAtUtc']),
      updatedAtUtc: serializer.fromJson<int>(json['updatedAtUtc']),
      attempts: serializer.fromJson<int>(json['attempts']),
      errorCategory: serializer.fromJson<String?>(json['errorCategory']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'idempotencyKey': serializer.toJson<String>(idempotencyKey),
      'sessionId': serializer.toJson<String>(sessionId),
      'targetId': serializer.toJson<String>(targetId),
      'layer': serializer.toJson<int>(layer),
      'state': serializer.toJson<String>(state),
      'sourceVersions': serializer.toJson<String>(sourceVersions),
      'sourceSetHash': serializer.toJson<String>(sourceSetHash),
      'promptVersion': serializer.toJson<String>(promptVersion),
      'model': serializer.toJson<String>(model),
      'createdAtUtc': serializer.toJson<int>(createdAtUtc),
      'updatedAtUtc': serializer.toJson<int>(updatedAtUtc),
      'attempts': serializer.toJson<int>(attempts),
      'errorCategory': serializer.toJson<String?>(errorCategory),
    };
  }

  JobRow copyWith({
    String? id,
    String? idempotencyKey,
    String? sessionId,
    String? targetId,
    int? layer,
    String? state,
    String? sourceVersions,
    String? sourceSetHash,
    String? promptVersion,
    String? model,
    int? createdAtUtc,
    int? updatedAtUtc,
    int? attempts,
    Value<String?> errorCategory = const Value.absent(),
  }) => JobRow(
    id: id ?? this.id,
    idempotencyKey: idempotencyKey ?? this.idempotencyKey,
    sessionId: sessionId ?? this.sessionId,
    targetId: targetId ?? this.targetId,
    layer: layer ?? this.layer,
    state: state ?? this.state,
    sourceVersions: sourceVersions ?? this.sourceVersions,
    sourceSetHash: sourceSetHash ?? this.sourceSetHash,
    promptVersion: promptVersion ?? this.promptVersion,
    model: model ?? this.model,
    createdAtUtc: createdAtUtc ?? this.createdAtUtc,
    updatedAtUtc: updatedAtUtc ?? this.updatedAtUtc,
    attempts: attempts ?? this.attempts,
    errorCategory: errorCategory.present
        ? errorCategory.value
        : this.errorCategory,
  );
  JobRow copyWithCompanion(JobsCompanion data) {
    return JobRow(
      id: data.id.present ? data.id.value : this.id,
      idempotencyKey: data.idempotencyKey.present
          ? data.idempotencyKey.value
          : this.idempotencyKey,
      sessionId: data.sessionId.present ? data.sessionId.value : this.sessionId,
      targetId: data.targetId.present ? data.targetId.value : this.targetId,
      layer: data.layer.present ? data.layer.value : this.layer,
      state: data.state.present ? data.state.value : this.state,
      sourceVersions: data.sourceVersions.present
          ? data.sourceVersions.value
          : this.sourceVersions,
      sourceSetHash: data.sourceSetHash.present
          ? data.sourceSetHash.value
          : this.sourceSetHash,
      promptVersion: data.promptVersion.present
          ? data.promptVersion.value
          : this.promptVersion,
      model: data.model.present ? data.model.value : this.model,
      createdAtUtc: data.createdAtUtc.present
          ? data.createdAtUtc.value
          : this.createdAtUtc,
      updatedAtUtc: data.updatedAtUtc.present
          ? data.updatedAtUtc.value
          : this.updatedAtUtc,
      attempts: data.attempts.present ? data.attempts.value : this.attempts,
      errorCategory: data.errorCategory.present
          ? data.errorCategory.value
          : this.errorCategory,
    );
  }

  @override
  String toString() {
    return (StringBuffer('JobRow(')
          ..write('id: $id, ')
          ..write('idempotencyKey: $idempotencyKey, ')
          ..write('sessionId: $sessionId, ')
          ..write('targetId: $targetId, ')
          ..write('layer: $layer, ')
          ..write('state: $state, ')
          ..write('sourceVersions: $sourceVersions, ')
          ..write('sourceSetHash: $sourceSetHash, ')
          ..write('promptVersion: $promptVersion, ')
          ..write('model: $model, ')
          ..write('createdAtUtc: $createdAtUtc, ')
          ..write('updatedAtUtc: $updatedAtUtc, ')
          ..write('attempts: $attempts, ')
          ..write('errorCategory: $errorCategory')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    idempotencyKey,
    sessionId,
    targetId,
    layer,
    state,
    sourceVersions,
    sourceSetHash,
    promptVersion,
    model,
    createdAtUtc,
    updatedAtUtc,
    attempts,
    errorCategory,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is JobRow &&
          other.id == this.id &&
          other.idempotencyKey == this.idempotencyKey &&
          other.sessionId == this.sessionId &&
          other.targetId == this.targetId &&
          other.layer == this.layer &&
          other.state == this.state &&
          other.sourceVersions == this.sourceVersions &&
          other.sourceSetHash == this.sourceSetHash &&
          other.promptVersion == this.promptVersion &&
          other.model == this.model &&
          other.createdAtUtc == this.createdAtUtc &&
          other.updatedAtUtc == this.updatedAtUtc &&
          other.attempts == this.attempts &&
          other.errorCategory == this.errorCategory);
}

class JobsCompanion extends UpdateCompanion<JobRow> {
  final Value<String> id;
  final Value<String> idempotencyKey;
  final Value<String> sessionId;
  final Value<String> targetId;
  final Value<int> layer;
  final Value<String> state;
  final Value<String> sourceVersions;
  final Value<String> sourceSetHash;
  final Value<String> promptVersion;
  final Value<String> model;
  final Value<int> createdAtUtc;
  final Value<int> updatedAtUtc;
  final Value<int> attempts;
  final Value<String?> errorCategory;
  final Value<int> rowid;
  const JobsCompanion({
    this.id = const Value.absent(),
    this.idempotencyKey = const Value.absent(),
    this.sessionId = const Value.absent(),
    this.targetId = const Value.absent(),
    this.layer = const Value.absent(),
    this.state = const Value.absent(),
    this.sourceVersions = const Value.absent(),
    this.sourceSetHash = const Value.absent(),
    this.promptVersion = const Value.absent(),
    this.model = const Value.absent(),
    this.createdAtUtc = const Value.absent(),
    this.updatedAtUtc = const Value.absent(),
    this.attempts = const Value.absent(),
    this.errorCategory = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  JobsCompanion.insert({
    required String id,
    required String idempotencyKey,
    required String sessionId,
    required String targetId,
    required int layer,
    required String state,
    required String sourceVersions,
    required String sourceSetHash,
    required String promptVersion,
    required String model,
    required int createdAtUtc,
    required int updatedAtUtc,
    this.attempts = const Value.absent(),
    this.errorCategory = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       idempotencyKey = Value(idempotencyKey),
       sessionId = Value(sessionId),
       targetId = Value(targetId),
       layer = Value(layer),
       state = Value(state),
       sourceVersions = Value(sourceVersions),
       sourceSetHash = Value(sourceSetHash),
       promptVersion = Value(promptVersion),
       model = Value(model),
       createdAtUtc = Value(createdAtUtc),
       updatedAtUtc = Value(updatedAtUtc);
  static Insertable<JobRow> custom({
    Expression<String>? id,
    Expression<String>? idempotencyKey,
    Expression<String>? sessionId,
    Expression<String>? targetId,
    Expression<int>? layer,
    Expression<String>? state,
    Expression<String>? sourceVersions,
    Expression<String>? sourceSetHash,
    Expression<String>? promptVersion,
    Expression<String>? model,
    Expression<int>? createdAtUtc,
    Expression<int>? updatedAtUtc,
    Expression<int>? attempts,
    Expression<String>? errorCategory,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (idempotencyKey != null) 'idempotency_key': idempotencyKey,
      if (sessionId != null) 'session_id': sessionId,
      if (targetId != null) 'target_id': targetId,
      if (layer != null) 'layer': layer,
      if (state != null) 'state': state,
      if (sourceVersions != null) 'source_versions': sourceVersions,
      if (sourceSetHash != null) 'source_set_hash': sourceSetHash,
      if (promptVersion != null) 'prompt_version': promptVersion,
      if (model != null) 'model': model,
      if (createdAtUtc != null) 'created_at_utc': createdAtUtc,
      if (updatedAtUtc != null) 'updated_at_utc': updatedAtUtc,
      if (attempts != null) 'attempts': attempts,
      if (errorCategory != null) 'error_category': errorCategory,
      if (rowid != null) 'rowid': rowid,
    });
  }

  JobsCompanion copyWith({
    Value<String>? id,
    Value<String>? idempotencyKey,
    Value<String>? sessionId,
    Value<String>? targetId,
    Value<int>? layer,
    Value<String>? state,
    Value<String>? sourceVersions,
    Value<String>? sourceSetHash,
    Value<String>? promptVersion,
    Value<String>? model,
    Value<int>? createdAtUtc,
    Value<int>? updatedAtUtc,
    Value<int>? attempts,
    Value<String?>? errorCategory,
    Value<int>? rowid,
  }) {
    return JobsCompanion(
      id: id ?? this.id,
      idempotencyKey: idempotencyKey ?? this.idempotencyKey,
      sessionId: sessionId ?? this.sessionId,
      targetId: targetId ?? this.targetId,
      layer: layer ?? this.layer,
      state: state ?? this.state,
      sourceVersions: sourceVersions ?? this.sourceVersions,
      sourceSetHash: sourceSetHash ?? this.sourceSetHash,
      promptVersion: promptVersion ?? this.promptVersion,
      model: model ?? this.model,
      createdAtUtc: createdAtUtc ?? this.createdAtUtc,
      updatedAtUtc: updatedAtUtc ?? this.updatedAtUtc,
      attempts: attempts ?? this.attempts,
      errorCategory: errorCategory ?? this.errorCategory,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (idempotencyKey.present) {
      map['idempotency_key'] = Variable<String>(idempotencyKey.value);
    }
    if (sessionId.present) {
      map['session_id'] = Variable<String>(sessionId.value);
    }
    if (targetId.present) {
      map['target_id'] = Variable<String>(targetId.value);
    }
    if (layer.present) {
      map['layer'] = Variable<int>(layer.value);
    }
    if (state.present) {
      map['state'] = Variable<String>(state.value);
    }
    if (sourceVersions.present) {
      map['source_versions'] = Variable<String>(sourceVersions.value);
    }
    if (sourceSetHash.present) {
      map['source_set_hash'] = Variable<String>(sourceSetHash.value);
    }
    if (promptVersion.present) {
      map['prompt_version'] = Variable<String>(promptVersion.value);
    }
    if (model.present) {
      map['model'] = Variable<String>(model.value);
    }
    if (createdAtUtc.present) {
      map['created_at_utc'] = Variable<int>(createdAtUtc.value);
    }
    if (updatedAtUtc.present) {
      map['updated_at_utc'] = Variable<int>(updatedAtUtc.value);
    }
    if (attempts.present) {
      map['attempts'] = Variable<int>(attempts.value);
    }
    if (errorCategory.present) {
      map['error_category'] = Variable<String>(errorCategory.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('JobsCompanion(')
          ..write('id: $id, ')
          ..write('idempotencyKey: $idempotencyKey, ')
          ..write('sessionId: $sessionId, ')
          ..write('targetId: $targetId, ')
          ..write('layer: $layer, ')
          ..write('state: $state, ')
          ..write('sourceVersions: $sourceVersions, ')
          ..write('sourceSetHash: $sourceSetHash, ')
          ..write('promptVersion: $promptVersion, ')
          ..write('model: $model, ')
          ..write('createdAtUtc: $createdAtUtc, ')
          ..write('updatedAtUtc: $updatedAtUtc, ')
          ..write('attempts: $attempts, ')
          ..write('errorCategory: $errorCategory, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $TitlesTable extends Titles with TableInfo<$TitlesTable, TitleRow> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $TitlesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _sessionIdMeta = const VerificationMeta(
    'sessionId',
  );
  @override
  late final GeneratedColumn<String> sessionId = GeneratedColumn<String>(
    'session_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES sessions (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _placeholderMeta = const VerificationMeta(
    'placeholder',
  );
  @override
  late final GeneratedColumn<String> placeholder = GeneratedColumn<String>(
    'placeholder',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _pendingFirstRevealMeta =
      const VerificationMeta('pendingFirstReveal');
  @override
  late final GeneratedColumn<bool> pendingFirstReveal = GeneratedColumn<bool>(
    'pending_first_reveal',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("pending_first_reveal" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _latestTitleMeta = const VerificationMeta(
    'latestTitle',
  );
  @override
  late final GeneratedColumn<String> latestTitle = GeneratedColumn<String>(
    'latest_title',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _latestVersionIdMeta = const VerificationMeta(
    'latestVersionId',
  );
  @override
  late final GeneratedColumn<String> latestVersionId = GeneratedColumn<String>(
    'latest_version_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _revealedAtUtcMeta = const VerificationMeta(
    'revealedAtUtc',
  );
  @override
  late final GeneratedColumn<int> revealedAtUtc = GeneratedColumn<int>(
    'revealed_at_utc',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _sourceVersionsMeta = const VerificationMeta(
    'sourceVersions',
  );
  @override
  late final GeneratedColumn<String> sourceVersions = GeneratedColumn<String>(
    'source_versions',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _sourceSetHashMeta = const VerificationMeta(
    'sourceSetHash',
  );
  @override
  late final GeneratedColumn<String> sourceSetHash = GeneratedColumn<String>(
    'source_set_hash',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _generatedAtUtcMeta = const VerificationMeta(
    'generatedAtUtc',
  );
  @override
  late final GeneratedColumn<int> generatedAtUtc = GeneratedColumn<int>(
    'generated_at_utc',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    sessionId,
    placeholder,
    pendingFirstReveal,
    latestTitle,
    latestVersionId,
    revealedAtUtc,
    sourceVersions,
    sourceSetHash,
    generatedAtUtc,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'titles';
  @override
  VerificationContext validateIntegrity(
    Insertable<TitleRow> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('session_id')) {
      context.handle(
        _sessionIdMeta,
        sessionId.isAcceptableOrUnknown(data['session_id']!, _sessionIdMeta),
      );
    } else if (isInserting) {
      context.missing(_sessionIdMeta);
    }
    if (data.containsKey('placeholder')) {
      context.handle(
        _placeholderMeta,
        placeholder.isAcceptableOrUnknown(
          data['placeholder']!,
          _placeholderMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_placeholderMeta);
    }
    if (data.containsKey('pending_first_reveal')) {
      context.handle(
        _pendingFirstRevealMeta,
        pendingFirstReveal.isAcceptableOrUnknown(
          data['pending_first_reveal']!,
          _pendingFirstRevealMeta,
        ),
      );
    }
    if (data.containsKey('latest_title')) {
      context.handle(
        _latestTitleMeta,
        latestTitle.isAcceptableOrUnknown(
          data['latest_title']!,
          _latestTitleMeta,
        ),
      );
    }
    if (data.containsKey('latest_version_id')) {
      context.handle(
        _latestVersionIdMeta,
        latestVersionId.isAcceptableOrUnknown(
          data['latest_version_id']!,
          _latestVersionIdMeta,
        ),
      );
    }
    if (data.containsKey('revealed_at_utc')) {
      context.handle(
        _revealedAtUtcMeta,
        revealedAtUtc.isAcceptableOrUnknown(
          data['revealed_at_utc']!,
          _revealedAtUtcMeta,
        ),
      );
    }
    if (data.containsKey('source_versions')) {
      context.handle(
        _sourceVersionsMeta,
        sourceVersions.isAcceptableOrUnknown(
          data['source_versions']!,
          _sourceVersionsMeta,
        ),
      );
    }
    if (data.containsKey('source_set_hash')) {
      context.handle(
        _sourceSetHashMeta,
        sourceSetHash.isAcceptableOrUnknown(
          data['source_set_hash']!,
          _sourceSetHashMeta,
        ),
      );
    }
    if (data.containsKey('generated_at_utc')) {
      context.handle(
        _generatedAtUtcMeta,
        generatedAtUtc.isAcceptableOrUnknown(
          data['generated_at_utc']!,
          _generatedAtUtcMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {sessionId};
  @override
  TitleRow map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return TitleRow(
      sessionId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}session_id'],
      )!,
      placeholder: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}placeholder'],
      )!,
      pendingFirstReveal: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}pending_first_reveal'],
      )!,
      latestTitle: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}latest_title'],
      ),
      latestVersionId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}latest_version_id'],
      ),
      revealedAtUtc: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}revealed_at_utc'],
      ),
      sourceVersions: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}source_versions'],
      ),
      sourceSetHash: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}source_set_hash'],
      ),
      generatedAtUtc: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}generated_at_utc'],
      ),
    );
  }

  @override
  $TitlesTable createAlias(String alias) {
    return $TitlesTable(attachedDatabase, alias);
  }
}

class TitleRow extends DataClass implements Insertable<TitleRow> {
  final String sessionId;
  final String placeholder;
  final bool pendingFirstReveal;
  final String? latestTitle;
  final String? latestVersionId;
  final int? revealedAtUtc;

  /// 有序 `SourceVersionRef[]` JSON；尚未生成标题时为 NULL。
  final String? sourceVersions;
  final String? sourceSetHash;
  final int? generatedAtUtc;
  const TitleRow({
    required this.sessionId,
    required this.placeholder,
    required this.pendingFirstReveal,
    this.latestTitle,
    this.latestVersionId,
    this.revealedAtUtc,
    this.sourceVersions,
    this.sourceSetHash,
    this.generatedAtUtc,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['session_id'] = Variable<String>(sessionId);
    map['placeholder'] = Variable<String>(placeholder);
    map['pending_first_reveal'] = Variable<bool>(pendingFirstReveal);
    if (!nullToAbsent || latestTitle != null) {
      map['latest_title'] = Variable<String>(latestTitle);
    }
    if (!nullToAbsent || latestVersionId != null) {
      map['latest_version_id'] = Variable<String>(latestVersionId);
    }
    if (!nullToAbsent || revealedAtUtc != null) {
      map['revealed_at_utc'] = Variable<int>(revealedAtUtc);
    }
    if (!nullToAbsent || sourceVersions != null) {
      map['source_versions'] = Variable<String>(sourceVersions);
    }
    if (!nullToAbsent || sourceSetHash != null) {
      map['source_set_hash'] = Variable<String>(sourceSetHash);
    }
    if (!nullToAbsent || generatedAtUtc != null) {
      map['generated_at_utc'] = Variable<int>(generatedAtUtc);
    }
    return map;
  }

  TitlesCompanion toCompanion(bool nullToAbsent) {
    return TitlesCompanion(
      sessionId: Value(sessionId),
      placeholder: Value(placeholder),
      pendingFirstReveal: Value(pendingFirstReveal),
      latestTitle: latestTitle == null && nullToAbsent
          ? const Value.absent()
          : Value(latestTitle),
      latestVersionId: latestVersionId == null && nullToAbsent
          ? const Value.absent()
          : Value(latestVersionId),
      revealedAtUtc: revealedAtUtc == null && nullToAbsent
          ? const Value.absent()
          : Value(revealedAtUtc),
      sourceVersions: sourceVersions == null && nullToAbsent
          ? const Value.absent()
          : Value(sourceVersions),
      sourceSetHash: sourceSetHash == null && nullToAbsent
          ? const Value.absent()
          : Value(sourceSetHash),
      generatedAtUtc: generatedAtUtc == null && nullToAbsent
          ? const Value.absent()
          : Value(generatedAtUtc),
    );
  }

  factory TitleRow.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return TitleRow(
      sessionId: serializer.fromJson<String>(json['sessionId']),
      placeholder: serializer.fromJson<String>(json['placeholder']),
      pendingFirstReveal: serializer.fromJson<bool>(json['pendingFirstReveal']),
      latestTitle: serializer.fromJson<String?>(json['latestTitle']),
      latestVersionId: serializer.fromJson<String?>(json['latestVersionId']),
      revealedAtUtc: serializer.fromJson<int?>(json['revealedAtUtc']),
      sourceVersions: serializer.fromJson<String?>(json['sourceVersions']),
      sourceSetHash: serializer.fromJson<String?>(json['sourceSetHash']),
      generatedAtUtc: serializer.fromJson<int?>(json['generatedAtUtc']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'sessionId': serializer.toJson<String>(sessionId),
      'placeholder': serializer.toJson<String>(placeholder),
      'pendingFirstReveal': serializer.toJson<bool>(pendingFirstReveal),
      'latestTitle': serializer.toJson<String?>(latestTitle),
      'latestVersionId': serializer.toJson<String?>(latestVersionId),
      'revealedAtUtc': serializer.toJson<int?>(revealedAtUtc),
      'sourceVersions': serializer.toJson<String?>(sourceVersions),
      'sourceSetHash': serializer.toJson<String?>(sourceSetHash),
      'generatedAtUtc': serializer.toJson<int?>(generatedAtUtc),
    };
  }

  TitleRow copyWith({
    String? sessionId,
    String? placeholder,
    bool? pendingFirstReveal,
    Value<String?> latestTitle = const Value.absent(),
    Value<String?> latestVersionId = const Value.absent(),
    Value<int?> revealedAtUtc = const Value.absent(),
    Value<String?> sourceVersions = const Value.absent(),
    Value<String?> sourceSetHash = const Value.absent(),
    Value<int?> generatedAtUtc = const Value.absent(),
  }) => TitleRow(
    sessionId: sessionId ?? this.sessionId,
    placeholder: placeholder ?? this.placeholder,
    pendingFirstReveal: pendingFirstReveal ?? this.pendingFirstReveal,
    latestTitle: latestTitle.present ? latestTitle.value : this.latestTitle,
    latestVersionId: latestVersionId.present
        ? latestVersionId.value
        : this.latestVersionId,
    revealedAtUtc: revealedAtUtc.present
        ? revealedAtUtc.value
        : this.revealedAtUtc,
    sourceVersions: sourceVersions.present
        ? sourceVersions.value
        : this.sourceVersions,
    sourceSetHash: sourceSetHash.present
        ? sourceSetHash.value
        : this.sourceSetHash,
    generatedAtUtc: generatedAtUtc.present
        ? generatedAtUtc.value
        : this.generatedAtUtc,
  );
  TitleRow copyWithCompanion(TitlesCompanion data) {
    return TitleRow(
      sessionId: data.sessionId.present ? data.sessionId.value : this.sessionId,
      placeholder: data.placeholder.present
          ? data.placeholder.value
          : this.placeholder,
      pendingFirstReveal: data.pendingFirstReveal.present
          ? data.pendingFirstReveal.value
          : this.pendingFirstReveal,
      latestTitle: data.latestTitle.present
          ? data.latestTitle.value
          : this.latestTitle,
      latestVersionId: data.latestVersionId.present
          ? data.latestVersionId.value
          : this.latestVersionId,
      revealedAtUtc: data.revealedAtUtc.present
          ? data.revealedAtUtc.value
          : this.revealedAtUtc,
      sourceVersions: data.sourceVersions.present
          ? data.sourceVersions.value
          : this.sourceVersions,
      sourceSetHash: data.sourceSetHash.present
          ? data.sourceSetHash.value
          : this.sourceSetHash,
      generatedAtUtc: data.generatedAtUtc.present
          ? data.generatedAtUtc.value
          : this.generatedAtUtc,
    );
  }

  @override
  String toString() {
    return (StringBuffer('TitleRow(')
          ..write('sessionId: $sessionId, ')
          ..write('placeholder: $placeholder, ')
          ..write('pendingFirstReveal: $pendingFirstReveal, ')
          ..write('latestTitle: $latestTitle, ')
          ..write('latestVersionId: $latestVersionId, ')
          ..write('revealedAtUtc: $revealedAtUtc, ')
          ..write('sourceVersions: $sourceVersions, ')
          ..write('sourceSetHash: $sourceSetHash, ')
          ..write('generatedAtUtc: $generatedAtUtc')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    sessionId,
    placeholder,
    pendingFirstReveal,
    latestTitle,
    latestVersionId,
    revealedAtUtc,
    sourceVersions,
    sourceSetHash,
    generatedAtUtc,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is TitleRow &&
          other.sessionId == this.sessionId &&
          other.placeholder == this.placeholder &&
          other.pendingFirstReveal == this.pendingFirstReveal &&
          other.latestTitle == this.latestTitle &&
          other.latestVersionId == this.latestVersionId &&
          other.revealedAtUtc == this.revealedAtUtc &&
          other.sourceVersions == this.sourceVersions &&
          other.sourceSetHash == this.sourceSetHash &&
          other.generatedAtUtc == this.generatedAtUtc);
}

class TitlesCompanion extends UpdateCompanion<TitleRow> {
  final Value<String> sessionId;
  final Value<String> placeholder;
  final Value<bool> pendingFirstReveal;
  final Value<String?> latestTitle;
  final Value<String?> latestVersionId;
  final Value<int?> revealedAtUtc;
  final Value<String?> sourceVersions;
  final Value<String?> sourceSetHash;
  final Value<int?> generatedAtUtc;
  final Value<int> rowid;
  const TitlesCompanion({
    this.sessionId = const Value.absent(),
    this.placeholder = const Value.absent(),
    this.pendingFirstReveal = const Value.absent(),
    this.latestTitle = const Value.absent(),
    this.latestVersionId = const Value.absent(),
    this.revealedAtUtc = const Value.absent(),
    this.sourceVersions = const Value.absent(),
    this.sourceSetHash = const Value.absent(),
    this.generatedAtUtc = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  TitlesCompanion.insert({
    required String sessionId,
    required String placeholder,
    this.pendingFirstReveal = const Value.absent(),
    this.latestTitle = const Value.absent(),
    this.latestVersionId = const Value.absent(),
    this.revealedAtUtc = const Value.absent(),
    this.sourceVersions = const Value.absent(),
    this.sourceSetHash = const Value.absent(),
    this.generatedAtUtc = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : sessionId = Value(sessionId),
       placeholder = Value(placeholder);
  static Insertable<TitleRow> custom({
    Expression<String>? sessionId,
    Expression<String>? placeholder,
    Expression<bool>? pendingFirstReveal,
    Expression<String>? latestTitle,
    Expression<String>? latestVersionId,
    Expression<int>? revealedAtUtc,
    Expression<String>? sourceVersions,
    Expression<String>? sourceSetHash,
    Expression<int>? generatedAtUtc,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (sessionId != null) 'session_id': sessionId,
      if (placeholder != null) 'placeholder': placeholder,
      if (pendingFirstReveal != null)
        'pending_first_reveal': pendingFirstReveal,
      if (latestTitle != null) 'latest_title': latestTitle,
      if (latestVersionId != null) 'latest_version_id': latestVersionId,
      if (revealedAtUtc != null) 'revealed_at_utc': revealedAtUtc,
      if (sourceVersions != null) 'source_versions': sourceVersions,
      if (sourceSetHash != null) 'source_set_hash': sourceSetHash,
      if (generatedAtUtc != null) 'generated_at_utc': generatedAtUtc,
      if (rowid != null) 'rowid': rowid,
    });
  }

  TitlesCompanion copyWith({
    Value<String>? sessionId,
    Value<String>? placeholder,
    Value<bool>? pendingFirstReveal,
    Value<String?>? latestTitle,
    Value<String?>? latestVersionId,
    Value<int?>? revealedAtUtc,
    Value<String?>? sourceVersions,
    Value<String?>? sourceSetHash,
    Value<int?>? generatedAtUtc,
    Value<int>? rowid,
  }) {
    return TitlesCompanion(
      sessionId: sessionId ?? this.sessionId,
      placeholder: placeholder ?? this.placeholder,
      pendingFirstReveal: pendingFirstReveal ?? this.pendingFirstReveal,
      latestTitle: latestTitle ?? this.latestTitle,
      latestVersionId: latestVersionId ?? this.latestVersionId,
      revealedAtUtc: revealedAtUtc ?? this.revealedAtUtc,
      sourceVersions: sourceVersions ?? this.sourceVersions,
      sourceSetHash: sourceSetHash ?? this.sourceSetHash,
      generatedAtUtc: generatedAtUtc ?? this.generatedAtUtc,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (sessionId.present) {
      map['session_id'] = Variable<String>(sessionId.value);
    }
    if (placeholder.present) {
      map['placeholder'] = Variable<String>(placeholder.value);
    }
    if (pendingFirstReveal.present) {
      map['pending_first_reveal'] = Variable<bool>(pendingFirstReveal.value);
    }
    if (latestTitle.present) {
      map['latest_title'] = Variable<String>(latestTitle.value);
    }
    if (latestVersionId.present) {
      map['latest_version_id'] = Variable<String>(latestVersionId.value);
    }
    if (revealedAtUtc.present) {
      map['revealed_at_utc'] = Variable<int>(revealedAtUtc.value);
    }
    if (sourceVersions.present) {
      map['source_versions'] = Variable<String>(sourceVersions.value);
    }
    if (sourceSetHash.present) {
      map['source_set_hash'] = Variable<String>(sourceSetHash.value);
    }
    if (generatedAtUtc.present) {
      map['generated_at_utc'] = Variable<int>(generatedAtUtc.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('TitlesCompanion(')
          ..write('sessionId: $sessionId, ')
          ..write('placeholder: $placeholder, ')
          ..write('pendingFirstReveal: $pendingFirstReveal, ')
          ..write('latestTitle: $latestTitle, ')
          ..write('latestVersionId: $latestVersionId, ')
          ..write('revealedAtUtc: $revealedAtUtc, ')
          ..write('sourceVersions: $sourceVersions, ')
          ..write('sourceSetHash: $sourceSetHash, ')
          ..write('generatedAtUtc: $generatedAtUtc, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $SettingsTable extends Settings
    with TableInfo<$SettingsTable, SettingRow> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $SettingsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _settingKeyMeta = const VerificationMeta(
    'settingKey',
  );
  @override
  late final GeneratedColumn<String> settingKey = GeneratedColumn<String>(
    'key',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _settingValueMeta = const VerificationMeta(
    'settingValue',
  );
  @override
  late final GeneratedColumn<String> settingValue = GeneratedColumn<String>(
    'value',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [settingKey, settingValue];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'settings';
  @override
  VerificationContext validateIntegrity(
    Insertable<SettingRow> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('key')) {
      context.handle(
        _settingKeyMeta,
        settingKey.isAcceptableOrUnknown(data['key']!, _settingKeyMeta),
      );
    } else if (isInserting) {
      context.missing(_settingKeyMeta);
    }
    if (data.containsKey('value')) {
      context.handle(
        _settingValueMeta,
        settingValue.isAcceptableOrUnknown(data['value']!, _settingValueMeta),
      );
    } else if (isInserting) {
      context.missing(_settingValueMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {settingKey};
  @override
  SettingRow map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return SettingRow(
      settingKey: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}key'],
      )!,
      settingValue: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}value'],
      )!,
    );
  }

  @override
  $SettingsTable createAlias(String alias) {
    return $SettingsTable(attachedDatabase, alias);
  }
}

class SettingRow extends DataClass implements Insertable<SettingRow> {
  final String settingKey;
  final String settingValue;
  const SettingRow({required this.settingKey, required this.settingValue});
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['key'] = Variable<String>(settingKey);
    map['value'] = Variable<String>(settingValue);
    return map;
  }

  SettingsCompanion toCompanion(bool nullToAbsent) {
    return SettingsCompanion(
      settingKey: Value(settingKey),
      settingValue: Value(settingValue),
    );
  }

  factory SettingRow.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return SettingRow(
      settingKey: serializer.fromJson<String>(json['settingKey']),
      settingValue: serializer.fromJson<String>(json['settingValue']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'settingKey': serializer.toJson<String>(settingKey),
      'settingValue': serializer.toJson<String>(settingValue),
    };
  }

  SettingRow copyWith({String? settingKey, String? settingValue}) => SettingRow(
    settingKey: settingKey ?? this.settingKey,
    settingValue: settingValue ?? this.settingValue,
  );
  SettingRow copyWithCompanion(SettingsCompanion data) {
    return SettingRow(
      settingKey: data.settingKey.present
          ? data.settingKey.value
          : this.settingKey,
      settingValue: data.settingValue.present
          ? data.settingValue.value
          : this.settingValue,
    );
  }

  @override
  String toString() {
    return (StringBuffer('SettingRow(')
          ..write('settingKey: $settingKey, ')
          ..write('settingValue: $settingValue')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(settingKey, settingValue);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is SettingRow &&
          other.settingKey == this.settingKey &&
          other.settingValue == this.settingValue);
}

class SettingsCompanion extends UpdateCompanion<SettingRow> {
  final Value<String> settingKey;
  final Value<String> settingValue;
  final Value<int> rowid;
  const SettingsCompanion({
    this.settingKey = const Value.absent(),
    this.settingValue = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  SettingsCompanion.insert({
    required String settingKey,
    required String settingValue,
    this.rowid = const Value.absent(),
  }) : settingKey = Value(settingKey),
       settingValue = Value(settingValue);
  static Insertable<SettingRow> custom({
    Expression<String>? settingKey,
    Expression<String>? settingValue,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (settingKey != null) 'key': settingKey,
      if (settingValue != null) 'value': settingValue,
      if (rowid != null) 'rowid': rowid,
    });
  }

  SettingsCompanion copyWith({
    Value<String>? settingKey,
    Value<String>? settingValue,
    Value<int>? rowid,
  }) {
    return SettingsCompanion(
      settingKey: settingKey ?? this.settingKey,
      settingValue: settingValue ?? this.settingValue,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (settingKey.present) {
      map['key'] = Variable<String>(settingKey.value);
    }
    if (settingValue.present) {
      map['value'] = Variable<String>(settingValue.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('SettingsCompanion(')
          ..write('settingKey: $settingKey, ')
          ..write('settingValue: $settingValue, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

abstract class _$EchoDatabase extends GeneratedDatabase {
  _$EchoDatabase(QueryExecutor e) : super(e);
  $EchoDatabaseManager get managers => $EchoDatabaseManager(this);
  late final $SessionsTable sessions = $SessionsTable(this);
  late final $AudioChunksTable audioChunks = $AudioChunksTable(this);
  late final $FinalSourcesTable finalSources = $FinalSourcesTable(this);
  late final $SegmentsTable segments = $SegmentsTable(this);
  late final $VersionsTable versions = $VersionsTable(this);
  late final $ArtifactsTable artifacts = $ArtifactsTable(this);
  late final $JobsTable jobs = $JobsTable(this);
  late final $TitlesTable titles = $TitlesTable(this);
  late final $SettingsTable settings = $SettingsTable(this);
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [
    sessions,
    audioChunks,
    finalSources,
    segments,
    versions,
    artifacts,
    jobs,
    titles,
    settings,
  ];
  @override
  StreamQueryUpdateRules get streamUpdateRules => const StreamQueryUpdateRules([
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'sessions',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('audio_chunks', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'sessions',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('final_sources', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'sessions',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('segments', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'segments',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('versions', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'sessions',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('artifacts', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'segments',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('artifacts', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'sessions',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('jobs', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'sessions',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('titles', kind: UpdateKind.delete)],
    ),
  ]);
}

typedef $$SessionsTableCreateCompanionBuilder = SessionsCompanion Function({
  required String id,
  Value<String?> title,
  required String placeholderTitle,
  required int createdAtUtc,
  required String state,
  Value<String?> modelId,
  Value<String?> modelVersion,
  Value<String?> installationId,
  Value<String?> asrConfigHash,
  Value<int> durationMs,
  Value<int> gapCount,
  Value<int> audioBytes,
  Value<String?> failureLabel,
  Value<int> rowid,
});
typedef $$SessionsTableUpdateCompanionBuilder = SessionsCompanion Function({
  Value<String> id,
  Value<String?> title,
  Value<String> placeholderTitle,
  Value<int> createdAtUtc,
  Value<String> state,
  Value<String?> modelId,
  Value<String?> modelVersion,
  Value<String?> installationId,
  Value<String?> asrConfigHash,
  Value<int> durationMs,
  Value<int> gapCount,
  Value<int> audioBytes,
  Value<String?> failureLabel,
  Value<int> rowid,
});

final class $$SessionsTableReferences
    extends BaseReferences<_$EchoDatabase, $SessionsTable, SessionRow> {
  $$SessionsTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static MultiTypedResultKey<$AudioChunksTable, List<AudioChunkRow>>
  _audioChunksRefsTable(_$EchoDatabase db) => MultiTypedResultKey.fromTable(
    db.audioChunks,
    aliasName: 'sessions__id__audio_chunks__session_id',
  );

  $$AudioChunksTableProcessedTableManager get audioChunksRefs {
    final manager = $$AudioChunksTableTableManager(
      $_db,
      $_db.audioChunks,
    ).filter((f) => f.sessionId.id.sqlEquals($_itemColumn<String>('id')!));

    final cache = $_typedResult.readTableOrNull(_audioChunksRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<$FinalSourcesTable, List<FinalSourceRow>>
  _finalSourcesRefsTable(_$EchoDatabase db) => MultiTypedResultKey.fromTable(
    db.finalSources,
    aliasName: 'sessions__id__final_sources__session_id',
  );

  $$FinalSourcesTableProcessedTableManager get finalSourcesRefs {
    final manager = $$FinalSourcesTableTableManager(
      $_db,
      $_db.finalSources,
    ).filter((f) => f.sessionId.id.sqlEquals($_itemColumn<String>('id')!));

    final cache = $_typedResult.readTableOrNull(_finalSourcesRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<$SegmentsTable, List<SegmentRow>>
  _segmentsRefsTable(_$EchoDatabase db) => MultiTypedResultKey.fromTable(
    db.segments,
    aliasName: 'sessions__id__segments__session_id',
  );

  $$SegmentsTableProcessedTableManager get segmentsRefs {
    final manager = $$SegmentsTableTableManager(
      $_db,
      $_db.segments,
    ).filter((f) => f.sessionId.id.sqlEquals($_itemColumn<String>('id')!));

    final cache = $_typedResult.readTableOrNull(_segmentsRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<$ArtifactsTable, List<ArtifactRow>>
  _artifactsRefsTable(_$EchoDatabase db) => MultiTypedResultKey.fromTable(
    db.artifacts,
    aliasName: 'sessions__id__artifacts__session_id',
  );

  $$ArtifactsTableProcessedTableManager get artifactsRefs {
    final manager = $$ArtifactsTableTableManager(
      $_db,
      $_db.artifacts,
    ).filter((f) => f.sessionId.id.sqlEquals($_itemColumn<String>('id')!));

    final cache = $_typedResult.readTableOrNull(_artifactsRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<$JobsTable, List<JobRow>> _jobsRefsTable(
    _$EchoDatabase db,
  ) => MultiTypedResultKey.fromTable(
    db.jobs,
    aliasName: 'sessions__id__jobs__session_id',
  );

  $$JobsTableProcessedTableManager get jobsRefs {
    final manager = $$JobsTableTableManager(
      $_db,
      $_db.jobs,
    ).filter((f) => f.sessionId.id.sqlEquals($_itemColumn<String>('id')!));

    final cache = $_typedResult.readTableOrNull(_jobsRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<$TitlesTable, List<TitleRow>> _titlesRefsTable(
    _$EchoDatabase db,
  ) => MultiTypedResultKey.fromTable(
    db.titles,
    aliasName: 'sessions__id__titles__session_id',
  );

  $$TitlesTableProcessedTableManager get titlesRefs {
    final manager = $$TitlesTableTableManager(
      $_db,
      $_db.titles,
    ).filter((f) => f.sessionId.id.sqlEquals($_itemColumn<String>('id')!));

    final cache = $_typedResult.readTableOrNull(_titlesRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }
}

class $$SessionsTableFilterComposer
    extends Composer<_$EchoDatabase, $SessionsTable> {
  $$SessionsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get placeholderTitle => $composableBuilder(
    column: $table.placeholderTitle,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get createdAtUtc => $composableBuilder(
    column: $table.createdAtUtc,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get state => $composableBuilder(
    column: $table.state,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get modelId => $composableBuilder(
    column: $table.modelId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get modelVersion => $composableBuilder(
    column: $table.modelVersion,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get installationId => $composableBuilder(
    column: $table.installationId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get asrConfigHash => $composableBuilder(
    column: $table.asrConfigHash,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get durationMs => $composableBuilder(
    column: $table.durationMs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get gapCount => $composableBuilder(
    column: $table.gapCount,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get audioBytes => $composableBuilder(
    column: $table.audioBytes,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get failureLabel => $composableBuilder(
    column: $table.failureLabel,
    builder: (column) => ColumnFilters(column),
  );

  Expression<bool> audioChunksRefs(
    Expression<bool> Function($$AudioChunksTableFilterComposer f) f,
  ) {
    final $$AudioChunksTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.audioChunks,
      getReferencedColumn: (t) => t.sessionId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$AudioChunksTableFilterComposer(
            $db: $db,
            $table: $db.audioChunks,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> finalSourcesRefs(
    Expression<bool> Function($$FinalSourcesTableFilterComposer f) f,
  ) {
    final $$FinalSourcesTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.finalSources,
      getReferencedColumn: (t) => t.sessionId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$FinalSourcesTableFilterComposer(
            $db: $db,
            $table: $db.finalSources,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> segmentsRefs(
    Expression<bool> Function($$SegmentsTableFilterComposer f) f,
  ) {
    final $$SegmentsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.segments,
      getReferencedColumn: (t) => t.sessionId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SegmentsTableFilterComposer(
            $db: $db,
            $table: $db.segments,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> artifactsRefs(
    Expression<bool> Function($$ArtifactsTableFilterComposer f) f,
  ) {
    final $$ArtifactsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.artifacts,
      getReferencedColumn: (t) => t.sessionId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$ArtifactsTableFilterComposer(
            $db: $db,
            $table: $db.artifacts,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> jobsRefs(
    Expression<bool> Function($$JobsTableFilterComposer f) f,
  ) {
    final $$JobsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.jobs,
      getReferencedColumn: (t) => t.sessionId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$JobsTableFilterComposer(
            $db: $db,
            $table: $db.jobs,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> titlesRefs(
    Expression<bool> Function($$TitlesTableFilterComposer f) f,
  ) {
    final $$TitlesTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.titles,
      getReferencedColumn: (t) => t.sessionId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$TitlesTableFilterComposer(
            $db: $db,
            $table: $db.titles,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$SessionsTableOrderingComposer
    extends Composer<_$EchoDatabase, $SessionsTable> {
  $$SessionsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get placeholderTitle => $composableBuilder(
    column: $table.placeholderTitle,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get createdAtUtc => $composableBuilder(
    column: $table.createdAtUtc,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get state => $composableBuilder(
    column: $table.state,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get modelId => $composableBuilder(
    column: $table.modelId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get modelVersion => $composableBuilder(
    column: $table.modelVersion,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get installationId => $composableBuilder(
    column: $table.installationId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get asrConfigHash => $composableBuilder(
    column: $table.asrConfigHash,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get durationMs => $composableBuilder(
    column: $table.durationMs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get gapCount => $composableBuilder(
    column: $table.gapCount,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get audioBytes => $composableBuilder(
    column: $table.audioBytes,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get failureLabel => $composableBuilder(
    column: $table.failureLabel,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$SessionsTableAnnotationComposer
    extends Composer<_$EchoDatabase, $SessionsTable> {
  $$SessionsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<String> get placeholderTitle => $composableBuilder(
    column: $table.placeholderTitle,
    builder: (column) => column,
  );

  GeneratedColumn<int> get createdAtUtc => $composableBuilder(
    column: $table.createdAtUtc,
    builder: (column) => column,
  );

  GeneratedColumn<String> get state =>
      $composableBuilder(column: $table.state, builder: (column) => column);

  GeneratedColumn<String> get modelId =>
      $composableBuilder(column: $table.modelId, builder: (column) => column);

  GeneratedColumn<String> get modelVersion => $composableBuilder(
    column: $table.modelVersion,
    builder: (column) => column,
  );

  GeneratedColumn<String> get installationId => $composableBuilder(
    column: $table.installationId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get asrConfigHash => $composableBuilder(
    column: $table.asrConfigHash,
    builder: (column) => column,
  );

  GeneratedColumn<int> get durationMs => $composableBuilder(
    column: $table.durationMs,
    builder: (column) => column,
  );

  GeneratedColumn<int> get gapCount =>
      $composableBuilder(column: $table.gapCount, builder: (column) => column);

  GeneratedColumn<int> get audioBytes => $composableBuilder(
    column: $table.audioBytes,
    builder: (column) => column,
  );

  GeneratedColumn<String> get failureLabel => $composableBuilder(
    column: $table.failureLabel,
    builder: (column) => column,
  );

  Expression<T> audioChunksRefs<T extends Object>(
    Expression<T> Function($$AudioChunksTableAnnotationComposer a) f,
  ) {
    final $$AudioChunksTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.audioChunks,
      getReferencedColumn: (t) => t.sessionId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$AudioChunksTableAnnotationComposer(
            $db: $db,
            $table: $db.audioChunks,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<T> finalSourcesRefs<T extends Object>(
    Expression<T> Function($$FinalSourcesTableAnnotationComposer a) f,
  ) {
    final $$FinalSourcesTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.finalSources,
      getReferencedColumn: (t) => t.sessionId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$FinalSourcesTableAnnotationComposer(
            $db: $db,
            $table: $db.finalSources,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<T> segmentsRefs<T extends Object>(
    Expression<T> Function($$SegmentsTableAnnotationComposer a) f,
  ) {
    final $$SegmentsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.segments,
      getReferencedColumn: (t) => t.sessionId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SegmentsTableAnnotationComposer(
            $db: $db,
            $table: $db.segments,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<T> artifactsRefs<T extends Object>(
    Expression<T> Function($$ArtifactsTableAnnotationComposer a) f,
  ) {
    final $$ArtifactsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.artifacts,
      getReferencedColumn: (t) => t.sessionId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$ArtifactsTableAnnotationComposer(
            $db: $db,
            $table: $db.artifacts,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<T> jobsRefs<T extends Object>(
    Expression<T> Function($$JobsTableAnnotationComposer a) f,
  ) {
    final $$JobsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.jobs,
      getReferencedColumn: (t) => t.sessionId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$JobsTableAnnotationComposer(
            $db: $db,
            $table: $db.jobs,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<T> titlesRefs<T extends Object>(
    Expression<T> Function($$TitlesTableAnnotationComposer a) f,
  ) {
    final $$TitlesTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.titles,
      getReferencedColumn: (t) => t.sessionId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$TitlesTableAnnotationComposer(
            $db: $db,
            $table: $db.titles,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$SessionsTableTableManager
    extends
        RootTableManager<
          _$EchoDatabase,
          $SessionsTable,
          SessionRow,
          $$SessionsTableFilterComposer,
          $$SessionsTableOrderingComposer,
          $$SessionsTableAnnotationComposer,
          $$SessionsTableCreateCompanionBuilder,
          $$SessionsTableUpdateCompanionBuilder,
          (SessionRow, $$SessionsTableReferences),
          SessionRow,
          PrefetchHooks Function({
            bool audioChunksRefs,
            bool finalSourcesRefs,
            bool segmentsRefs,
            bool artifactsRefs,
            bool jobsRefs,
            bool titlesRefs,
          })
        > {
  $$SessionsTableTableManager(_$EchoDatabase db, $SessionsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$SessionsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$SessionsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$SessionsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String?> title = const Value.absent(),
                Value<String> placeholderTitle = const Value.absent(),
                Value<int> createdAtUtc = const Value.absent(),
                Value<String> state = const Value.absent(),
                Value<String?> modelId = const Value.absent(),
                Value<String?> modelVersion = const Value.absent(),
                Value<String?> installationId = const Value.absent(),
                Value<String?> asrConfigHash = const Value.absent(),
                Value<int> durationMs = const Value.absent(),
                Value<int> gapCount = const Value.absent(),
                Value<int> audioBytes = const Value.absent(),
                Value<String?> failureLabel = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => SessionsCompanion(
                id: id,
                title: title,
                placeholderTitle: placeholderTitle,
                createdAtUtc: createdAtUtc,
                state: state,
                modelId: modelId,
                modelVersion: modelVersion,
                installationId: installationId,
                asrConfigHash: asrConfigHash,
                durationMs: durationMs,
                gapCount: gapCount,
                audioBytes: audioBytes,
                failureLabel: failureLabel,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                Value<String?> title = const Value.absent(),
                required String placeholderTitle,
                required int createdAtUtc,
                required String state,
                Value<String?> modelId = const Value.absent(),
                Value<String?> modelVersion = const Value.absent(),
                Value<String?> installationId = const Value.absent(),
                Value<String?> asrConfigHash = const Value.absent(),
                Value<int> durationMs = const Value.absent(),
                Value<int> gapCount = const Value.absent(),
                Value<int> audioBytes = const Value.absent(),
                Value<String?> failureLabel = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => SessionsCompanion.insert(
                id: id,
                title: title,
                placeholderTitle: placeholderTitle,
                createdAtUtc: createdAtUtc,
                state: state,
                modelId: modelId,
                modelVersion: modelVersion,
                installationId: installationId,
                asrConfigHash: asrConfigHash,
                durationMs: durationMs,
                gapCount: gapCount,
                audioBytes: audioBytes,
                failureLabel: failureLabel,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$SessionsTable, SessionRow>(table),
                  $$SessionsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback:
              ({
                audioChunksRefs = false,
                finalSourcesRefs = false,
                segmentsRefs = false,
                artifactsRefs = false,
                jobsRefs = false,
                titlesRefs = false,
              }) {
                return PrefetchHooks(
                  db: db,
                  explicitlyWatchedTables: [
                    if (audioChunksRefs) db.audioChunks,
                    if (finalSourcesRefs) db.finalSources,
                    if (segmentsRefs) db.segments,
                    if (artifactsRefs) db.artifacts,
                    if (jobsRefs) db.jobs,
                    if (titlesRefs) db.titles,
                  ],
                  addJoins: null,
                  getPrefetchedDataCallback: (items) async {
                    return [
                      if (audioChunksRefs)
                        await $_getPrefetchedDataByColumns<
                          SessionRow,
                          $SessionsTable,
                          AudioChunkRow,
                          $AudioChunksTable
                        >(
                          currentTable: table,
                          referencedTable: $$SessionsTableReferences
                              ._audioChunksRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$SessionsTableReferences(
                                db,
                                table,
                                p0,
                              ).audioChunksRefs,
                          currentColumn: 'id',
                          referencedColumn: 'session_id',
                          typedResults: items,
                        ),
                      if (finalSourcesRefs)
                        await $_getPrefetchedDataByColumns<
                          SessionRow,
                          $SessionsTable,
                          FinalSourceRow,
                          $FinalSourcesTable
                        >(
                          currentTable: table,
                          referencedTable: $$SessionsTableReferences
                              ._finalSourcesRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$SessionsTableReferences(
                                db,
                                table,
                                p0,
                              ).finalSourcesRefs,
                          currentColumn: 'id',
                          referencedColumn: 'session_id',
                          typedResults: items,
                        ),
                      if (segmentsRefs)
                        await $_getPrefetchedDataByColumns<
                          SessionRow,
                          $SessionsTable,
                          SegmentRow,
                          $SegmentsTable
                        >(
                          currentTable: table,
                          referencedTable: $$SessionsTableReferences
                              ._segmentsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$SessionsTableReferences(
                                db,
                                table,
                                p0,
                              ).segmentsRefs,
                          currentColumn: 'id',
                          referencedColumn: 'session_id',
                          typedResults: items,
                        ),
                      if (artifactsRefs)
                        await $_getPrefetchedDataByColumns<
                          SessionRow,
                          $SessionsTable,
                          ArtifactRow,
                          $ArtifactsTable
                        >(
                          currentTable: table,
                          referencedTable: $$SessionsTableReferences
                              ._artifactsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$SessionsTableReferences(
                                db,
                                table,
                                p0,
                              ).artifactsRefs,
                          currentColumn: 'id',
                          referencedColumn: 'session_id',
                          typedResults: items,
                        ),
                      if (jobsRefs)
                        await $_getPrefetchedDataByColumns<
                          SessionRow,
                          $SessionsTable,
                          JobRow,
                          $JobsTable
                        >(
                          currentTable: table,
                          referencedTable: $$SessionsTableReferences
                              ._jobsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$SessionsTableReferences(db, table, p0).jobsRefs,
                          currentColumn: 'id',
                          referencedColumn: 'session_id',
                          typedResults: items,
                        ),
                      if (titlesRefs)
                        await $_getPrefetchedDataByColumns<
                          SessionRow,
                          $SessionsTable,
                          TitleRow,
                          $TitlesTable
                        >(
                          currentTable: table,
                          referencedTable: $$SessionsTableReferences
                              ._titlesRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$SessionsTableReferences(
                                db,
                                table,
                                p0,
                              ).titlesRefs,
                          currentColumn: 'id',
                          referencedColumn: 'session_id',
                          typedResults: items,
                        ),
                    ];
                  },
                );
              },
        ),
      );
}

typedef $$SessionsTableProcessedTableManager =
    ProcessedTableManager<
      _$EchoDatabase,
      $SessionsTable,
      SessionRow,
      $$SessionsTableFilterComposer,
      $$SessionsTableOrderingComposer,
      $$SessionsTableAnnotationComposer,
      $$SessionsTableCreateCompanionBuilder,
      $$SessionsTableUpdateCompanionBuilder,
      (SessionRow, $$SessionsTableReferences),
      SessionRow,
      PrefetchHooks Function({
        bool audioChunksRefs,
        bool finalSourcesRefs,
        bool segmentsRefs,
        bool artifactsRefs,
        bool jobsRefs,
        bool titlesRefs,
      })
    >;
typedef $$AudioChunksTableCreateCompanionBuilder =
    AudioChunksCompanion Function({
      required String id,
      required String sessionId,
      required int chunkSeq,
      required String state,
      required int createdAtUtc,
      required String tempPath,
      Value<String?> finalPath,
      Value<int?> sealedAtUtc,
      Value<int?> capturedStartMs,
      Value<int?> capturedEndMs,
      Value<int?> durationMs,
      Value<String?> codec,
      Value<int?> sampleRate,
      Value<int?> bitRate,
      Value<String?> checksum,
      Value<String?> failureStage,
      Value<int> rowid,
    });
typedef $$AudioChunksTableUpdateCompanionBuilder =
    AudioChunksCompanion Function({
      Value<String> id,
      Value<String> sessionId,
      Value<int> chunkSeq,
      Value<String> state,
      Value<int> createdAtUtc,
      Value<String> tempPath,
      Value<String?> finalPath,
      Value<int?> sealedAtUtc,
      Value<int?> capturedStartMs,
      Value<int?> capturedEndMs,
      Value<int?> durationMs,
      Value<String?> codec,
      Value<int?> sampleRate,
      Value<int?> bitRate,
      Value<String?> checksum,
      Value<String?> failureStage,
      Value<int> rowid,
    });

final class $$AudioChunksTableReferences
    extends BaseReferences<_$EchoDatabase, $AudioChunksTable, AudioChunkRow> {
  $$AudioChunksTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static $SessionsTable _sessionIdTable(_$EchoDatabase db) =>
      db.sessions.createAlias('audio_chunks__session_id__sessions__id');

  $$SessionsTableProcessedTableManager get sessionId {
    final $_column = $_itemColumn<String>('session_id')!;

    final manager = $$SessionsTableTableManager(
      $_db,
      $_db.sessions,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_sessionIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$AudioChunksTableFilterComposer
    extends Composer<_$EchoDatabase, $AudioChunksTable> {
  $$AudioChunksTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get chunkSeq => $composableBuilder(
    column: $table.chunkSeq,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get state => $composableBuilder(
    column: $table.state,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get createdAtUtc => $composableBuilder(
    column: $table.createdAtUtc,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get tempPath => $composableBuilder(
    column: $table.tempPath,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get finalPath => $composableBuilder(
    column: $table.finalPath,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get sealedAtUtc => $composableBuilder(
    column: $table.sealedAtUtc,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get capturedStartMs => $composableBuilder(
    column: $table.capturedStartMs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get capturedEndMs => $composableBuilder(
    column: $table.capturedEndMs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get durationMs => $composableBuilder(
    column: $table.durationMs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get codec => $composableBuilder(
    column: $table.codec,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get sampleRate => $composableBuilder(
    column: $table.sampleRate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get bitRate => $composableBuilder(
    column: $table.bitRate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get checksum => $composableBuilder(
    column: $table.checksum,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get failureStage => $composableBuilder(
    column: $table.failureStage,
    builder: (column) => ColumnFilters(column),
  );

  $$SessionsTableFilterComposer get sessionId {
    final $$SessionsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.sessionId,
      referencedTable: $db.sessions,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SessionsTableFilterComposer(
            $db: $db,
            $table: $db.sessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$AudioChunksTableOrderingComposer
    extends Composer<_$EchoDatabase, $AudioChunksTable> {
  $$AudioChunksTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get chunkSeq => $composableBuilder(
    column: $table.chunkSeq,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get state => $composableBuilder(
    column: $table.state,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get createdAtUtc => $composableBuilder(
    column: $table.createdAtUtc,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get tempPath => $composableBuilder(
    column: $table.tempPath,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get finalPath => $composableBuilder(
    column: $table.finalPath,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get sealedAtUtc => $composableBuilder(
    column: $table.sealedAtUtc,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get capturedStartMs => $composableBuilder(
    column: $table.capturedStartMs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get capturedEndMs => $composableBuilder(
    column: $table.capturedEndMs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get durationMs => $composableBuilder(
    column: $table.durationMs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get codec => $composableBuilder(
    column: $table.codec,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get sampleRate => $composableBuilder(
    column: $table.sampleRate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get bitRate => $composableBuilder(
    column: $table.bitRate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get checksum => $composableBuilder(
    column: $table.checksum,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get failureStage => $composableBuilder(
    column: $table.failureStage,
    builder: (column) => ColumnOrderings(column),
  );

  $$SessionsTableOrderingComposer get sessionId {
    final $$SessionsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.sessionId,
      referencedTable: $db.sessions,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SessionsTableOrderingComposer(
            $db: $db,
            $table: $db.sessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$AudioChunksTableAnnotationComposer
    extends Composer<_$EchoDatabase, $AudioChunksTable> {
  $$AudioChunksTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<int> get chunkSeq =>
      $composableBuilder(column: $table.chunkSeq, builder: (column) => column);

  GeneratedColumn<String> get state =>
      $composableBuilder(column: $table.state, builder: (column) => column);

  GeneratedColumn<int> get createdAtUtc => $composableBuilder(
    column: $table.createdAtUtc,
    builder: (column) => column,
  );

  GeneratedColumn<String> get tempPath =>
      $composableBuilder(column: $table.tempPath, builder: (column) => column);

  GeneratedColumn<String> get finalPath =>
      $composableBuilder(column: $table.finalPath, builder: (column) => column);

  GeneratedColumn<int> get sealedAtUtc => $composableBuilder(
    column: $table.sealedAtUtc,
    builder: (column) => column,
  );

  GeneratedColumn<int> get capturedStartMs => $composableBuilder(
    column: $table.capturedStartMs,
    builder: (column) => column,
  );

  GeneratedColumn<int> get capturedEndMs => $composableBuilder(
    column: $table.capturedEndMs,
    builder: (column) => column,
  );

  GeneratedColumn<int> get durationMs => $composableBuilder(
    column: $table.durationMs,
    builder: (column) => column,
  );

  GeneratedColumn<String> get codec =>
      $composableBuilder(column: $table.codec, builder: (column) => column);

  GeneratedColumn<int> get sampleRate => $composableBuilder(
    column: $table.sampleRate,
    builder: (column) => column,
  );

  GeneratedColumn<int> get bitRate =>
      $composableBuilder(column: $table.bitRate, builder: (column) => column);

  GeneratedColumn<String> get checksum =>
      $composableBuilder(column: $table.checksum, builder: (column) => column);

  GeneratedColumn<String> get failureStage => $composableBuilder(
    column: $table.failureStage,
    builder: (column) => column,
  );

  $$SessionsTableAnnotationComposer get sessionId {
    final $$SessionsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.sessionId,
      referencedTable: $db.sessions,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SessionsTableAnnotationComposer(
            $db: $db,
            $table: $db.sessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$AudioChunksTableTableManager
    extends
        RootTableManager<
          _$EchoDatabase,
          $AudioChunksTable,
          AudioChunkRow,
          $$AudioChunksTableFilterComposer,
          $$AudioChunksTableOrderingComposer,
          $$AudioChunksTableAnnotationComposer,
          $$AudioChunksTableCreateCompanionBuilder,
          $$AudioChunksTableUpdateCompanionBuilder,
          (AudioChunkRow, $$AudioChunksTableReferences),
          AudioChunkRow,
          PrefetchHooks Function({bool sessionId})
        > {
  $$AudioChunksTableTableManager(_$EchoDatabase db, $AudioChunksTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$AudioChunksTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$AudioChunksTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$AudioChunksTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String> sessionId = const Value.absent(),
                Value<int> chunkSeq = const Value.absent(),
                Value<String> state = const Value.absent(),
                Value<int> createdAtUtc = const Value.absent(),
                Value<String> tempPath = const Value.absent(),
                Value<String?> finalPath = const Value.absent(),
                Value<int?> sealedAtUtc = const Value.absent(),
                Value<int?> capturedStartMs = const Value.absent(),
                Value<int?> capturedEndMs = const Value.absent(),
                Value<int?> durationMs = const Value.absent(),
                Value<String?> codec = const Value.absent(),
                Value<int?> sampleRate = const Value.absent(),
                Value<int?> bitRate = const Value.absent(),
                Value<String?> checksum = const Value.absent(),
                Value<String?> failureStage = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => AudioChunksCompanion(
                id: id,
                sessionId: sessionId,
                chunkSeq: chunkSeq,
                state: state,
                createdAtUtc: createdAtUtc,
                tempPath: tempPath,
                finalPath: finalPath,
                sealedAtUtc: sealedAtUtc,
                capturedStartMs: capturedStartMs,
                capturedEndMs: capturedEndMs,
                durationMs: durationMs,
                codec: codec,
                sampleRate: sampleRate,
                bitRate: bitRate,
                checksum: checksum,
                failureStage: failureStage,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required String sessionId,
                required int chunkSeq,
                required String state,
                required int createdAtUtc,
                required String tempPath,
                Value<String?> finalPath = const Value.absent(),
                Value<int?> sealedAtUtc = const Value.absent(),
                Value<int?> capturedStartMs = const Value.absent(),
                Value<int?> capturedEndMs = const Value.absent(),
                Value<int?> durationMs = const Value.absent(),
                Value<String?> codec = const Value.absent(),
                Value<int?> sampleRate = const Value.absent(),
                Value<int?> bitRate = const Value.absent(),
                Value<String?> checksum = const Value.absent(),
                Value<String?> failureStage = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => AudioChunksCompanion.insert(
                id: id,
                sessionId: sessionId,
                chunkSeq: chunkSeq,
                state: state,
                createdAtUtc: createdAtUtc,
                tempPath: tempPath,
                finalPath: finalPath,
                sealedAtUtc: sealedAtUtc,
                capturedStartMs: capturedStartMs,
                capturedEndMs: capturedEndMs,
                durationMs: durationMs,
                codec: codec,
                sampleRate: sampleRate,
                bitRate: bitRate,
                checksum: checksum,
                failureStage: failureStage,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$AudioChunksTable, AudioChunkRow>(table),
                  $$AudioChunksTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({sessionId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (sessionId) {
                      state = state.withJoin(
                        currentTable: table,
                        currentColumn: table.sessionId,
                        referencedTable: $$AudioChunksTableReferences
                            ._sessionIdTable(db),
                        referencedColumn: $$AudioChunksTableReferences
                            ._sessionIdTable(db)
                            .id,
                      ) as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$AudioChunksTableProcessedTableManager =
    ProcessedTableManager<
      _$EchoDatabase,
      $AudioChunksTable,
      AudioChunkRow,
      $$AudioChunksTableFilterComposer,
      $$AudioChunksTableOrderingComposer,
      $$AudioChunksTableAnnotationComposer,
      $$AudioChunksTableCreateCompanionBuilder,
      $$AudioChunksTableUpdateCompanionBuilder,
      (AudioChunkRow, $$AudioChunksTableReferences),
      AudioChunkRow,
      PrefetchHooks Function({bool sessionId})
    >;
typedef $$FinalSourcesTableCreateCompanionBuilder =
    FinalSourcesCompanion Function({
      required String id,
      required String sessionId,
      required String text$,
      required int finalizedAtUtc,
      required int captureStartedMonoNs,
      required int captureEndedMonoNs,
      required int capturedStartMs,
      required int capturedEndMs,
      required String chunkIds,
      required String modelId,
      required String modelVersion,
      Value<int> rowid,
    });
typedef $$FinalSourcesTableUpdateCompanionBuilder =
    FinalSourcesCompanion Function({
      Value<String> id,
      Value<String> sessionId,
      Value<String> text$,
      Value<int> finalizedAtUtc,
      Value<int> captureStartedMonoNs,
      Value<int> captureEndedMonoNs,
      Value<int> capturedStartMs,
      Value<int> capturedEndMs,
      Value<String> chunkIds,
      Value<String> modelId,
      Value<String> modelVersion,
      Value<int> rowid,
    });

final class $$FinalSourcesTableReferences
    extends BaseReferences<_$EchoDatabase, $FinalSourcesTable, FinalSourceRow> {
  $$FinalSourcesTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static $SessionsTable _sessionIdTable(_$EchoDatabase db) =>
      db.sessions.createAlias('final_sources__session_id__sessions__id');

  $$SessionsTableProcessedTableManager get sessionId {
    final $_column = $_itemColumn<String>('session_id')!;

    final manager = $$SessionsTableTableManager(
      $_db,
      $_db.sessions,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_sessionIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$FinalSourcesTableFilterComposer
    extends Composer<_$EchoDatabase, $FinalSourcesTable> {
  $$FinalSourcesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get text$ => $composableBuilder(
    column: $table.text$,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get finalizedAtUtc => $composableBuilder(
    column: $table.finalizedAtUtc,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get captureStartedMonoNs => $composableBuilder(
    column: $table.captureStartedMonoNs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get captureEndedMonoNs => $composableBuilder(
    column: $table.captureEndedMonoNs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get capturedStartMs => $composableBuilder(
    column: $table.capturedStartMs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get capturedEndMs => $composableBuilder(
    column: $table.capturedEndMs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get chunkIds => $composableBuilder(
    column: $table.chunkIds,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get modelId => $composableBuilder(
    column: $table.modelId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get modelVersion => $composableBuilder(
    column: $table.modelVersion,
    builder: (column) => ColumnFilters(column),
  );

  $$SessionsTableFilterComposer get sessionId {
    final $$SessionsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.sessionId,
      referencedTable: $db.sessions,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SessionsTableFilterComposer(
            $db: $db,
            $table: $db.sessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$FinalSourcesTableOrderingComposer
    extends Composer<_$EchoDatabase, $FinalSourcesTable> {
  $$FinalSourcesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get text$ => $composableBuilder(
    column: $table.text$,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get finalizedAtUtc => $composableBuilder(
    column: $table.finalizedAtUtc,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get captureStartedMonoNs => $composableBuilder(
    column: $table.captureStartedMonoNs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get captureEndedMonoNs => $composableBuilder(
    column: $table.captureEndedMonoNs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get capturedStartMs => $composableBuilder(
    column: $table.capturedStartMs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get capturedEndMs => $composableBuilder(
    column: $table.capturedEndMs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get chunkIds => $composableBuilder(
    column: $table.chunkIds,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get modelId => $composableBuilder(
    column: $table.modelId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get modelVersion => $composableBuilder(
    column: $table.modelVersion,
    builder: (column) => ColumnOrderings(column),
  );

  $$SessionsTableOrderingComposer get sessionId {
    final $$SessionsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.sessionId,
      referencedTable: $db.sessions,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SessionsTableOrderingComposer(
            $db: $db,
            $table: $db.sessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$FinalSourcesTableAnnotationComposer
    extends Composer<_$EchoDatabase, $FinalSourcesTable> {
  $$FinalSourcesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get text$ =>
      $composableBuilder(column: $table.text$, builder: (column) => column);

  GeneratedColumn<int> get finalizedAtUtc => $composableBuilder(
    column: $table.finalizedAtUtc,
    builder: (column) => column,
  );

  GeneratedColumn<int> get captureStartedMonoNs => $composableBuilder(
    column: $table.captureStartedMonoNs,
    builder: (column) => column,
  );

  GeneratedColumn<int> get captureEndedMonoNs => $composableBuilder(
    column: $table.captureEndedMonoNs,
    builder: (column) => column,
  );

  GeneratedColumn<int> get capturedStartMs => $composableBuilder(
    column: $table.capturedStartMs,
    builder: (column) => column,
  );

  GeneratedColumn<int> get capturedEndMs => $composableBuilder(
    column: $table.capturedEndMs,
    builder: (column) => column,
  );

  GeneratedColumn<String> get chunkIds =>
      $composableBuilder(column: $table.chunkIds, builder: (column) => column);

  GeneratedColumn<String> get modelId =>
      $composableBuilder(column: $table.modelId, builder: (column) => column);

  GeneratedColumn<String> get modelVersion => $composableBuilder(
    column: $table.modelVersion,
    builder: (column) => column,
  );

  $$SessionsTableAnnotationComposer get sessionId {
    final $$SessionsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.sessionId,
      referencedTable: $db.sessions,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SessionsTableAnnotationComposer(
            $db: $db,
            $table: $db.sessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$FinalSourcesTableTableManager
    extends
        RootTableManager<
          _$EchoDatabase,
          $FinalSourcesTable,
          FinalSourceRow,
          $$FinalSourcesTableFilterComposer,
          $$FinalSourcesTableOrderingComposer,
          $$FinalSourcesTableAnnotationComposer,
          $$FinalSourcesTableCreateCompanionBuilder,
          $$FinalSourcesTableUpdateCompanionBuilder,
          (FinalSourceRow, $$FinalSourcesTableReferences),
          FinalSourceRow,
          PrefetchHooks Function({bool sessionId})
        > {
  $$FinalSourcesTableTableManager(_$EchoDatabase db, $FinalSourcesTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$FinalSourcesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$FinalSourcesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$FinalSourcesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String> sessionId = const Value.absent(),
                Value<String> text$ = const Value.absent(),
                Value<int> finalizedAtUtc = const Value.absent(),
                Value<int> captureStartedMonoNs = const Value.absent(),
                Value<int> captureEndedMonoNs = const Value.absent(),
                Value<int> capturedStartMs = const Value.absent(),
                Value<int> capturedEndMs = const Value.absent(),
                Value<String> chunkIds = const Value.absent(),
                Value<String> modelId = const Value.absent(),
                Value<String> modelVersion = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => FinalSourcesCompanion(
                id: id,
                sessionId: sessionId,
                text$: text$,
                finalizedAtUtc: finalizedAtUtc,
                captureStartedMonoNs: captureStartedMonoNs,
                captureEndedMonoNs: captureEndedMonoNs,
                capturedStartMs: capturedStartMs,
                capturedEndMs: capturedEndMs,
                chunkIds: chunkIds,
                modelId: modelId,
                modelVersion: modelVersion,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required String sessionId,
                required String text$,
                required int finalizedAtUtc,
                required int captureStartedMonoNs,
                required int captureEndedMonoNs,
                required int capturedStartMs,
                required int capturedEndMs,
                required String chunkIds,
                required String modelId,
                required String modelVersion,
                Value<int> rowid = const Value.absent(),
              }) => FinalSourcesCompanion.insert(
                id: id,
                sessionId: sessionId,
                text$: text$,
                finalizedAtUtc: finalizedAtUtc,
                captureStartedMonoNs: captureStartedMonoNs,
                captureEndedMonoNs: captureEndedMonoNs,
                capturedStartMs: capturedStartMs,
                capturedEndMs: capturedEndMs,
                chunkIds: chunkIds,
                modelId: modelId,
                modelVersion: modelVersion,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$FinalSourcesTable, FinalSourceRow>(table),
                  $$FinalSourcesTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({sessionId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (sessionId) {
                      state = state.withJoin(
                        currentTable: table,
                        currentColumn: table.sessionId,
                        referencedTable: $$FinalSourcesTableReferences
                            ._sessionIdTable(db),
                        referencedColumn: $$FinalSourcesTableReferences
                            ._sessionIdTable(db)
                            .id,
                      ) as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$FinalSourcesTableProcessedTableManager =
    ProcessedTableManager<
      _$EchoDatabase,
      $FinalSourcesTable,
      FinalSourceRow,
      $$FinalSourcesTableFilterComposer,
      $$FinalSourcesTableOrderingComposer,
      $$FinalSourcesTableAnnotationComposer,
      $$FinalSourcesTableCreateCompanionBuilder,
      $$FinalSourcesTableUpdateCompanionBuilder,
      (FinalSourceRow, $$FinalSourcesTableReferences),
      FinalSourceRow,
      PrefetchHooks Function({bool sessionId})
    >;
typedef $$SegmentsTableCreateCompanionBuilder = SegmentsCompanion Function({
  required String id,
  required String sessionId,
  required int ordinal,
  required String kind,
  required String sourceFinalIds,
  required int capturedStartMs,
  required int capturedEndMs,
  required String chunkIds,
  Value<String?> gapReason,
  Value<int?> foldDueAtUtc,
  Value<String?> defaultVersionId,
  Value<int> rowid,
});
typedef $$SegmentsTableUpdateCompanionBuilder = SegmentsCompanion Function({
  Value<String> id,
  Value<String> sessionId,
  Value<int> ordinal,
  Value<String> kind,
  Value<String> sourceFinalIds,
  Value<int> capturedStartMs,
  Value<int> capturedEndMs,
  Value<String> chunkIds,
  Value<String?> gapReason,
  Value<int?> foldDueAtUtc,
  Value<String?> defaultVersionId,
  Value<int> rowid,
});

final class $$SegmentsTableReferences
    extends BaseReferences<_$EchoDatabase, $SegmentsTable, SegmentRow> {
  $$SegmentsTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static $SessionsTable _sessionIdTable(_$EchoDatabase db) =>
      db.sessions.createAlias('segments__session_id__sessions__id');

  $$SessionsTableProcessedTableManager get sessionId {
    final $_column = $_itemColumn<String>('session_id')!;

    final manager = $$SessionsTableTableManager(
      $_db,
      $_db.sessions,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_sessionIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }

  static MultiTypedResultKey<$VersionsTable, List<VersionRow>>
  _versionsRefsTable(_$EchoDatabase db) => MultiTypedResultKey.fromTable(
    db.versions,
    aliasName: 'segments__id__versions__segment_id',
  );

  $$VersionsTableProcessedTableManager get versionsRefs {
    final manager = $$VersionsTableTableManager(
      $_db,
      $_db.versions,
    ).filter((f) => f.segmentId.id.sqlEquals($_itemColumn<String>('id')!));

    final cache = $_typedResult.readTableOrNull(_versionsRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<$ArtifactsTable, List<ArtifactRow>>
  _artifactsRefsTable(_$EchoDatabase db) => MultiTypedResultKey.fromTable(
    db.artifacts,
    aliasName: 'segments__id__artifacts__segment_id',
  );

  $$ArtifactsTableProcessedTableManager get artifactsRefs {
    final manager = $$ArtifactsTableTableManager(
      $_db,
      $_db.artifacts,
    ).filter((f) => f.segmentId.id.sqlEquals($_itemColumn<String>('id')!));

    final cache = $_typedResult.readTableOrNull(_artifactsRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }
}

class $$SegmentsTableFilterComposer
    extends Composer<_$EchoDatabase, $SegmentsTable> {
  $$SegmentsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get ordinal => $composableBuilder(
    column: $table.ordinal,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get sourceFinalIds => $composableBuilder(
    column: $table.sourceFinalIds,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get capturedStartMs => $composableBuilder(
    column: $table.capturedStartMs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get capturedEndMs => $composableBuilder(
    column: $table.capturedEndMs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get chunkIds => $composableBuilder(
    column: $table.chunkIds,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get gapReason => $composableBuilder(
    column: $table.gapReason,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get foldDueAtUtc => $composableBuilder(
    column: $table.foldDueAtUtc,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get defaultVersionId => $composableBuilder(
    column: $table.defaultVersionId,
    builder: (column) => ColumnFilters(column),
  );

  $$SessionsTableFilterComposer get sessionId {
    final $$SessionsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.sessionId,
      referencedTable: $db.sessions,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SessionsTableFilterComposer(
            $db: $db,
            $table: $db.sessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  Expression<bool> versionsRefs(
    Expression<bool> Function($$VersionsTableFilterComposer f) f,
  ) {
    final $$VersionsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.versions,
      getReferencedColumn: (t) => t.segmentId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$VersionsTableFilterComposer(
            $db: $db,
            $table: $db.versions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> artifactsRefs(
    Expression<bool> Function($$ArtifactsTableFilterComposer f) f,
  ) {
    final $$ArtifactsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.artifacts,
      getReferencedColumn: (t) => t.segmentId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$ArtifactsTableFilterComposer(
            $db: $db,
            $table: $db.artifacts,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$SegmentsTableOrderingComposer
    extends Composer<_$EchoDatabase, $SegmentsTable> {
  $$SegmentsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get ordinal => $composableBuilder(
    column: $table.ordinal,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get sourceFinalIds => $composableBuilder(
    column: $table.sourceFinalIds,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get capturedStartMs => $composableBuilder(
    column: $table.capturedStartMs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get capturedEndMs => $composableBuilder(
    column: $table.capturedEndMs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get chunkIds => $composableBuilder(
    column: $table.chunkIds,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get gapReason => $composableBuilder(
    column: $table.gapReason,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get foldDueAtUtc => $composableBuilder(
    column: $table.foldDueAtUtc,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get defaultVersionId => $composableBuilder(
    column: $table.defaultVersionId,
    builder: (column) => ColumnOrderings(column),
  );

  $$SessionsTableOrderingComposer get sessionId {
    final $$SessionsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.sessionId,
      referencedTable: $db.sessions,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SessionsTableOrderingComposer(
            $db: $db,
            $table: $db.sessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$SegmentsTableAnnotationComposer
    extends Composer<_$EchoDatabase, $SegmentsTable> {
  $$SegmentsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<int> get ordinal =>
      $composableBuilder(column: $table.ordinal, builder: (column) => column);

  GeneratedColumn<String> get kind =>
      $composableBuilder(column: $table.kind, builder: (column) => column);

  GeneratedColumn<String> get sourceFinalIds => $composableBuilder(
    column: $table.sourceFinalIds,
    builder: (column) => column,
  );

  GeneratedColumn<int> get capturedStartMs => $composableBuilder(
    column: $table.capturedStartMs,
    builder: (column) => column,
  );

  GeneratedColumn<int> get capturedEndMs => $composableBuilder(
    column: $table.capturedEndMs,
    builder: (column) => column,
  );

  GeneratedColumn<String> get chunkIds =>
      $composableBuilder(column: $table.chunkIds, builder: (column) => column);

  GeneratedColumn<String> get gapReason =>
      $composableBuilder(column: $table.gapReason, builder: (column) => column);

  GeneratedColumn<int> get foldDueAtUtc => $composableBuilder(
    column: $table.foldDueAtUtc,
    builder: (column) => column,
  );

  GeneratedColumn<String> get defaultVersionId => $composableBuilder(
    column: $table.defaultVersionId,
    builder: (column) => column,
  );

  $$SessionsTableAnnotationComposer get sessionId {
    final $$SessionsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.sessionId,
      referencedTable: $db.sessions,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SessionsTableAnnotationComposer(
            $db: $db,
            $table: $db.sessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  Expression<T> versionsRefs<T extends Object>(
    Expression<T> Function($$VersionsTableAnnotationComposer a) f,
  ) {
    final $$VersionsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.versions,
      getReferencedColumn: (t) => t.segmentId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$VersionsTableAnnotationComposer(
            $db: $db,
            $table: $db.versions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<T> artifactsRefs<T extends Object>(
    Expression<T> Function($$ArtifactsTableAnnotationComposer a) f,
  ) {
    final $$ArtifactsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.artifacts,
      getReferencedColumn: (t) => t.segmentId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$ArtifactsTableAnnotationComposer(
            $db: $db,
            $table: $db.artifacts,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$SegmentsTableTableManager
    extends
        RootTableManager<
          _$EchoDatabase,
          $SegmentsTable,
          SegmentRow,
          $$SegmentsTableFilterComposer,
          $$SegmentsTableOrderingComposer,
          $$SegmentsTableAnnotationComposer,
          $$SegmentsTableCreateCompanionBuilder,
          $$SegmentsTableUpdateCompanionBuilder,
          (SegmentRow, $$SegmentsTableReferences),
          SegmentRow,
          PrefetchHooks Function({
            bool sessionId,
            bool versionsRefs,
            bool artifactsRefs,
          })
        > {
  $$SegmentsTableTableManager(_$EchoDatabase db, $SegmentsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$SegmentsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$SegmentsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$SegmentsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String> sessionId = const Value.absent(),
                Value<int> ordinal = const Value.absent(),
                Value<String> kind = const Value.absent(),
                Value<String> sourceFinalIds = const Value.absent(),
                Value<int> capturedStartMs = const Value.absent(),
                Value<int> capturedEndMs = const Value.absent(),
                Value<String> chunkIds = const Value.absent(),
                Value<String?> gapReason = const Value.absent(),
                Value<int?> foldDueAtUtc = const Value.absent(),
                Value<String?> defaultVersionId = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => SegmentsCompanion(
                id: id,
                sessionId: sessionId,
                ordinal: ordinal,
                kind: kind,
                sourceFinalIds: sourceFinalIds,
                capturedStartMs: capturedStartMs,
                capturedEndMs: capturedEndMs,
                chunkIds: chunkIds,
                gapReason: gapReason,
                foldDueAtUtc: foldDueAtUtc,
                defaultVersionId: defaultVersionId,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required String sessionId,
                required int ordinal,
                required String kind,
                required String sourceFinalIds,
                required int capturedStartMs,
                required int capturedEndMs,
                required String chunkIds,
                Value<String?> gapReason = const Value.absent(),
                Value<int?> foldDueAtUtc = const Value.absent(),
                Value<String?> defaultVersionId = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => SegmentsCompanion.insert(
                id: id,
                sessionId: sessionId,
                ordinal: ordinal,
                kind: kind,
                sourceFinalIds: sourceFinalIds,
                capturedStartMs: capturedStartMs,
                capturedEndMs: capturedEndMs,
                chunkIds: chunkIds,
                gapReason: gapReason,
                foldDueAtUtc: foldDueAtUtc,
                defaultVersionId: defaultVersionId,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$SegmentsTable, SegmentRow>(table),
                  $$SegmentsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback:
              ({
                sessionId = false,
                versionsRefs = false,
                artifactsRefs = false,
              }) {
                return PrefetchHooks(
                  db: db,
                  explicitlyWatchedTables: [
                    if (versionsRefs) db.versions,
                    if (artifactsRefs) db.artifacts,
                  ],
                  addJoins:
                      <
                        T extends TableManagerState<
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic
                        >
                      >(state) {
                        if (sessionId) {
                          state = state.withJoin(
                            currentTable: table,
                            currentColumn: table.sessionId,
                            referencedTable: $$SegmentsTableReferences
                                ._sessionIdTable(db),
                            referencedColumn: $$SegmentsTableReferences
                                ._sessionIdTable(db)
                                .id,
                          ) as T;
                        }

                        return state;
                      },
                  getPrefetchedDataCallback: (items) async {
                    return [
                      if (versionsRefs)
                        await $_getPrefetchedDataByColumns<
                          SegmentRow,
                          $SegmentsTable,
                          VersionRow,
                          $VersionsTable
                        >(
                          currentTable: table,
                          referencedTable: $$SegmentsTableReferences
                              ._versionsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$SegmentsTableReferences(
                                db,
                                table,
                                p0,
                              ).versionsRefs,
                          currentColumn: 'id',
                          referencedColumn: 'segment_id',
                          typedResults: items,
                        ),
                      if (artifactsRefs)
                        await $_getPrefetchedDataByColumns<
                          SegmentRow,
                          $SegmentsTable,
                          ArtifactRow,
                          $ArtifactsTable
                        >(
                          currentTable: table,
                          referencedTable: $$SegmentsTableReferences
                              ._artifactsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$SegmentsTableReferences(
                                db,
                                table,
                                p0,
                              ).artifactsRefs,
                          currentColumn: 'id',
                          referencedColumn: 'segment_id',
                          typedResults: items,
                        ),
                    ];
                  },
                );
              },
        ),
      );
}

typedef $$SegmentsTableProcessedTableManager =
    ProcessedTableManager<
      _$EchoDatabase,
      $SegmentsTable,
      SegmentRow,
      $$SegmentsTableFilterComposer,
      $$SegmentsTableOrderingComposer,
      $$SegmentsTableAnnotationComposer,
      $$SegmentsTableCreateCompanionBuilder,
      $$SegmentsTableUpdateCompanionBuilder,
      (SegmentRow, $$SegmentsTableReferences),
      SegmentRow,
      PrefetchHooks Function({
        bool sessionId,
        bool versionsRefs,
        bool artifactsRefs,
      })
    >;
typedef $$VersionsTableCreateCompanionBuilder = VersionsCompanion Function({
  required String id,
  required String segmentId,
  required String kind,
  required int layer,
  required String text$,
  Value<String?> parentVersionId,
  Value<String?> modelId,
  Value<String?> modelVersion,
  required int createdAtUtc,
  required int finalizedAtUtc,
  required int captureStartMs,
  required int captureEndMs,
  Value<String> sourceVersions,
  Value<String?> sourceSetHash,
  Value<int> rowid,
});
typedef $$VersionsTableUpdateCompanionBuilder = VersionsCompanion Function({
  Value<String> id,
  Value<String> segmentId,
  Value<String> kind,
  Value<int> layer,
  Value<String> text$,
  Value<String?> parentVersionId,
  Value<String?> modelId,
  Value<String?> modelVersion,
  Value<int> createdAtUtc,
  Value<int> finalizedAtUtc,
  Value<int> captureStartMs,
  Value<int> captureEndMs,
  Value<String> sourceVersions,
  Value<String?> sourceSetHash,
  Value<int> rowid,
});

final class $$VersionsTableReferences
    extends BaseReferences<_$EchoDatabase, $VersionsTable, VersionRow> {
  $$VersionsTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static $SegmentsTable _segmentIdTable(_$EchoDatabase db) =>
      db.segments.createAlias('versions__segment_id__segments__id');

  $$SegmentsTableProcessedTableManager get segmentId {
    final $_column = $_itemColumn<String>('segment_id')!;

    final manager = $$SegmentsTableTableManager(
      $_db,
      $_db.segments,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_segmentIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$VersionsTableFilterComposer
    extends Composer<_$EchoDatabase, $VersionsTable> {
  $$VersionsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get layer => $composableBuilder(
    column: $table.layer,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get text$ => $composableBuilder(
    column: $table.text$,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get parentVersionId => $composableBuilder(
    column: $table.parentVersionId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get modelId => $composableBuilder(
    column: $table.modelId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get modelVersion => $composableBuilder(
    column: $table.modelVersion,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get createdAtUtc => $composableBuilder(
    column: $table.createdAtUtc,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get finalizedAtUtc => $composableBuilder(
    column: $table.finalizedAtUtc,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get captureStartMs => $composableBuilder(
    column: $table.captureStartMs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get captureEndMs => $composableBuilder(
    column: $table.captureEndMs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get sourceVersions => $composableBuilder(
    column: $table.sourceVersions,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get sourceSetHash => $composableBuilder(
    column: $table.sourceSetHash,
    builder: (column) => ColumnFilters(column),
  );

  $$SegmentsTableFilterComposer get segmentId {
    final $$SegmentsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.segmentId,
      referencedTable: $db.segments,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SegmentsTableFilterComposer(
            $db: $db,
            $table: $db.segments,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$VersionsTableOrderingComposer
    extends Composer<_$EchoDatabase, $VersionsTable> {
  $$VersionsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get layer => $composableBuilder(
    column: $table.layer,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get text$ => $composableBuilder(
    column: $table.text$,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get parentVersionId => $composableBuilder(
    column: $table.parentVersionId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get modelId => $composableBuilder(
    column: $table.modelId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get modelVersion => $composableBuilder(
    column: $table.modelVersion,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get createdAtUtc => $composableBuilder(
    column: $table.createdAtUtc,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get finalizedAtUtc => $composableBuilder(
    column: $table.finalizedAtUtc,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get captureStartMs => $composableBuilder(
    column: $table.captureStartMs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get captureEndMs => $composableBuilder(
    column: $table.captureEndMs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get sourceVersions => $composableBuilder(
    column: $table.sourceVersions,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get sourceSetHash => $composableBuilder(
    column: $table.sourceSetHash,
    builder: (column) => ColumnOrderings(column),
  );

  $$SegmentsTableOrderingComposer get segmentId {
    final $$SegmentsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.segmentId,
      referencedTable: $db.segments,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SegmentsTableOrderingComposer(
            $db: $db,
            $table: $db.segments,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$VersionsTableAnnotationComposer
    extends Composer<_$EchoDatabase, $VersionsTable> {
  $$VersionsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get kind =>
      $composableBuilder(column: $table.kind, builder: (column) => column);

  GeneratedColumn<int> get layer =>
      $composableBuilder(column: $table.layer, builder: (column) => column);

  GeneratedColumn<String> get text$ =>
      $composableBuilder(column: $table.text$, builder: (column) => column);

  GeneratedColumn<String> get parentVersionId => $composableBuilder(
    column: $table.parentVersionId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get modelId =>
      $composableBuilder(column: $table.modelId, builder: (column) => column);

  GeneratedColumn<String> get modelVersion => $composableBuilder(
    column: $table.modelVersion,
    builder: (column) => column,
  );

  GeneratedColumn<int> get createdAtUtc => $composableBuilder(
    column: $table.createdAtUtc,
    builder: (column) => column,
  );

  GeneratedColumn<int> get finalizedAtUtc => $composableBuilder(
    column: $table.finalizedAtUtc,
    builder: (column) => column,
  );

  GeneratedColumn<int> get captureStartMs => $composableBuilder(
    column: $table.captureStartMs,
    builder: (column) => column,
  );

  GeneratedColumn<int> get captureEndMs => $composableBuilder(
    column: $table.captureEndMs,
    builder: (column) => column,
  );

  GeneratedColumn<String> get sourceVersions => $composableBuilder(
    column: $table.sourceVersions,
    builder: (column) => column,
  );

  GeneratedColumn<String> get sourceSetHash => $composableBuilder(
    column: $table.sourceSetHash,
    builder: (column) => column,
  );

  $$SegmentsTableAnnotationComposer get segmentId {
    final $$SegmentsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.segmentId,
      referencedTable: $db.segments,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SegmentsTableAnnotationComposer(
            $db: $db,
            $table: $db.segments,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$VersionsTableTableManager
    extends
        RootTableManager<
          _$EchoDatabase,
          $VersionsTable,
          VersionRow,
          $$VersionsTableFilterComposer,
          $$VersionsTableOrderingComposer,
          $$VersionsTableAnnotationComposer,
          $$VersionsTableCreateCompanionBuilder,
          $$VersionsTableUpdateCompanionBuilder,
          (VersionRow, $$VersionsTableReferences),
          VersionRow,
          PrefetchHooks Function({bool segmentId})
        > {
  $$VersionsTableTableManager(_$EchoDatabase db, $VersionsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$VersionsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$VersionsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$VersionsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String> segmentId = const Value.absent(),
                Value<String> kind = const Value.absent(),
                Value<int> layer = const Value.absent(),
                Value<String> text$ = const Value.absent(),
                Value<String?> parentVersionId = const Value.absent(),
                Value<String?> modelId = const Value.absent(),
                Value<String?> modelVersion = const Value.absent(),
                Value<int> createdAtUtc = const Value.absent(),
                Value<int> finalizedAtUtc = const Value.absent(),
                Value<int> captureStartMs = const Value.absent(),
                Value<int> captureEndMs = const Value.absent(),
                Value<String> sourceVersions = const Value.absent(),
                Value<String?> sourceSetHash = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => VersionsCompanion(
                id: id,
                segmentId: segmentId,
                kind: kind,
                layer: layer,
                text$: text$,
                parentVersionId: parentVersionId,
                modelId: modelId,
                modelVersion: modelVersion,
                createdAtUtc: createdAtUtc,
                finalizedAtUtc: finalizedAtUtc,
                captureStartMs: captureStartMs,
                captureEndMs: captureEndMs,
                sourceVersions: sourceVersions,
                sourceSetHash: sourceSetHash,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required String segmentId,
                required String kind,
                required int layer,
                required String text$,
                Value<String?> parentVersionId = const Value.absent(),
                Value<String?> modelId = const Value.absent(),
                Value<String?> modelVersion = const Value.absent(),
                required int createdAtUtc,
                required int finalizedAtUtc,
                required int captureStartMs,
                required int captureEndMs,
                Value<String> sourceVersions = const Value.absent(),
                Value<String?> sourceSetHash = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => VersionsCompanion.insert(
                id: id,
                segmentId: segmentId,
                kind: kind,
                layer: layer,
                text$: text$,
                parentVersionId: parentVersionId,
                modelId: modelId,
                modelVersion: modelVersion,
                createdAtUtc: createdAtUtc,
                finalizedAtUtc: finalizedAtUtc,
                captureStartMs: captureStartMs,
                captureEndMs: captureEndMs,
                sourceVersions: sourceVersions,
                sourceSetHash: sourceSetHash,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$VersionsTable, VersionRow>(table),
                  $$VersionsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({segmentId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (segmentId) {
                      state = state.withJoin(
                        currentTable: table,
                        currentColumn: table.segmentId,
                        referencedTable: $$VersionsTableReferences
                            ._segmentIdTable(db),
                        referencedColumn: $$VersionsTableReferences
                            ._segmentIdTable(db)
                            .id,
                      ) as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$VersionsTableProcessedTableManager =
    ProcessedTableManager<
      _$EchoDatabase,
      $VersionsTable,
      VersionRow,
      $$VersionsTableFilterComposer,
      $$VersionsTableOrderingComposer,
      $$VersionsTableAnnotationComposer,
      $$VersionsTableCreateCompanionBuilder,
      $$VersionsTableUpdateCompanionBuilder,
      (VersionRow, $$VersionsTableReferences),
      VersionRow,
      PrefetchHooks Function({bool segmentId})
    >;
typedef $$ArtifactsTableCreateCompanionBuilder = ArtifactsCompanion Function({
  required String id,
  required String sessionId,
  Value<String?> segmentId,
  required int layer,
  required String state,
  required String text$,
  required String sourceVersions,
  required String sourceSetHash,
  required String promptVersion,
  required String model,
  required int createdAtUtc,
  Value<String?> parentVersionId,
  Value<String> citations,
  Value<String?> errorCategory,
  Value<int> rowid,
});
typedef $$ArtifactsTableUpdateCompanionBuilder = ArtifactsCompanion Function({
  Value<String> id,
  Value<String> sessionId,
  Value<String?> segmentId,
  Value<int> layer,
  Value<String> state,
  Value<String> text$,
  Value<String> sourceVersions,
  Value<String> sourceSetHash,
  Value<String> promptVersion,
  Value<String> model,
  Value<int> createdAtUtc,
  Value<String?> parentVersionId,
  Value<String> citations,
  Value<String?> errorCategory,
  Value<int> rowid,
});

final class $$ArtifactsTableReferences
    extends BaseReferences<_$EchoDatabase, $ArtifactsTable, ArtifactRow> {
  $$ArtifactsTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static $SessionsTable _sessionIdTable(_$EchoDatabase db) =>
      db.sessions.createAlias('artifacts__session_id__sessions__id');

  $$SessionsTableProcessedTableManager get sessionId {
    final $_column = $_itemColumn<String>('session_id')!;

    final manager = $$SessionsTableTableManager(
      $_db,
      $_db.sessions,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_sessionIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }

  static $SegmentsTable _segmentIdTable(_$EchoDatabase db) =>
      db.segments.createAlias('artifacts__segment_id__segments__id');

  $$SegmentsTableProcessedTableManager? get segmentId {
    final $_column = $_itemColumn<String>('segment_id');
    if ($_column == null) return null;
    final manager = $$SegmentsTableTableManager(
      $_db,
      $_db.segments,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_segmentIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$ArtifactsTableFilterComposer
    extends Composer<_$EchoDatabase, $ArtifactsTable> {
  $$ArtifactsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get layer => $composableBuilder(
    column: $table.layer,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get state => $composableBuilder(
    column: $table.state,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get text$ => $composableBuilder(
    column: $table.text$,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get sourceVersions => $composableBuilder(
    column: $table.sourceVersions,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get sourceSetHash => $composableBuilder(
    column: $table.sourceSetHash,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get promptVersion => $composableBuilder(
    column: $table.promptVersion,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get model => $composableBuilder(
    column: $table.model,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get createdAtUtc => $composableBuilder(
    column: $table.createdAtUtc,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get parentVersionId => $composableBuilder(
    column: $table.parentVersionId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get citations => $composableBuilder(
    column: $table.citations,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get errorCategory => $composableBuilder(
    column: $table.errorCategory,
    builder: (column) => ColumnFilters(column),
  );

  $$SessionsTableFilterComposer get sessionId {
    final $$SessionsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.sessionId,
      referencedTable: $db.sessions,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SessionsTableFilterComposer(
            $db: $db,
            $table: $db.sessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$SegmentsTableFilterComposer get segmentId {
    final $$SegmentsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.segmentId,
      referencedTable: $db.segments,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SegmentsTableFilterComposer(
            $db: $db,
            $table: $db.segments,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$ArtifactsTableOrderingComposer
    extends Composer<_$EchoDatabase, $ArtifactsTable> {
  $$ArtifactsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get layer => $composableBuilder(
    column: $table.layer,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get state => $composableBuilder(
    column: $table.state,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get text$ => $composableBuilder(
    column: $table.text$,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get sourceVersions => $composableBuilder(
    column: $table.sourceVersions,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get sourceSetHash => $composableBuilder(
    column: $table.sourceSetHash,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get promptVersion => $composableBuilder(
    column: $table.promptVersion,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get model => $composableBuilder(
    column: $table.model,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get createdAtUtc => $composableBuilder(
    column: $table.createdAtUtc,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get parentVersionId => $composableBuilder(
    column: $table.parentVersionId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get citations => $composableBuilder(
    column: $table.citations,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get errorCategory => $composableBuilder(
    column: $table.errorCategory,
    builder: (column) => ColumnOrderings(column),
  );

  $$SessionsTableOrderingComposer get sessionId {
    final $$SessionsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.sessionId,
      referencedTable: $db.sessions,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SessionsTableOrderingComposer(
            $db: $db,
            $table: $db.sessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$SegmentsTableOrderingComposer get segmentId {
    final $$SegmentsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.segmentId,
      referencedTable: $db.segments,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SegmentsTableOrderingComposer(
            $db: $db,
            $table: $db.segments,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$ArtifactsTableAnnotationComposer
    extends Composer<_$EchoDatabase, $ArtifactsTable> {
  $$ArtifactsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<int> get layer =>
      $composableBuilder(column: $table.layer, builder: (column) => column);

  GeneratedColumn<String> get state =>
      $composableBuilder(column: $table.state, builder: (column) => column);

  GeneratedColumn<String> get text$ =>
      $composableBuilder(column: $table.text$, builder: (column) => column);

  GeneratedColumn<String> get sourceVersions => $composableBuilder(
    column: $table.sourceVersions,
    builder: (column) => column,
  );

  GeneratedColumn<String> get sourceSetHash => $composableBuilder(
    column: $table.sourceSetHash,
    builder: (column) => column,
  );

  GeneratedColumn<String> get promptVersion => $composableBuilder(
    column: $table.promptVersion,
    builder: (column) => column,
  );

  GeneratedColumn<String> get model =>
      $composableBuilder(column: $table.model, builder: (column) => column);

  GeneratedColumn<int> get createdAtUtc => $composableBuilder(
    column: $table.createdAtUtc,
    builder: (column) => column,
  );

  GeneratedColumn<String> get parentVersionId => $composableBuilder(
    column: $table.parentVersionId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get citations =>
      $composableBuilder(column: $table.citations, builder: (column) => column);

  GeneratedColumn<String> get errorCategory => $composableBuilder(
    column: $table.errorCategory,
    builder: (column) => column,
  );

  $$SessionsTableAnnotationComposer get sessionId {
    final $$SessionsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.sessionId,
      referencedTable: $db.sessions,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SessionsTableAnnotationComposer(
            $db: $db,
            $table: $db.sessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$SegmentsTableAnnotationComposer get segmentId {
    final $$SegmentsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.segmentId,
      referencedTable: $db.segments,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SegmentsTableAnnotationComposer(
            $db: $db,
            $table: $db.segments,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$ArtifactsTableTableManager
    extends
        RootTableManager<
          _$EchoDatabase,
          $ArtifactsTable,
          ArtifactRow,
          $$ArtifactsTableFilterComposer,
          $$ArtifactsTableOrderingComposer,
          $$ArtifactsTableAnnotationComposer,
          $$ArtifactsTableCreateCompanionBuilder,
          $$ArtifactsTableUpdateCompanionBuilder,
          (ArtifactRow, $$ArtifactsTableReferences),
          ArtifactRow,
          PrefetchHooks Function({bool sessionId, bool segmentId})
        > {
  $$ArtifactsTableTableManager(_$EchoDatabase db, $ArtifactsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$ArtifactsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$ArtifactsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$ArtifactsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String> sessionId = const Value.absent(),
                Value<String?> segmentId = const Value.absent(),
                Value<int> layer = const Value.absent(),
                Value<String> state = const Value.absent(),
                Value<String> text$ = const Value.absent(),
                Value<String> sourceVersions = const Value.absent(),
                Value<String> sourceSetHash = const Value.absent(),
                Value<String> promptVersion = const Value.absent(),
                Value<String> model = const Value.absent(),
                Value<int> createdAtUtc = const Value.absent(),
                Value<String?> parentVersionId = const Value.absent(),
                Value<String> citations = const Value.absent(),
                Value<String?> errorCategory = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => ArtifactsCompanion(
                id: id,
                sessionId: sessionId,
                segmentId: segmentId,
                layer: layer,
                state: state,
                text$: text$,
                sourceVersions: sourceVersions,
                sourceSetHash: sourceSetHash,
                promptVersion: promptVersion,
                model: model,
                createdAtUtc: createdAtUtc,
                parentVersionId: parentVersionId,
                citations: citations,
                errorCategory: errorCategory,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required String sessionId,
                Value<String?> segmentId = const Value.absent(),
                required int layer,
                required String state,
                required String text$,
                required String sourceVersions,
                required String sourceSetHash,
                required String promptVersion,
                required String model,
                required int createdAtUtc,
                Value<String?> parentVersionId = const Value.absent(),
                Value<String> citations = const Value.absent(),
                Value<String?> errorCategory = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => ArtifactsCompanion.insert(
                id: id,
                sessionId: sessionId,
                segmentId: segmentId,
                layer: layer,
                state: state,
                text$: text$,
                sourceVersions: sourceVersions,
                sourceSetHash: sourceSetHash,
                promptVersion: promptVersion,
                model: model,
                createdAtUtc: createdAtUtc,
                parentVersionId: parentVersionId,
                citations: citations,
                errorCategory: errorCategory,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$ArtifactsTable, ArtifactRow>(table),
                  $$ArtifactsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({sessionId = false, segmentId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (sessionId) {
                      state = state.withJoin(
                        currentTable: table,
                        currentColumn: table.sessionId,
                        referencedTable: $$ArtifactsTableReferences
                            ._sessionIdTable(db),
                        referencedColumn: $$ArtifactsTableReferences
                            ._sessionIdTable(db)
                            .id,
                      ) as T;
                    }
                    if (segmentId) {
                      state = state.withJoin(
                        currentTable: table,
                        currentColumn: table.segmentId,
                        referencedTable: $$ArtifactsTableReferences
                            ._segmentIdTable(db),
                        referencedColumn: $$ArtifactsTableReferences
                            ._segmentIdTable(db)
                            .id,
                      ) as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$ArtifactsTableProcessedTableManager =
    ProcessedTableManager<
      _$EchoDatabase,
      $ArtifactsTable,
      ArtifactRow,
      $$ArtifactsTableFilterComposer,
      $$ArtifactsTableOrderingComposer,
      $$ArtifactsTableAnnotationComposer,
      $$ArtifactsTableCreateCompanionBuilder,
      $$ArtifactsTableUpdateCompanionBuilder,
      (ArtifactRow, $$ArtifactsTableReferences),
      ArtifactRow,
      PrefetchHooks Function({bool sessionId, bool segmentId})
    >;
typedef $$JobsTableCreateCompanionBuilder = JobsCompanion Function({
  required String id,
  required String idempotencyKey,
  required String sessionId,
  required String targetId,
  required int layer,
  required String state,
  required String sourceVersions,
  required String sourceSetHash,
  required String promptVersion,
  required String model,
  required int createdAtUtc,
  required int updatedAtUtc,
  Value<int> attempts,
  Value<String?> errorCategory,
  Value<int> rowid,
});
typedef $$JobsTableUpdateCompanionBuilder = JobsCompanion Function({
  Value<String> id,
  Value<String> idempotencyKey,
  Value<String> sessionId,
  Value<String> targetId,
  Value<int> layer,
  Value<String> state,
  Value<String> sourceVersions,
  Value<String> sourceSetHash,
  Value<String> promptVersion,
  Value<String> model,
  Value<int> createdAtUtc,
  Value<int> updatedAtUtc,
  Value<int> attempts,
  Value<String?> errorCategory,
  Value<int> rowid,
});

final class $$JobsTableReferences
    extends BaseReferences<_$EchoDatabase, $JobsTable, JobRow> {
  $$JobsTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static $SessionsTable _sessionIdTable(_$EchoDatabase db) =>
      db.sessions.createAlias('jobs__session_id__sessions__id');

  $$SessionsTableProcessedTableManager get sessionId {
    final $_column = $_itemColumn<String>('session_id')!;

    final manager = $$SessionsTableTableManager(
      $_db,
      $_db.sessions,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_sessionIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$JobsTableFilterComposer extends Composer<_$EchoDatabase, $JobsTable> {
  $$JobsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get idempotencyKey => $composableBuilder(
    column: $table.idempotencyKey,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get targetId => $composableBuilder(
    column: $table.targetId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get layer => $composableBuilder(
    column: $table.layer,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get state => $composableBuilder(
    column: $table.state,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get sourceVersions => $composableBuilder(
    column: $table.sourceVersions,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get sourceSetHash => $composableBuilder(
    column: $table.sourceSetHash,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get promptVersion => $composableBuilder(
    column: $table.promptVersion,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get model => $composableBuilder(
    column: $table.model,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get createdAtUtc => $composableBuilder(
    column: $table.createdAtUtc,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get updatedAtUtc => $composableBuilder(
    column: $table.updatedAtUtc,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get attempts => $composableBuilder(
    column: $table.attempts,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get errorCategory => $composableBuilder(
    column: $table.errorCategory,
    builder: (column) => ColumnFilters(column),
  );

  $$SessionsTableFilterComposer get sessionId {
    final $$SessionsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.sessionId,
      referencedTable: $db.sessions,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SessionsTableFilterComposer(
            $db: $db,
            $table: $db.sessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$JobsTableOrderingComposer extends Composer<_$EchoDatabase, $JobsTable> {
  $$JobsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get idempotencyKey => $composableBuilder(
    column: $table.idempotencyKey,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get targetId => $composableBuilder(
    column: $table.targetId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get layer => $composableBuilder(
    column: $table.layer,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get state => $composableBuilder(
    column: $table.state,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get sourceVersions => $composableBuilder(
    column: $table.sourceVersions,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get sourceSetHash => $composableBuilder(
    column: $table.sourceSetHash,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get promptVersion => $composableBuilder(
    column: $table.promptVersion,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get model => $composableBuilder(
    column: $table.model,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get createdAtUtc => $composableBuilder(
    column: $table.createdAtUtc,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get updatedAtUtc => $composableBuilder(
    column: $table.updatedAtUtc,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get attempts => $composableBuilder(
    column: $table.attempts,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get errorCategory => $composableBuilder(
    column: $table.errorCategory,
    builder: (column) => ColumnOrderings(column),
  );

  $$SessionsTableOrderingComposer get sessionId {
    final $$SessionsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.sessionId,
      referencedTable: $db.sessions,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SessionsTableOrderingComposer(
            $db: $db,
            $table: $db.sessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$JobsTableAnnotationComposer
    extends Composer<_$EchoDatabase, $JobsTable> {
  $$JobsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get idempotencyKey => $composableBuilder(
    column: $table.idempotencyKey,
    builder: (column) => column,
  );

  GeneratedColumn<String> get targetId =>
      $composableBuilder(column: $table.targetId, builder: (column) => column);

  GeneratedColumn<int> get layer =>
      $composableBuilder(column: $table.layer, builder: (column) => column);

  GeneratedColumn<String> get state =>
      $composableBuilder(column: $table.state, builder: (column) => column);

  GeneratedColumn<String> get sourceVersions => $composableBuilder(
    column: $table.sourceVersions,
    builder: (column) => column,
  );

  GeneratedColumn<String> get sourceSetHash => $composableBuilder(
    column: $table.sourceSetHash,
    builder: (column) => column,
  );

  GeneratedColumn<String> get promptVersion => $composableBuilder(
    column: $table.promptVersion,
    builder: (column) => column,
  );

  GeneratedColumn<String> get model =>
      $composableBuilder(column: $table.model, builder: (column) => column);

  GeneratedColumn<int> get createdAtUtc => $composableBuilder(
    column: $table.createdAtUtc,
    builder: (column) => column,
  );

  GeneratedColumn<int> get updatedAtUtc => $composableBuilder(
    column: $table.updatedAtUtc,
    builder: (column) => column,
  );

  GeneratedColumn<int> get attempts =>
      $composableBuilder(column: $table.attempts, builder: (column) => column);

  GeneratedColumn<String> get errorCategory => $composableBuilder(
    column: $table.errorCategory,
    builder: (column) => column,
  );

  $$SessionsTableAnnotationComposer get sessionId {
    final $$SessionsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.sessionId,
      referencedTable: $db.sessions,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SessionsTableAnnotationComposer(
            $db: $db,
            $table: $db.sessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$JobsTableTableManager
    extends
        RootTableManager<
          _$EchoDatabase,
          $JobsTable,
          JobRow,
          $$JobsTableFilterComposer,
          $$JobsTableOrderingComposer,
          $$JobsTableAnnotationComposer,
          $$JobsTableCreateCompanionBuilder,
          $$JobsTableUpdateCompanionBuilder,
          (JobRow, $$JobsTableReferences),
          JobRow,
          PrefetchHooks Function({bool sessionId})
        > {
  $$JobsTableTableManager(_$EchoDatabase db, $JobsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$JobsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$JobsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$JobsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String> idempotencyKey = const Value.absent(),
                Value<String> sessionId = const Value.absent(),
                Value<String> targetId = const Value.absent(),
                Value<int> layer = const Value.absent(),
                Value<String> state = const Value.absent(),
                Value<String> sourceVersions = const Value.absent(),
                Value<String> sourceSetHash = const Value.absent(),
                Value<String> promptVersion = const Value.absent(),
                Value<String> model = const Value.absent(),
                Value<int> createdAtUtc = const Value.absent(),
                Value<int> updatedAtUtc = const Value.absent(),
                Value<int> attempts = const Value.absent(),
                Value<String?> errorCategory = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => JobsCompanion(
                id: id,
                idempotencyKey: idempotencyKey,
                sessionId: sessionId,
                targetId: targetId,
                layer: layer,
                state: state,
                sourceVersions: sourceVersions,
                sourceSetHash: sourceSetHash,
                promptVersion: promptVersion,
                model: model,
                createdAtUtc: createdAtUtc,
                updatedAtUtc: updatedAtUtc,
                attempts: attempts,
                errorCategory: errorCategory,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required String idempotencyKey,
                required String sessionId,
                required String targetId,
                required int layer,
                required String state,
                required String sourceVersions,
                required String sourceSetHash,
                required String promptVersion,
                required String model,
                required int createdAtUtc,
                required int updatedAtUtc,
                Value<int> attempts = const Value.absent(),
                Value<String?> errorCategory = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => JobsCompanion.insert(
                id: id,
                idempotencyKey: idempotencyKey,
                sessionId: sessionId,
                targetId: targetId,
                layer: layer,
                state: state,
                sourceVersions: sourceVersions,
                sourceSetHash: sourceSetHash,
                promptVersion: promptVersion,
                model: model,
                createdAtUtc: createdAtUtc,
                updatedAtUtc: updatedAtUtc,
                attempts: attempts,
                errorCategory: errorCategory,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$JobsTable, JobRow>(table),
                  $$JobsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({sessionId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (sessionId) {
                      state = state.withJoin(
                        currentTable: table,
                        currentColumn: table.sessionId,
                        referencedTable: $$JobsTableReferences._sessionIdTable(
                          db,
                        ),
                        referencedColumn: $$JobsTableReferences
                            ._sessionIdTable(db)
                            .id,
                      ) as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$JobsTableProcessedTableManager =
    ProcessedTableManager<
      _$EchoDatabase,
      $JobsTable,
      JobRow,
      $$JobsTableFilterComposer,
      $$JobsTableOrderingComposer,
      $$JobsTableAnnotationComposer,
      $$JobsTableCreateCompanionBuilder,
      $$JobsTableUpdateCompanionBuilder,
      (JobRow, $$JobsTableReferences),
      JobRow,
      PrefetchHooks Function({bool sessionId})
    >;
typedef $$TitlesTableCreateCompanionBuilder = TitlesCompanion Function({
  required String sessionId,
  required String placeholder,
  Value<bool> pendingFirstReveal,
  Value<String?> latestTitle,
  Value<String?> latestVersionId,
  Value<int?> revealedAtUtc,
  Value<String?> sourceVersions,
  Value<String?> sourceSetHash,
  Value<int?> generatedAtUtc,
  Value<int> rowid,
});
typedef $$TitlesTableUpdateCompanionBuilder = TitlesCompanion Function({
  Value<String> sessionId,
  Value<String> placeholder,
  Value<bool> pendingFirstReveal,
  Value<String?> latestTitle,
  Value<String?> latestVersionId,
  Value<int?> revealedAtUtc,
  Value<String?> sourceVersions,
  Value<String?> sourceSetHash,
  Value<int?> generatedAtUtc,
  Value<int> rowid,
});

final class $$TitlesTableReferences
    extends BaseReferences<_$EchoDatabase, $TitlesTable, TitleRow> {
  $$TitlesTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static $SessionsTable _sessionIdTable(_$EchoDatabase db) =>
      db.sessions.createAlias('titles__session_id__sessions__id');

  $$SessionsTableProcessedTableManager get sessionId {
    final $_column = $_itemColumn<String>('session_id')!;

    final manager = $$SessionsTableTableManager(
      $_db,
      $_db.sessions,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_sessionIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$TitlesTableFilterComposer
    extends Composer<_$EchoDatabase, $TitlesTable> {
  $$TitlesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get placeholder => $composableBuilder(
    column: $table.placeholder,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get pendingFirstReveal => $composableBuilder(
    column: $table.pendingFirstReveal,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get latestTitle => $composableBuilder(
    column: $table.latestTitle,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get latestVersionId => $composableBuilder(
    column: $table.latestVersionId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get revealedAtUtc => $composableBuilder(
    column: $table.revealedAtUtc,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get sourceVersions => $composableBuilder(
    column: $table.sourceVersions,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get sourceSetHash => $composableBuilder(
    column: $table.sourceSetHash,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get generatedAtUtc => $composableBuilder(
    column: $table.generatedAtUtc,
    builder: (column) => ColumnFilters(column),
  );

  $$SessionsTableFilterComposer get sessionId {
    final $$SessionsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.sessionId,
      referencedTable: $db.sessions,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SessionsTableFilterComposer(
            $db: $db,
            $table: $db.sessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$TitlesTableOrderingComposer
    extends Composer<_$EchoDatabase, $TitlesTable> {
  $$TitlesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get placeholder => $composableBuilder(
    column: $table.placeholder,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get pendingFirstReveal => $composableBuilder(
    column: $table.pendingFirstReveal,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get latestTitle => $composableBuilder(
    column: $table.latestTitle,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get latestVersionId => $composableBuilder(
    column: $table.latestVersionId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get revealedAtUtc => $composableBuilder(
    column: $table.revealedAtUtc,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get sourceVersions => $composableBuilder(
    column: $table.sourceVersions,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get sourceSetHash => $composableBuilder(
    column: $table.sourceSetHash,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get generatedAtUtc => $composableBuilder(
    column: $table.generatedAtUtc,
    builder: (column) => ColumnOrderings(column),
  );

  $$SessionsTableOrderingComposer get sessionId {
    final $$SessionsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.sessionId,
      referencedTable: $db.sessions,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SessionsTableOrderingComposer(
            $db: $db,
            $table: $db.sessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$TitlesTableAnnotationComposer
    extends Composer<_$EchoDatabase, $TitlesTable> {
  $$TitlesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get placeholder => $composableBuilder(
    column: $table.placeholder,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get pendingFirstReveal => $composableBuilder(
    column: $table.pendingFirstReveal,
    builder: (column) => column,
  );

  GeneratedColumn<String> get latestTitle => $composableBuilder(
    column: $table.latestTitle,
    builder: (column) => column,
  );

  GeneratedColumn<String> get latestVersionId => $composableBuilder(
    column: $table.latestVersionId,
    builder: (column) => column,
  );

  GeneratedColumn<int> get revealedAtUtc => $composableBuilder(
    column: $table.revealedAtUtc,
    builder: (column) => column,
  );

  GeneratedColumn<String> get sourceVersions => $composableBuilder(
    column: $table.sourceVersions,
    builder: (column) => column,
  );

  GeneratedColumn<String> get sourceSetHash => $composableBuilder(
    column: $table.sourceSetHash,
    builder: (column) => column,
  );

  GeneratedColumn<int> get generatedAtUtc => $composableBuilder(
    column: $table.generatedAtUtc,
    builder: (column) => column,
  );

  $$SessionsTableAnnotationComposer get sessionId {
    final $$SessionsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.sessionId,
      referencedTable: $db.sessions,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$SessionsTableAnnotationComposer(
            $db: $db,
            $table: $db.sessions,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$TitlesTableTableManager
    extends
        RootTableManager<
          _$EchoDatabase,
          $TitlesTable,
          TitleRow,
          $$TitlesTableFilterComposer,
          $$TitlesTableOrderingComposer,
          $$TitlesTableAnnotationComposer,
          $$TitlesTableCreateCompanionBuilder,
          $$TitlesTableUpdateCompanionBuilder,
          (TitleRow, $$TitlesTableReferences),
          TitleRow,
          PrefetchHooks Function({bool sessionId})
        > {
  $$TitlesTableTableManager(_$EchoDatabase db, $TitlesTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$TitlesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$TitlesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$TitlesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> sessionId = const Value.absent(),
                Value<String> placeholder = const Value.absent(),
                Value<bool> pendingFirstReveal = const Value.absent(),
                Value<String?> latestTitle = const Value.absent(),
                Value<String?> latestVersionId = const Value.absent(),
                Value<int?> revealedAtUtc = const Value.absent(),
                Value<String?> sourceVersions = const Value.absent(),
                Value<String?> sourceSetHash = const Value.absent(),
                Value<int?> generatedAtUtc = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => TitlesCompanion(
                sessionId: sessionId,
                placeholder: placeholder,
                pendingFirstReveal: pendingFirstReveal,
                latestTitle: latestTitle,
                latestVersionId: latestVersionId,
                revealedAtUtc: revealedAtUtc,
                sourceVersions: sourceVersions,
                sourceSetHash: sourceSetHash,
                generatedAtUtc: generatedAtUtc,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String sessionId,
                required String placeholder,
                Value<bool> pendingFirstReveal = const Value.absent(),
                Value<String?> latestTitle = const Value.absent(),
                Value<String?> latestVersionId = const Value.absent(),
                Value<int?> revealedAtUtc = const Value.absent(),
                Value<String?> sourceVersions = const Value.absent(),
                Value<String?> sourceSetHash = const Value.absent(),
                Value<int?> generatedAtUtc = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => TitlesCompanion.insert(
                sessionId: sessionId,
                placeholder: placeholder,
                pendingFirstReveal: pendingFirstReveal,
                latestTitle: latestTitle,
                latestVersionId: latestVersionId,
                revealedAtUtc: revealedAtUtc,
                sourceVersions: sourceVersions,
                sourceSetHash: sourceSetHash,
                generatedAtUtc: generatedAtUtc,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$TitlesTable, TitleRow>(table),
                  $$TitlesTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({sessionId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (sessionId) {
                      state = state.withJoin(
                        currentTable: table,
                        currentColumn: table.sessionId,
                        referencedTable: $$TitlesTableReferences
                            ._sessionIdTable(db),
                        referencedColumn: $$TitlesTableReferences
                            ._sessionIdTable(db)
                            .id,
                      ) as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$TitlesTableProcessedTableManager =
    ProcessedTableManager<
      _$EchoDatabase,
      $TitlesTable,
      TitleRow,
      $$TitlesTableFilterComposer,
      $$TitlesTableOrderingComposer,
      $$TitlesTableAnnotationComposer,
      $$TitlesTableCreateCompanionBuilder,
      $$TitlesTableUpdateCompanionBuilder,
      (TitleRow, $$TitlesTableReferences),
      TitleRow,
      PrefetchHooks Function({bool sessionId})
    >;
typedef $$SettingsTableCreateCompanionBuilder = SettingsCompanion Function({
  required String settingKey,
  required String settingValue,
  Value<int> rowid,
});
typedef $$SettingsTableUpdateCompanionBuilder = SettingsCompanion Function({
  Value<String> settingKey,
  Value<String> settingValue,
  Value<int> rowid,
});

class $$SettingsTableFilterComposer
    extends Composer<_$EchoDatabase, $SettingsTable> {
  $$SettingsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get settingKey => $composableBuilder(
    column: $table.settingKey,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get settingValue => $composableBuilder(
    column: $table.settingValue,
    builder: (column) => ColumnFilters(column),
  );
}

class $$SettingsTableOrderingComposer
    extends Composer<_$EchoDatabase, $SettingsTable> {
  $$SettingsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get settingKey => $composableBuilder(
    column: $table.settingKey,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get settingValue => $composableBuilder(
    column: $table.settingValue,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$SettingsTableAnnotationComposer
    extends Composer<_$EchoDatabase, $SettingsTable> {
  $$SettingsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get settingKey => $composableBuilder(
    column: $table.settingKey,
    builder: (column) => column,
  );

  GeneratedColumn<String> get settingValue => $composableBuilder(
    column: $table.settingValue,
    builder: (column) => column,
  );
}

class $$SettingsTableTableManager
    extends
        RootTableManager<
          _$EchoDatabase,
          $SettingsTable,
          SettingRow,
          $$SettingsTableFilterComposer,
          $$SettingsTableOrderingComposer,
          $$SettingsTableAnnotationComposer,
          $$SettingsTableCreateCompanionBuilder,
          $$SettingsTableUpdateCompanionBuilder,
          (
            SettingRow,
            BaseReferences<_$EchoDatabase, $SettingsTable, SettingRow>,
          ),
          SettingRow,
          PrefetchHooks Function()
        > {
  $$SettingsTableTableManager(_$EchoDatabase db, $SettingsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$SettingsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$SettingsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$SettingsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> settingKey = const Value.absent(),
                Value<String> settingValue = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => SettingsCompanion(
                settingKey: settingKey,
                settingValue: settingValue,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String settingKey,
                required String settingValue,
                Value<int> rowid = const Value.absent(),
              }) => SettingsCompanion.insert(
                settingKey: settingKey,
                settingValue: settingValue,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$SettingsTable, SettingRow>(table),
                  BaseReferences<_$EchoDatabase, $SettingsTable, SettingRow>(
                    db,
                    table,
                    e,
                  ),
                ),
              )
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$SettingsTableProcessedTableManager =
    ProcessedTableManager<
      _$EchoDatabase,
      $SettingsTable,
      SettingRow,
      $$SettingsTableFilterComposer,
      $$SettingsTableOrderingComposer,
      $$SettingsTableAnnotationComposer,
      $$SettingsTableCreateCompanionBuilder,
      $$SettingsTableUpdateCompanionBuilder,
      (SettingRow, BaseReferences<_$EchoDatabase, $SettingsTable, SettingRow>),
      SettingRow,
      PrefetchHooks Function()
    >;

class $EchoDatabaseManager {
  final _$EchoDatabase _db;
  $EchoDatabaseManager(this._db);
  $$SessionsTableTableManager get sessions =>
      $$SessionsTableTableManager(_db, _db.sessions);
  $$AudioChunksTableTableManager get audioChunks =>
      $$AudioChunksTableTableManager(_db, _db.audioChunks);
  $$FinalSourcesTableTableManager get finalSources =>
      $$FinalSourcesTableTableManager(_db, _db.finalSources);
  $$SegmentsTableTableManager get segments =>
      $$SegmentsTableTableManager(_db, _db.segments);
  $$VersionsTableTableManager get versions =>
      $$VersionsTableTableManager(_db, _db.versions);
  $$ArtifactsTableTableManager get artifacts =>
      $$ArtifactsTableTableManager(_db, _db.artifacts);
  $$JobsTableTableManager get jobs => $$JobsTableTableManager(_db, _db.jobs);
  $$TitlesTableTableManager get titles =>
      $$TitlesTableTableManager(_db, _db.titles);
  $$SettingsTableTableManager get settings =>
      $$SettingsTableTableManager(_db, _db.settings);
}
