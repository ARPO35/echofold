package com.echofold.core.model

/**
 * 音频块状态。
 *
 * 状态迁移以共享契约第 5 节为准：`PLACEHOLDER → OPEN → SEALING → SEALED`，
 * 失败进入 `SEAL_FAILED`，恢复扫描只会落到 `RECOVERABLE` 或 `CORRUPT`。
 */
enum class ChunkState {
    /** 身份与临时路径已持久化，尚无已验证的可分发编码资源；不得当作可播放块。 */
    PLACEHOLDER,

    /** 编码资源有效并接受同一块切点内的 PCM；会话同一时刻只允许一个 OPEN 块。 */
    OPEN,

    /** 已切断新分发，正在排空、结束容器、同步与校验；不可重新打开追加。 */
    SEALING,

    /** 时长、校验、索引与最终路径均已提交；不得原地覆盖。 */
    SEALED,

    /** 封存未完整提交，必须保留临时文件与失败阶段，不得当作完整块。 */
    SEAL_FAILED,

    /** 恢复后仅部分可证明一致，可索引可解码前缀并量化边界。 */
    RECOVERABLE,

    /** 无法证明可解码或时间映射不可信，不得用于默认回听、导出成功或重识别输入。 */
    CORRUPT,
}

/** 录音会话状态。 */
enum class SessionStatus {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPING,

    /** 低存储按顺序停止成功后进入；失败必须停留在 BLOCKED 而不是伪造暂停。 */
    LOW_STORAGE_PAUSED,
    FAILED,
}

/** 采集分发状态；`BLOCKED` 表示停止向 ASR/AAC 分发新 PCM。 */
enum class DispatchState { RUNNING, BLOCKED }

/** 转写段类型：普通文字段或显式转写缺口。 */
enum class SegmentKind { TEXT, GAP }

/** 转写版本类型；L0 证据与各类生成物共用同一版本表。 */
enum class VersionKind { ASR, GAP, AI, USER }

/** 来源版本类型，参与 `sourceSetHash` 的规范 JSON。 */
enum class SourceKind { FINAL, AI, USER, GAP }

/** 转写缺口的唯一原因：ASR 排队时延超过跳读阈值。 */
enum class GapReason { ASR_OVERLOAD }

/**
 * 时钟锚点质量。
 *
 * `DEVICE_BOOT_BASED` 为正常设备开机单调时钟；`RESTART_DEGRADED` 表示跨重启后
 * 旧单调值只能与原锚点共同解释，不得与其他会话的裸数值比较。
 */
enum class ClockQuality { DEVICE_BOOT_BASED, RESTART_DEGRADED }
