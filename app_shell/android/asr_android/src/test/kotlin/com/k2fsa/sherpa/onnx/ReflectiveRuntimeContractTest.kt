package com.k2fsa.sherpa.onnx

import android.content.res.AssetManager
import com.echofold.asr.engine.ReflectiveSherpaRuntime
import com.echofold.asr.engine.SherpaModelPaths
import com.echofold.core.asr.engine.AsrEngineException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import java.io.File

class ReflectiveRuntimeContractTest {
    @BeforeEach
    fun resetFixture() {
        OnlineRecognizer.lastInstance = null
        OnlineRecognizer.failCreateStream = false
        OnlineRecognizer.createStreamHotwords = null
        OnlineRecognizer.decodeCalls = 0
        OnlineRecognizer.releaseCalls = 0
        OnlineStream.releaseCalls = 0
    }

    @Test
    fun `uses official constructor hotword string and single stream decode`() {
        val recognizer = ReflectiveSherpaRuntime().createRecognizer(modelPaths(), threads = 2, provider = "cpu")

        assertEquals("", OnlineRecognizer.createStreamHotwords)
        assertEquals(2, OnlineRecognizer.lastInstance?.config?.modelConfigValue?.numThreadsValue)
        recognizer.acceptWaveform(floatArrayOf(0.1f, -0.1f), 16_000)
        assertEquals(1, OnlineRecognizer.decodeCalls)
        assertFalse(OnlineRecognizer.lastInstance?.usedDecodeStreams ?: true)

        recognizer.close()
        recognizer.close()
        val closedError = assertThrows(AsrEngineException::class.java) { recognizer.reset() }
        assertTrue(closedError.message.orEmpty().contains("已关闭"))
        assertThrows(AsrEngineException::class.java) { recognizer.acceptWaveform(floatArrayOf(0f), 16_000) }
        assertThrows(AsrEngineException::class.java) { recognizer.isEndpoint() }
        assertThrows(AsrEngineException::class.java) { recognizer.resultText() }
        assertEquals(1, OnlineStream.releaseCalls)
        assertEquals(1, OnlineRecognizer.releaseCalls)
    }

    @Test
    fun `bundled official AAR exposes the reflected API signatures`() {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .first { Files.isRegularFile(it.resolve("app_shell/android/app/libs/sherpa-onnx-1.13.6.aar")) }
        val aar = repository.resolve("app_shell/android/app/libs/sherpa-onnx-1.13.6.aar")
        val classesJar = Files.createTempFile("sherpa-onnx-1.13.6-", ".jar")
        try {
            ZipFile(aar.toFile()).use { zip ->
                zip.getInputStream(zip.getEntry("classes.jar")).use { input ->
                    Files.copy(input, classesJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            }
            val aarLoader = URLClassLoader(arrayOf(classesJar.toUri().toURL()), javaClass.classLoader)
            aarLoader.use { loader ->
                val recognizerClass = loader.loadClass("com.k2fsa.sherpa.onnx.OnlineRecognizer")
                val configClass = loader.loadClass("com.k2fsa.sherpa.onnx.OnlineRecognizerConfig")
                val streamClass = loader.loadClass("com.k2fsa.sherpa.onnx.OnlineStream")
                val featureClass = loader.loadClass("com.k2fsa.sherpa.onnx.FeatureConfig")
                val transducerClass = loader.loadClass("com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig")
                val modelClass = loader.loadClass("com.k2fsa.sherpa.onnx.OnlineModelConfig")
                assertTrue(recognizerClass.constructors.any { constructor ->
                    constructor.parameterCount == 2 &&
                        constructor.parameterTypes[0].name == "android.content.res.AssetManager" &&
                        constructor.parameterTypes[1] == configClass
                })
                assertEquals(streamClass, recognizerClass.getMethod("createStream", String::class.java).returnType)
                assertEquals(Void.TYPE, recognizerClass.getMethod("decode", streamClass).returnType)
                assertEquals(Boolean::class.javaPrimitiveType, recognizerClass.getMethod("isReady", streamClass).returnType)
                assertEquals(Void.TYPE, recognizerClass.getMethod("reset", streamClass).returnType)
                featureClass.getMethod("setSampleRate", Int::class.javaPrimitiveType)
                featureClass.getMethod("setFeatureDim", Int::class.javaPrimitiveType)
                transducerClass.getMethod("setEncoder", String::class.java)
                transducerClass.getMethod("setDecoder", String::class.java)
                transducerClass.getMethod("setJoiner", String::class.java)
                modelClass.getMethod("setTokens", String::class.java)
                modelClass.getMethod("setNumThreads", Int::class.javaPrimitiveType)
                modelClass.getMethod("setProvider", String::class.java)
                modelClass.getMethod("setDebug", Boolean::class.javaPrimitiveType)
            }
        } finally {
            Files.deleteIfExists(classesJar)
        }
    }

    @Test
    fun `releases recognizer if stream initialization fails`() {
        OnlineRecognizer.failCreateStream = true

        assertThrows(AsrEngineException::class.java) {
            ReflectiveSherpaRuntime().createRecognizer(modelPaths(), threads = 2, provider = "cpu")
        }

        assertEquals(1, OnlineRecognizer.releaseCalls)
    }

    @Test
    fun `required configuration setter absence is an error`() {
        val method = ReflectiveSherpaRuntime::class.java.getDeclaredMethod(
            "setRequired",
            Any::class.java,
            String::class.java,
            Any::class.java,
        ).apply { isAccessible = true }

        val error = assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
            method.invoke(ReflectiveSherpaRuntime(), MissingConfigurationProperty(), "setMissing", "value")
        }
        assertTrue(error.targetException is AsrEngineException)
    }

    private fun modelPaths() = SherpaModelPaths(
        encoder = File("encoder.onnx"),
        decoder = File("decoder.onnx"),
        joiner = File("joiner.onnx"),
        tokens = File("tokens.txt"),
    )
}

private class MissingConfigurationProperty

class FeatureConfig {
    var sampleRateValue: Int = 0
    var featureDimValue: Int = 0
    fun setSampleRate(value: Int) { sampleRateValue = value }
    fun setFeatureDim(value: Int) { featureDimValue = value }
}

class OnlineTransducerModelConfig {
    var encoderValue: String = ""
    var decoderValue: String = ""
    var joinerValue: String = ""
    fun setEncoder(value: String) { encoderValue = value }
    fun setDecoder(value: String) { decoderValue = value }
    fun setJoiner(value: String) { joinerValue = value }
}

class OnlineModelConfig {
    var transducerValue: OnlineTransducerModelConfig? = null
    var tokensValue: String = ""
    var numThreadsValue: Int = 0
    var providerValue: String = ""
    var debugValue: Boolean = true
    fun setTransducer(value: OnlineTransducerModelConfig) { transducerValue = value }
    fun setTokens(value: String) { tokensValue = value }
    fun setNumThreads(value: Int) { numThreadsValue = value }
    fun setProvider(value: String) { providerValue = value }
    fun setDebug(value: Boolean) { debugValue = value }
}

class OnlineRecognizerConfig {
    var featConfigValue: FeatureConfig? = null
    var modelConfigValue: OnlineModelConfig? = null
    var decodingMethodValue: String = ""
    var enableEndpointValue: Boolean = false
    fun setFeatConfig(value: FeatureConfig) { featConfigValue = value }
    fun setModelConfig(value: OnlineModelConfig) { modelConfigValue = value }
    fun setDecodingMethod(value: String) { decodingMethodValue = value }
    fun setEnableEndpoint(value: Boolean) { enableEndpointValue = value }
}

class OnlineRecognizer(@Suppress("UNUSED_PARAMETER") assetManager: AssetManager?, val config: OnlineRecognizerConfig) {
    private var ready = true
    var usedDecodeStreams: Boolean = false
        private set

    init {
        lastInstance = this
    }

    fun createStream(hotwords: String): OnlineStream {
        createStreamHotwords = hotwords
        check(!failCreateStream) { "fixture stream initialization failure" }
        return OnlineStream()
    }

    fun isReady(@Suppress("UNUSED_PARAMETER") stream: OnlineStream): Boolean = ready.also { ready = false }
    fun decode(@Suppress("UNUSED_PARAMETER") stream: OnlineStream) { decodeCalls += 1 }
    fun decodeStreams(@Suppress("UNUSED_PARAMETER") streams: Array<OnlineStream>) { usedDecodeStreams = true }
    fun isEndpoint(@Suppress("UNUSED_PARAMETER") stream: OnlineStream): Boolean = false
    fun getResult(@Suppress("UNUSED_PARAMETER") stream: OnlineStream): OnlineRecognizerResult = OnlineRecognizerResult("")
    fun reset(@Suppress("UNUSED_PARAMETER") stream: OnlineStream) { ready = true }
    fun release() { releaseCalls += 1 }

    companion object {
        var lastInstance: OnlineRecognizer? = null
        var failCreateStream: Boolean = false
        var createStreamHotwords: String? = null
        var decodeCalls: Int = 0
        var releaseCalls: Int = 0
    }
}

class OnlineStream {
    fun acceptWaveform(@Suppress("UNUSED_PARAMETER") samples: FloatArray, @Suppress("UNUSED_PARAMETER") sampleRate: Int) = Unit
    fun release() { releaseCalls += 1 }

    companion object {
        var releaseCalls: Int = 0
    }
}

class OnlineRecognizerResult(private val text: String) {
    fun getText(): String = text
}
