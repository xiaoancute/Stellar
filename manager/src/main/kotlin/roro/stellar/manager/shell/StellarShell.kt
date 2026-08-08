package roro.stellar.manager.shell

import android.os.Handler
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.system.Os
import roro.stellar.Stellar
import roro.stellar.StellarApiConstants
import java.io.FileDescriptor
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

object StellarShell {
    private const val REQUEST_CODE = 1001

    @JvmStatic
    fun main(args: Array<String>, packageName: String, binder: IBinder, handler: Handler) {
        Stellar.onBinderReceived(binder, packageName)

        val started = AtomicBoolean(false)
        fun startCommand() {
            if (!started.compareAndSet(false, true)) return
            Thread {
                val exitCode = try {
                    runCommand(args)
                } catch (error: Throwable) {
                    error.printStackTrace(System.err)
                    1
                }
                System.out.flush()
                System.err.flush()
                exitProcess(exitCode)
            }.apply { name = "stsh-command" }.start()
        }

        lateinit var permissionListener: Stellar.OnRequestPermissionResultListener
        permissionListener = Stellar.OnRequestPermissionResultListener { requestCode, allowed, _ ->
            if (requestCode != REQUEST_CODE) return@OnRequestPermissionResultListener
            Stellar.removeRequestPermissionResultListener(permissionListener)
            if (allowed) startCommand() else fail("Stellar permission was denied")
        }

        val binderListener = Stellar.OnBinderReceivedListener {
            if (Stellar.checkSelfPermission()) {
                startCommand()
                return@OnBinderReceivedListener
            }
            if (Stellar.shouldShowRequestPermissionRationale()) {
                fail("Stellar permission was denied previously; enable Termux in Stellar first")
                return@OnBinderReceivedListener
            }
            Stellar.addRequestPermissionResultListener(permissionListener, handler)
            Stellar.requestPermission(StellarApiConstants.PERMISSION_STELLAR, REQUEST_CODE)
        }
        Stellar.addBinderReceivedListenerSticky(binderListener, handler)
    }

    private fun runCommand(args: Array<String>): Int {
        val commandArgs = if (args.firstOrNull() == "exec") args.drop(1) else args.toList()
        val command = Array<String?>(commandArgs.size + 1) { index ->
            if (index == 0) "/system/bin/sh" else commandArgs[index - 1]
        }
        val environment: Array<String?>? = if (System.getenv("STSH_PRESERVE_ENV") == "1") {
            System.getenv().map { (key, value) -> "$key=$value" }.toTypedArray()
        } else {
            null
        }

        return if (isTerminal(FileDescriptor.`in`) && isTerminal(FileDescriptor.out)
            && isTerminal(FileDescriptor.err)
        ) {
            runPty(command, environment)
        } else {
            runPipes(command, environment)
        }
    }

    private fun runPipes(command: Array<String?>, environment: Array<String?>?): Int {
        val process = Stellar.newProcess(command, environment, null)
        val outputThread = copyAsync(process.inputStream, System.out)
        val errorThread = copyAsync(process.errorStream, System.err)
        val inputThread = copyInputAsync(System.`in`, process.outputStream)
        val exitCode = process.waitFor()
        outputThread.join()
        errorThread.join()
        process.destroy()
        inputThread.interrupt()
        return exitCode
    }

    private fun runPty(command: Array<String?>, environment: Array<String?>?): Int {
        val process = Stellar.newPtyProcess(command, environment, null)
        val pty = process.ptyFd
        val inputFd = ParcelFileDescriptor.dup(pty.fileDescriptor)
        val outputFd = ParcelFileDescriptor.dup(pty.fileDescriptor)
        pty.close()
        val columns = System.getenv("COLUMNS")?.toIntOrNull() ?: 80
        val rows = System.getenv("LINES")?.toIntOrNull() ?: 24
        process.resize(columns, rows)

        val rawTerminal = setRawTerminal()
        val outputThread = copyAsync(
            ParcelFileDescriptor.AutoCloseInputStream(inputFd),
            System.out
        )
        val inputThread = Thread {
            try {
                copy(System.`in`, ParcelFileDescriptor.AutoCloseOutputStream(outputFd))
            } catch (_: Throwable) {
            }
        }.apply {
            name = "stsh-input"
            isDaemon = true
            start()
        }

        return try {
            val exitCode = process.waitFor()
            outputThread.join()
            exitCode
        } finally {
            if (rawTerminal) restoreTerminal()
            process.destroy()
            inputThread.interrupt()
        }
    }

    private fun copy(input: InputStream, output: OutputStream) {
        input.use { source ->
            output.use { destination ->
                source.copyTo(destination)
                destination.flush()
            }
        }
    }

    private fun copyAsync(input: InputStream, output: OutputStream): Thread = Thread {
        try {
            input.copyTo(output)
            output.flush()
        } catch (_: Throwable) {
        } finally {
            input.close()
        }
    }.apply {
        name = "stsh-output"
        isDaemon = false
        start()
    }

    private fun copyInputAsync(input: InputStream, output: OutputStream): Thread = Thread {
        try {
            input.copyTo(output)
            output.flush()
        } catch (_: Throwable) {
        } finally {
            try {
                output.close()
            } catch (_: Throwable) {
            }
        }
    }.apply {
        name = "stsh-input"
        isDaemon = true
        start()
    }

    private fun isTerminal(fileDescriptor: FileDescriptor): Boolean = try {
        Os.isatty(fileDescriptor)
    } catch (_: Throwable) {
        false
    }

    private fun setRawTerminal(): Boolean = try {
        ProcessBuilder("/system/bin/sh", "-c", "/system/bin/stty raw -echo < /dev/tty")
            .start().waitFor() == 0
    } catch (_: Throwable) {
        false
    }

    private fun restoreTerminal() {
        try {
            ProcessBuilder("/system/bin/sh", "-c", "/system/bin/stty sane < /dev/tty")
                .start().waitFor()
        } catch (_: Throwable) {
        }
    }

    private fun fail(message: String): Nothing {
        System.err.println(message)
        System.err.flush()
        exitProcess(1)
    }
}
