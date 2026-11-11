# Processing Orchestration

## ADDED Requirements

### Requirement: 足够成段即预处理

系统 SHALL 在 FINAL 文字足够形成段落时立即创建校订与要点预处理任务，MUST NOT 把自动折叠延迟当作预处理延迟。

#### Scenario: 折叠设置为十分钟

- **WHEN** 某段文字在一分钟内已经形成有效 FINAL 语义段
- **THEN** 系统立即提交 L1/L2 预处理，而不是十分钟后才提交
- **AND** 十分钟配置只影响视觉折叠到期时间

#### Scenario: 暂停或停止冲刷任务

- **WHEN** 用户暂停或结束会话且仍有未处理 FINAL
- **THEN** 系统把符合段落条件的内容送入有界队列
- **AND** 任务可跨页面和界面重建继续，但不重复提交相同源版本

### Requirement: 手机直连 OpenAI-compatible BYOK

系统 SHALL 由 Android 直接请求用户配置的 Base URL、API Key 和模型名所对应的 OpenAI-compatible 文本接口，首版 MUST NOT 依赖自建中继或远端 ASR。Base URL 及所有实际 LLM 请求 SHALL 使用 HTTPS；系统 MUST NOT 向明文 HTTP 端点发送请求、API Key 或 Authorization。遇到重定向时，系统 SHALL 先验证目标仍为 HTTPS；只要主机、端口或 scheme 与原目标不同，MUST NOT 转发 Authorization、API Key 或其他敏感请求头。对于跨域重定向，客户端 SHALL 拒绝继续请求并提示用户显式重新配置新端点，而不是静默接受。

#### Scenario: 用户启用端点

- **WHEN** 用户输入 HTTPS 兼容端点、密钥和模型并确认文字离机说明
- **THEN** 系统执行能力/鉴权检查并保存非敏感配置
- **AND** 后续请求从手机直接发往所显示主机和端口

#### Scenario: 明文 HTTP 端点或降级重定向

- **WHEN** 用户配置 HTTP Base URL，或 HTTPS 端点返回指向 HTTP 的重定向
- **THEN** 系统在发送离机文字和敏感请求头之前拒绝该请求并显示明确错误
- **AND** 不把原 API Key、Authorization 或正文转发到降级目标

#### Scenario: 跨域或跨端口重定向

- **WHEN** 原 HTTPS 端点返回指向另一主机、端口或 scheme 的重定向
- **THEN** 客户端丢弃 Authorization、API Key 和其他敏感请求头并拒绝自动继续
- **AND** 用户看到新目标并显式重新配置前，不向新目标发送原任务正文或密钥

#### Scenario: 同源 HTTPS 重定向

- **WHEN** 重定向目标仍为同一主机和端口的 HTTPS
- **THEN** 客户端可在严格路径与重定向次数限制内处理
- **AND** 不得因重定向放宽原始来源版本、响应来源回显或 TLS 校验

#### Scenario: 未配置或关闭 BYOK

- **WHEN** 用户未配置或主动关闭 BYOK
- **THEN** 录音、本地 ASR、原始文字、手动编辑和离线重识别继续工作
- **AND** 系统不发起隐含的备选云端请求

### Requirement: 最小文字上下文与数据分类

系统 SHALL 只发送当前任务必需的稳定文字、有限上下文和结构化指令，MUST NOT 发送音频、API Key 到非目标主机或无关会话全文。

#### Scenario: 校订一个段落

- **WHEN** 创建 L1 校订请求
- **THEN** 请求包含该段来源版本、必要相邻上下文和允许操作
- **AND** 请求不包含音频路径、可下载音频 URL、其他无关会话或设备密钥

#### Scenario: 生成会话摘要

- **WHEN** 创建 L3 摘要请求
- **THEN** 系统优先使用已验证 L2 与必要引用，控制最大字符/token 预算
- **AND** 超出预算时按话题分批聚合而非无限截断证据映射

### Requirement: 结构化输出与客户端校验

系统 SHALL 要求模型返回结构化操作或带引用产物，并 SHALL 在客户端验证 schema、有序来源集、来源哈希和引用范围后提交。每个 `SemanticArtifact` 和 `ProcessingJob` SHALL 保存非空、按段序去重的有序 `sourceVersions[]`，每项同时标识来源节点/段和不可变版本 ID；`sourceSetHash` SHALL 由保留数组顺序的规范化 `sourceVersions[]` 序列化计算。规范化 SHALL 固定字段、字段顺序、UTF-8 编码和无关空白处理，MUST NOT 对来源数组排序或以集合语义消除顺序差异；哈希相同之外仍 SHALL 逐项比较来源 ID、版本 ID 和顺序。

#### Scenario: 端点支持结构化输出

- **WHEN** 端点声明支持 JSON Schema 或等效 structured output
- **THEN** 适配器使用该能力请求规定结构
- **AND** 客户端仍执行完整校验，不把协议声明当作内容正确证明

#### Scenario: 返回 Markdown 或无效引用

- **WHEN** 响应无法解析为约定 JSON 或引用越界
- **THEN** 系统进行有限格式/内容修复重试
- **AND** 重试后仍失败则保留旧版本并记录脱敏错误类别

#### Scenario: 多源响应回显不一致

- **WHEN** 响应回显的 `sourceVersions[]` 缺少来源、增加来源、替换任一版本、改变顺序，或其 `sourceSetHash` 与请求不一致
- **THEN** 客户端拒绝整个响应，不提交部分产物，也不自动重排后接受
- **AND** 即使响应引用范围全部有效且哈希值被伪造为请求值，逐项来源检查仍必须失败

#### Scenario: L1 结构合法不等于语义正确

- **WHEN** L1 响应结构、操作目标和引用均合法
- **THEN** 客户端只能提交其结构校验结果和相应版本，不宣称已自动证明语义蕴含、事实保持或引用支持
- **AND** 数字、否定、人名、行动关系和无依据事实仍须通过冻结人工参考集的发布前人工验收，自动检查不得替代该边界

### Requirement: 幂等、缓存与有序源版本检查

系统 SHALL 为每个任务生成包含任务类型、规范化 `sourceSetHash`、有序 `sourceVersions[]`、提示词版本、模型和参数的幂等键，并 SHALL 在缓存查询和提交前逐项重新检查当前来源版本及其顺序。缓存命中和提交接受 SHALL 同时满足来源集哈希一致、逐项一致和来源仍有效，MUST NOT 只凭任务 ID、单个来源哈希、来源数量或集合相同复用产物。

#### Scenario: 网络超时后重试

- **WHEN** 客户端未确定请求是否成功并执行重试
- **THEN** 相同幂等键只产生一个有效版本
- **AND** 系统不向阅读流重复追加同一文字

#### Scenario: 缓存命中

- **WHEN** 有序 `sourceVersions[]`、`sourceSetHash` 和全部生成参数均相同的有效产物已存在，且所有来源仍为当前有效版本
- **THEN** 系统复用该产物，不重复调用端点

#### Scenario: 多源顺序不同的缓存查找

- **WHEN** 两个任务包含相同来源 ID 和版本但数组顺序不同，或任一来源版本不同
- **THEN** 规范化 `sourceSetHash` 不同且缓存不命中
- **AND** 系统不得将同集合不同顺序的任务合并、去重或复用彼此的产物

#### Scenario: 多源之一的旧结果迟到

- **WHEN** 用户编辑、重识别或上游重生成使多源集合中的任一来源版本变化，随后基于旧集合的任务结果返回
- **THEN** 客户端通过逐项比较和 `sourceSetHash` 拒绝该过期结果，不覆盖当前版本，不提交部分有效结果，也不把它当成当前缓存命中
- **AND** 只有基于当前完整有序来源集的新任务可生成有效版本

#### Scenario: 来源版本未变但顺序改变

- **WHEN** 上游调整多源段顺序而未改变各来源版本，旧任务结果随后返回
- **THEN** 客户端因顺序不同拒绝旧结果
- **AND** 系统不借助数组排序、单一哈希覆盖或忽略顺序的比较绕过该检查

### Requirement: 有界队列与优先级

系统 SHALL 按音频与 ASR、实时 UI、用户操作、标题、L1/L2、L3 的优先级分配资源，并 SHALL 使用有界队列和背压避免无限积压。

#### Scenario: LLM 持续不可用

- **WHEN** 网络或端点长时间失败且新 FINAL 持续到来
- **THEN** 系统合并、推迟或淘汰可重建任务并保持队列有界
- **AND** 音频采集、ASR 和实时文字不被 LLM 重试阻塞

#### Scenario: 用户主动立即整理

- **WHEN** 用户对失败或待处理段执行立即整理
- **THEN** 该任务获得用户操作优先级
- **AND** 相同幂等键不造成重复运行

### Requirement: 自动标题生成

系统 SHALL 在会话获得足量稳定文字后生成简洁标题，并 SHALL 将标题生成和标题首次展示分成两个独立状态。尚未 revealed 的 `TitlePresentation` SHALL 把其唯一 `pendingFirstReveal` 继承给最新有效标题版本，而不是为每次标题生成新增首次展示资格；已经 revealed 后的标题更新 MUST NOT 重新设置首次展示资格。

#### Scenario: 标题生成成功

- **WHEN** 会话已有足够 L1/L2 内容且 BYOK 可用
- **THEN** 系统生成带 `sourceSetHash` 的标题版本；若从未完成首次展示，则其展示状态为 `pendingFirstReveal`
- **AND** 系统不直接在不可见列表项上消费首次动画

#### Scenario: 首展前多代标题先后完成

- **WHEN** 标题首次展示尚未完成，新来源标题成为最新有效版本
- **THEN** `pendingFirstReveal` 转移到该最新有效版本，旧标题不再拥有另一份待首展资格
- **AND** 列表第一次实际可见时只展示最新有效版本，并在完成后提交对应 `revealedAt`

#### Scenario: 首展后来源更新

- **WHEN** 某代标题已经完成首次展示，随后来源更新产生新有效标题
- **THEN** 新版本以普通更新展示且不获得 `pendingFirstReveal`
- **AND** 列表重建、页面切换或重启不会使它再次执行首次动画

#### Scenario: BYOK 不可用

- **WHEN** 无法生成自动标题
- **THEN** 会话保留日期时间占位标题并可稍后重试
- **AND** 录音、转写和其他会话标题不受影响

#### Scenario: 来源文字被用户编辑

- **WHEN** 标题来源版本因用户编辑失效
- **THEN** 系统生成新的标题任务并保留版本关系
- **AND** 后续内容更新不重复冒充首次生成动画

### Requirement: 过期重建与用户版本优先

系统 SHALL 根据依赖图只重建受影响的 L2/L3 和标题，并 SHALL 保证用户校订版本不被自动任务覆盖。

#### Scenario: 单段被编辑

- **WHEN** 一个长会话中只有一段用户校订变化
- **THEN** 系统使该段下游及引用它的聚合产物过期
- **AND** 不无关地重写整个会话全部段落

#### Scenario: 重识别版本恢复语义处理

- **WHEN** 普通段或 GAP 段产生成功有效的新重识别版本，旧 ASR 版本仍是该段当前 L1/L2 的来源
- **THEN** 依赖旧版本的候选产物按影响范围过期，新语义任务使用当前完整有序 `sourceVersions[]`
- **AND** 原始 GAP 只保留为不可变证据，不作为普通文字送入 LLM；新任务不得把字面量 `...` 复制成 L1 或 L2，失败时保留原有效内容和缺口展示

#### Scenario: 重识别结果已经过期

- **WHEN** 同一来源区间已有更新的有效重识别版本，较早提交的重识别结果随后完成
- **THEN** 系统不改变当前默认展示，也不以其来源集刷新缓存或提交下游产物
- **AND** 旧结果仅可按版本历史保留，原 GAP 和所有既有有效版本继续可回查
