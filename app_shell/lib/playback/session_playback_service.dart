/// 使用 just_audio/audio_session 的跨块回听服务。
///
/// 录音中不得调用本服务；由控制器在调用前拦截并提示用户先手动暂停。
library;

import 'dart:async';

import 'package:audio_session/audio_session.dart';
import 'package:just_audio/just_audio.dart';

import 'playback_planner.dart';

/// 回听失败；`code` 供诊断，`message` 为用户可读原因。
final class PlaybackException implements Exception {
  const PlaybackException(this.code, this.message);

  final String code;
  final String message;

  @override
  String toString() => 'PlaybackException($code): $message';
}

/// 跨块播放器。
final class SessionPlaybackService {
  SessionPlaybackService({AudioPlayer? player}) : _player = player ?? AudioPlayer();

  final AudioPlayer _player;
  bool _sessionConfigured = false;

  Stream<PlayerState> get playerState => _player.playerStateStream;

  bool get isPlaying => _player.playing;

  Duration get position => _player.position;

  /// 载入并播放一个计划；`complete=false` 时仍播放可验证片段，调用方负责展示缺口。
  Future<void> play(PlaybackPlan plan) async {
    if (plan.clips.isEmpty) {
      throw const PlaybackException('NO_AUDIO', '该区间没有可播放的音频证据');
    }
    await _configureSession();
    final List<AudioSource> sources = plan.clips
        .map(
          (PlaybackClip clip) => ClippingAudioSource(
            child: AudioSource.file(clip.path),
            start: Duration(milliseconds: clip.startMs),
            end: Duration(milliseconds: clip.endMs),
            tag: clip.chunkId,
          ),
        )
        .toList(growable: false);
    try {
      await _player.setAudioSources(
        sources,
        preload: true,
        initialIndex: 0,
        initialPosition: Duration.zero,
      );
      await _player.play();
    } on PlayerException catch (error) {
      throw PlaybackException('DECODE_FAILED', error.message ?? '音频无法解码');
    } on PlayerInterruptedException {
      throw const PlaybackException('INTERRUPTED', '播放已被新的请求打断');
    }
  }

  Future<void> pause() => _player.pause();

  Future<void> resume() => _player.play();

  Future<void> stop() async {
    await _player.stop();
    await _player.seek(Duration.zero);
  }

  Future<void> dispose() => _player.dispose();

  Future<void> _configureSession() async {
    if (_sessionConfigured) {
      return;
    }
    final AudioSession session = await AudioSession.instance;
    await session.configure(const AudioSessionConfiguration.speech());
    _sessionConfigured = true;
  }
}
