/// BYOK 密钥存储边界。
///
/// 密钥只允许通过 Android Keystore 支持的 `flutter_secure_storage` 保存；
/// 本文件不提供明文数据库、日志或导出实现。测试可注入 [InMemorySecretStore]。
library;

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// 密钥读写接口。
///
/// 实现不得把密钥写入 `toString`、日志、drift 或导出模型；调用方只按需读取。
abstract interface class SecretStore {
  /// 读取当前密钥；不存在时返回 `null`。
  Future<String?> readApiKey();

  /// 写入密钥；空字符串视为非法并抛出 [ArgumentError]。
  Future<void> writeApiKey(String apiKey);

  /// 删除密钥；不存在时也必须成功。
  Future<void> deleteApiKey();
}

/// 基于 `flutter_secure_storage` 的默认实现。
final class SecureStorageSecretStore implements SecretStore {
  SecureStorageSecretStore({
    FlutterSecureStorage? storage,
    this.keyName = defaultKeyName,
  }) : _storage = storage ?? const FlutterSecureStorage();

  /// 默认的 Keystore 条目名。
  static const String defaultKeyName = 'echofold.byok.api_key';

  final FlutterSecureStorage _storage;
  final String keyName;

  @override
  Future<String?> readApiKey() => _storage.read(key: keyName);

  @override
  Future<void> writeApiKey(String apiKey) {
    if (apiKey.isEmpty) {
      throw ArgumentError.value(apiKey, 'apiKey', '密钥不能为空');
    }
    return _storage.write(key: keyName, value: apiKey);
  }

  @override
  Future<void> deleteApiKey() => _storage.delete(key: keyName);

  @override
  String toString() => 'SecureStorageSecretStore(keyName: $keyName)';
}

/// 仅用于测试与内存装配的密钥存储。
///
/// 真实应用必须使用 [SecureStorageSecretStore]，不得把此实现作为生产后端。
final class InMemorySecretStore implements SecretStore {
  InMemorySecretStore({String? initialApiKey}) : _apiKey = initialApiKey;

  String? _apiKey;

  @override
  Future<String?> readApiKey() async => _apiKey;

  @override
  Future<void> writeApiKey(String apiKey) async {
    if (apiKey.isEmpty) {
      throw ArgumentError.value(apiKey, 'apiKey', '密钥不能为空');
    }
    _apiKey = apiKey;
  }

  @override
  Future<void> deleteApiKey() async {
    _apiKey = null;
  }

  @override
  String toString() => 'InMemorySecretStore(hasKey: ${_apiKey != null})';
}
