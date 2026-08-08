package roro.stellar.manager.shell

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.util.Base64
import roro.stellar.Stellar
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors

class ShellBridgeServer(private val context: Context) : Closeable {

    private val executor = Executors.newCachedThreadPool()
    private val serverSocket = LocalServerSocket(SOCKET_NAME)

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

    private fun handle(socket: LocalSocket) {
        socket.use { client ->
            val output = client.outputStream
            try {
                val packages = context.packageManager.getPackagesForUid(client.peerCredentials.uid)
                    ?.toSet()
                    .orEmpty()
                if (TERMUX_PACKAGE !in packages) {
                    writeResponse(output, 126, "Termux UID required\n".toByteArray())
                    return
                }

                if (!Stellar.pingBinder()) {
                    writeResponse(output, 125, "Stellar service is not running\n".toByteArray())
                    return
                }

                val lines = client.inputStream.bufferedReader().readLines()
                if (lines.firstOrNull() != PROTOCOL) {
                    writeResponse(output, 124, "Invalid stsh request\n".toByteArray())
                    return
                }
                val args = lines.drop(1).map {
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

    override fun close() {
        runCatching { serverSocket.close() }
        executor.shutdownNow()
    }

    companion object {
        private const val SOCKET_NAME = "roro.stellar.manager.stsh"
        private const val TERMUX_PACKAGE = "com.termux"
        private const val PROTOCOL = "STSH1"
        private const val STATUS_PREFIX = "STSH_STATUS="
    }
}
