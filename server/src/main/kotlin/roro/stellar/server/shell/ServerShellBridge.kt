package roro.stellar.server.shell

import android.util.Base64
import roro.stellar.server.util.Logger
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.Executors

class ServerShellBridge : Closeable {

    private val executor = Executors.newCachedThreadPool()
    private val serverSocket = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(InetAddress.getByName(HOST), PORT))
    }
    private val token = generateToken()

    fun start() {
        publishToken()
        executor.execute {
            while (!serverSocket.isClosed) {
                val socket = try {
                    serverSocket.accept()
                } catch (_: Throwable) {
                    break
                }
                executor.execute { handle(socket) }
            }
        }
        LOGGER.i("stsh bridge listening on 127.0.0.1:%d", PORT)
    }

    private fun publishToken() {
        val tokenFile = File(TOKEN_FILE)
        val parent = tokenFile.parentFile
        check(parent != null && (parent.isDirectory || parent.mkdirs())) {
            "Unable to create ${tokenFile.parent}"
        }
        tokenFile.writeText(token)
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            val output = client.getOutputStream()
            try {
                if (!client.inetAddress.isLoopbackAddress) {
                    writeResponse(output, 126, "Local connection required\n".toByteArray())
                    return
                }

                val reader = client.getInputStream().bufferedReader()
                val lines = buildList {
                    while (true) {
                        add(reader.readLine() ?: break)
                    }
                }
                if (lines.firstOrNull() != PROTOCOL || lines.getOrNull(1) != token) {
                    writeResponse(output, 126, "Invalid stsh token\n".toByteArray())
                    return
                }

                val args = lines.drop(2).map {
                    String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8)
                }
                val process = if (args.firstOrNull() == "exec") {
                    val command = args.drop(1)
                    check(command.isNotEmpty()) { "Missing command" }
                    ProcessBuilder(command).start()
                } else {
                    ProcessBuilder(listOf("/system/bin/sh") + args).start()
                }
                process.outputStream.close()

                val buffer = ByteArrayOutputStream()
                val stdout = copyAsync(process.inputStream, buffer)
                val stderr = copyAsync(process.errorStream, buffer)
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
        private val LOGGER = Logger("ServerShellBridge")
        private const val HOST = "127.0.0.1"
        private const val PORT = 59521
        private const val PROTOCOL = "STSH1"
        private const val STATUS_PREFIX = "STSH_STATUS="
        private const val TOKEN_FILE =
            "/storage/emulated/0/Android/data/com.termux/files/.stellar-stsh-token"
    }
}
