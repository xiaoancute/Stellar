package roro.stellar.manager.shell

import android.content.Context
import android.util.Base64
import roro.stellar.Stellar
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.Executors

class ShellBridgeServer(context: Context) : Closeable {

    private val executor = Executors.newCachedThreadPool()
    private val serverSocket = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(InetAddress.getLoopbackAddress(), PORT))
    }
    private val token = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getString(TOKEN_KEY, null)
        ?: generateToken().also {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(TOKEN_KEY, it)
                .apply()
        }

    fun start() {
        executor.execute {
            while (true) {
                val socket = try {
                    serverSocket.accept()
                } catch (_: Throwable) {
                    break
                }
                executor.execute { handle(socket) }
            }
        }
    }

    fun publishToken() {
        executor.execute {
            runCatching {
                val command = arrayOf<String?>(
                    "/system/bin/sh",
                    "-c",
                    "mkdir -p '$TERMUX_FILES' && printf '%s' '$token' > '$TOKEN_FILE'"
                )
                val process = Stellar.newProcess(command, null, null)
                process.outputStream.close()
                process.waitFor()
                process.destroy()
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            val output = client.getOutputStream()
            try {
                if (!client.inetAddress.isLoopbackAddress) {
                    writeResponse(output, 126, "Local connection required\n".toByteArray())
                    return
                }

                val lines = client.getInputStream().bufferedReader().readLines()
                if (lines.firstOrNull() != PROTOCOL || lines.getOrNull(1) != token) {
                    writeResponse(output, 126, "Invalid stsh token\n".toByteArray())
                    return
                }

                if (!Stellar.pingBinder()) {
                    writeResponse(output, 125, "Stellar service is not running\n".toByteArray())
                    return
                }

                val args = lines.drop(2).map {
                    String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8)
                }
                val command = arrayOfNulls<String>(args.size + 1)
                command[0] = "/system/bin/sh"
                args.forEachIndexed { index, arg -> command[index + 1] = arg }

                val process = Stellar.newProcess(command, null, null)
                val buffer = ByteArrayOutputStream()
                val stdout = copyAsync(process.inputStream, buffer)
                val stderr = copyAsync(process.errorStream, buffer)
                process.outputStream.close()
                val exitCode = process.waitFor()
                stdout.join()
                stderr.join()
                process.destroy()
                writeResponse(output, exitCode, buffer.toByteArray())
            } catch (error: Throwable) {
                writeResponse(
                    output,
                    123,
                    ((error.message ?: error.javaClass.simpleName) + "\n").toByteArray()
                )
            }
        }
    }

    private fun copyAsync(input: InputStream, output: ByteArrayOutputStream): Thread = Thread {
        input.use { source ->
            val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = source.read(bytes)
                if (count < 0) break
                synchronized(output) {
                    output.write(bytes, 0, count)
                }
            }
        }
    }.apply {
        isDaemon = true
        start()
    }

    private fun writeResponse(output: OutputStream, exitCode: Int, body: ByteArray) {
        runCatching {
            output.write("$STATUS_PREFIX$exitCode\n".toByteArray())
            output.write(body)
            output.flush()
        }
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun close() {
        runCatching { serverSocket.close() }
        executor.shutdownNow()
    }

    companion object {
        private const val PORT = 59521
        private const val PREFERENCES = "stsh_bridge"
        private const val TOKEN_KEY = "token"
        private const val PROTOCOL = "STSH1"
        private const val STATUS_PREFIX = "STSH_STATUS="
        private const val TERMUX_FILES =
            "/storage/emulated/0/Android/data/com.termux/files"
        private const val TOKEN_FILE = "$TERMUX_FILES/.stellar-stsh-token"
    }
}
