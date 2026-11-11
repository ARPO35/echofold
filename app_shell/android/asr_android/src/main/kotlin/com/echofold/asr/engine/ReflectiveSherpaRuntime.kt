package com.echofold.asr.engine

import com.echofold.core.asr.engine.AsrEngineException
import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * 反射适配 sherpa-onnx Android AAR。
 *
 * Maven Central 没有官方 `com.k2-fsa.sherpa.onnx` Android 构件（当前环境仅有第三方重打包 AAR），
 * 因此本模块不引入编译期依赖，运行时通过反射调用 AAR 中的 Kotlin API：
 * `FeatureConfig`、`OnlineTransducerModelConfig`、`OnlineModelConfig`、`OnlineRecognizerConfig`、
 * `OnlineRecognizer`、`OnlineStream`。
 *
 * 配置对象用“Kotlin 默认参数合成构造 + setter”装配，避免把某个版本的参数顺序写死；缺少必需
 * setter 或类时抛 [AsrEngineException] 并指出缺失项。该适配未在真机验证（见交付报告）。
 */
class ReflectiveSherpaRuntime(
    private val classLoader: ClassLoader =
        ReflectiveSherpaRuntime::class.java.classLoader ?: ClassLoader.getSystemClassLoader(),
) : SherpaRuntime {
    override fun createRecognizer(paths: SherpaModelPaths, threads: Int, provider: String): SherpaRecognizer {
        try {
            val featureConfig = newConfig(FEATURE_CONFIG)
            setOptional(featureConfig, "setSampleRate", SAMPLE_RATE)
            setOptional(featureConfig, "setFeatureDim", FEATURE_DIM)

            val transducer = newConfig(TRANSDUCER_CONFIG)
            setRequired(transducer, "setEncoder", paths.encoder.absolutePath)
            setRequired(transducer, "setDecoder", paths.decoder.absolutePath)
            setRequired(transducer, "setJoiner", paths.joiner.absolutePath)

            val modelConfig = newConfig(MODEL_CONFIG)
            setRequired(modelConfig, "setTransducer", transducer)
            setOptional(modelConfig, "setTokens", paths.tokens.absolutePath)
            setOptional(modelConfig, "setNumThreads", threads)
            setOptional(modelConfig, "setProvider", provider)
            setOptional(modelConfig, "setDebug", false)

            val recognizerConfig = newConfig(RECOGNIZER_CONFIG)
            setRequired(recognizerConfig, "setFeatConfig", featureConfig)
            setRequired(recognizerConfig, "setModelConfig", modelConfig)
            setOptional(recognizerConfig, "setDecodingMethod", "greedy_search")
            setOptional(recognizerConfig, "setEnableEndpoint", true)

            val recognizerClass = loadClass(RECOGNIZER)
            val constructor =
                recognizerClass.constructors.firstOrNull {
                    it.parameterCount == 1 && it.parameterTypes[0].isAssignableFrom(recognizerConfig.javaClass)
                } ?: throw AsrEngineException("sherpa-onnx OnlineRecognizer 缺少接收 OnlineRecognizerConfig 的构造器")
            val recognizer = newInstance(constructor, arrayOf(recognizerConfig))
            val stream = invokeRequired(recognizer, "createStream", emptyArray(), "OnlineRecognizer.createStream")
            return ReflectiveSherpaRecognizer(recognizer, stream)
        } catch (e: AsrEngineException) {
            throw e
        } catch (e: ClassNotFoundException) {
            throw AsrEngineException(
                "未找到 sherpa-onnx 运行时类 ${e.message}；需要在 APK 中打包 sherpa-onnx AAR",
                e,
            )
        } catch (e: Throwable) {
            throw AsrEngineException("sherpa-onnx 反射初始化失败：${describe(e)}", e)
        }
    }

    private fun newConfig(className: String): Any {
        val clazz = loadClass(className)
        clazz.declaredConstructors.firstOrNull { it.parameterCount == 0 }?.let { return newInstance(it, emptyArray()) }
        val defaultConstructor =
            clazz.declaredConstructors.firstOrNull { constructor ->
                constructor.parameterTypes.isNotEmpty() &&
                    constructor.parameterTypes.last().name == DEFAULT_CONSTRUCTOR_MARKER
            } ?: throw AsrEngineException("sherpa-onnx 配置类 $className 缺少默认参数构造器")
        val parameterTypes = defaultConstructor.parameterTypes
        val maskCount = parameterTypes.size - 1
        val arguments = arrayOfNulls<Any?>(parameterTypes.size)
        for (index in 0 until maskCount) {
            arguments[index] = if (parameterTypes[index] == Integer.TYPE) -1 else 0
        }
        return newInstance(defaultConstructor, arguments)
    }

    private fun setRequired(target: Any, setter: String, value: Any) {
        if (!setProperty(target, setter, value)) {
            throw AsrEngineException("sherpa-onnx ${target.javaClass.simpleName} 缺少必需方法 $setter")
        }
    }

    private fun setOptional(target: Any, setter: String, value: Any) {
        setProperty(target, setter, value)
    }

    /** @return 是否找到并执行了 setter */
    private fun setProperty(target: Any, setter: String, value: Any): Boolean {
        val method =
            target.javaClass.methods.firstOrNull {
                it.name == setter && it.parameterCount == 1 && accepts(it.parameterTypes[0], value)
            } ?: return false
        try {
            method.invoke(target, value)
        } catch (e: InvocationTargetException) {
            throw AsrEngineException("sherpa-onnx ${target.javaClass.simpleName}.$setter 调用失败：${describe(e)}", e)
        } catch (e: IllegalAccessException) {
            throw AsrEngineException("sherpa-onnx ${target.javaClass.simpleName}.$setter 不可访问", e)
        }
        return true
    }

    private fun accepts(parameterType: Class<*>, value: Any): Boolean {
        if (value is Int) return parameterType == Int::class.javaPrimitiveType || parameterType == Int::class.javaObjectType
        if (value is Boolean) return parameterType == Boolean::class.javaPrimitiveType || parameterType == Boolean::class.javaObjectType
        if (value is String) return parameterType.isAssignableFrom(String::class.java)
        return parameterType.isAssignableFrom(value.javaClass)
    }

    private fun newInstance(constructor: Constructor<*>, arguments: Array<Any?>): Any {
        constructor.isAccessible = true
        return try {
            constructor.newInstance(*arguments)
        } catch (e: InvocationTargetException) {
            throw AsrEngineException("sherpa-onnx ${constructor.declaringClass.simpleName} 构造失败：${describe(e)}", e)
        }
    }

    private fun loadClass(name: String): Class<*> = Class.forName(name, false, classLoader)

    private fun invokeRequired(target: Any, methodName: String, arguments: Array<Any?>, description: String): Any {
        val method = target.javaClass.methods.firstOrNull { it.name == methodName && matches(it, arguments) }
            ?: throw AsrEngineException("sherpa-onnx 缺少方法 $description")
        return invoke(method, target, arguments, description)
            ?: throw AsrEngineException("sherpa-onnx 方法 $description 返回 null")
    }

    private fun matches(method: Method, arguments: Array<Any?>): Boolean {
        if (method.parameterCount != arguments.size) return false
        return method.parameterTypes.indices.all { index ->
            val argument = arguments[index]
            if (argument == null) !method.parameterTypes[index].isPrimitive else accepts(method.parameterTypes[index], argument)
        }
    }

    private fun invoke(method: Method, target: Any?, arguments: Array<Any?>, description: String): Any? {
        try {
            method.isAccessible = true
            return method.invoke(target, *arguments)
        } catch (e: InvocationTargetException) {
            throw AsrEngineException("sherpa-onnx 调用 $description 失败：${describe(e)}", e)
        } catch (e: IllegalAccessException) {
            throw AsrEngineException("sherpa-onnx 方法 $description 不可访问", e)
        }
    }

    private fun describe(throwable: Throwable): String {
        val cause = if (throwable is InvocationTargetException) throwable.targetException ?: throwable else throwable
        return cause.message ?: cause.javaClass.simpleName
    }

    /** 绑定单个 OnlineStream 的识别器。 */
    private inner class ReflectiveSherpaRecognizer(
        private val recognizer: Any,
        private val stream: Any,
    ) : SherpaRecognizer {
        private val streamClass: Class<*> = stream.javaClass
        private val acceptWaveformMethod: Method =
            stream.javaClass.methods.firstOrNull {
                it.name == "acceptWaveform" && it.parameterCount == 2 && it.parameterTypes[0] == FloatArray::class.java
            } ?: throw AsrEngineException("sherpa-onnx OnlineStream 缺少 acceptWaveform(FloatArray, Int)")
        private val isReadyMethod: Method? = recognizer.javaClass.methods.firstOrNull { it.name == "isReady" && it.parameterCount == 1 }
        private val isEndpointMethod: Method =
            recognizer.javaClass.methods.firstOrNull { it.name == "isEndpoint" && it.parameterCount == 1 }
                ?: throw AsrEngineException("sherpa-onnx OnlineRecognizer 缺少 isEndpoint")
        private val getResultMethod: Method =
            recognizer.javaClass.methods.firstOrNull { it.name == "getResult" && it.parameterCount == 1 }
                ?: throw AsrEngineException("sherpa-onnx OnlineRecognizer 缺少 getResult")
        private val resetMethod: Method? = recognizer.javaClass.methods.firstOrNull { it.name == "reset" && it.parameterCount == 1 }
        private val decodeStreamsMethod: Method? =
            recognizer.javaClass.methods.firstOrNull { it.name == "decodeStreams" && it.parameterCount == 1 && it.parameterTypes[0].isArray }
        private val resultTextMethod: Method? =
            getResultMethod.returnType.methods.firstOrNull { it.name == "getText" && it.parameterCount == 0 }
        private var closed = false

        override fun acceptWaveform(samples: FloatArray, sampleRate: Int) {
            ensureOpen()
            invoke(acceptWaveformMethod, stream, arrayOf(samples, sampleRate), "OnlineStream.acceptWaveform")
            decodeReady()
        }

        override fun isEndpoint(): Boolean {
            ensureOpen()
            val value = invoke(isEndpointMethod, recognizer, arrayOf(stream), "OnlineRecognizer.isEndpoint")
            return value as? Boolean ?: throw AsrEngineException("sherpa-onnx isEndpoint 返回值不是 Boolean")
        }

        override fun resultText(): String {
            ensureOpen()
            val result = invoke(getResultMethod, recognizer, arrayOf(stream), "OnlineRecognizer.getResult")
            val method = resultTextMethod
                ?: throw AsrEngineException("sherpa-onnx OnlineRecognizerResult 缺少 getText")
            val text = invoke(method, result, emptyArray(), "OnlineRecognizerResult.getText")
            return text as? String ?: throw AsrEngineException("sherpa-onnx 识别文本不是 String")
        }

        override fun reset() {
            ensureOpen()
            if (resetMethod == null) throw AsrEngineException("sherpa-onnx OnlineRecognizer 缺少 reset")
            invoke(resetMethod, recognizer, arrayOf(stream), "OnlineRecognizer.reset")
        }

        override fun close() {
            if (closed) return
            closed = true
            release(stream)
            release(recognizer)
        }

        private fun decodeReady() {
            val decode = decodeStreamsMethod ?: return
            val streams = ReflectArray.newInstance(streamClass, 1)
            ReflectArray.set(streams, 0, stream)
            if (isReadyMethod == null) {
                invoke(decode, recognizer, arrayOf(streams), "OnlineRecognizer.decodeStreams")
                return
            }
            var steps = 0
            while (invoke(isReadyMethod, recognizer, arrayOf(stream), "OnlineRecognizer.isReady") == true) {
                invoke(decode, recognizer, arrayOf(streams), "OnlineRecognizer.decodeStreams")
                steps += 1
                if (steps > MAX_DECODE_STEPS) {
                    throw AsrEngineException("sherpa-onnx decode 循环未收敛（超过 $MAX_DECODE_STEPS 步）")
                }
            }
        }

        private fun release(target: Any) {
            val method = target.javaClass.methods.firstOrNull { it.name == "release" && it.parameterCount == 0 } ?: return
            try {
                method.isAccessible = true
                method.invoke(target)
            } catch (e: Exception) {
                // 释放属于收尾路径：失败没有可恢复动作，也不得让 close 破坏会话清理。
                // 缺失/失败的 release 在下次进程重启时由 native 资源回收覆盖。
            }
        }

        private fun ensureOpen() {
            if (closed) throw AsrEngineException("sherpa-onnx 识别器已关闭")
        }
    }

    private companion object {
        const val FEATURE_CONFIG = "com.k2fsa.sherpa.onnx.FeatureConfig"
        const val TRANSDUCER_CONFIG = "com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig"
        const val MODEL_CONFIG = "com.k2fsa.sherpa.onnx.OnlineModelConfig"
        const val RECOGNIZER_CONFIG = "com.k2fsa.sherpa.onnx.OnlineRecognizerConfig"
        const val RECOGNIZER = "com.k2fsa.sherpa.onnx.OnlineRecognizer"
        const val DEFAULT_CONSTRUCTOR_MARKER = "kotlin.jvm.internal.DefaultConstructorMarker"
        const val SAMPLE_RATE = 16_000
        const val FEATURE_DIM = 80
        const val MAX_DECODE_STEPS = 100_000
    }
}
