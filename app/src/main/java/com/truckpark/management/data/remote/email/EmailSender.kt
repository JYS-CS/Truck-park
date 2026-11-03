package com.truckpark.management.data.remote.email

import com.truckpark.management.core.common.AuthError
import com.truckpark.management.core.common.AuthResult
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.time.ZonedDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.UUID
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dispatches transactional verification emails for account registration and admin security setup.
 */
interface EmailSender {
    suspend fun sendVerificationEmail(
        toEmail: String,
        code: String,
        recipientName: String? = null,
        isAdminSetup: Boolean = false,
    ): AuthResult<Unit>
}

object NoOpEmailSender : EmailSender {
    override suspend fun sendVerificationEmail(
        toEmail: String,
        code: String,
        recipientName: String?,
        isAdminSetup: Boolean,
    ): AuthResult<Unit> = AuthResult.Success(Unit)
}

/**
 * Sends transactional verification emails over Brevo SMTP with STARTTLS (port 587).
 * Uses standard JDK sockets and TLS so no external mail libraries are required.
 */
class BrevoSmtpEmailSender(
    private val smtpHost: String,
    private val smtpPort: Int = 587,
    private val smtpLogin: String,
    private val smtpKey: String,
    private val senderEmail: String,
    private val senderName: String = "Hibiscus TruckPark",
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 20_000,
) : EmailSender {

    override suspend fun sendVerificationEmail(
        toEmail: String,
        code: String,
        recipientName: String?,
        isAdminSetup: Boolean,
    ): AuthResult<Unit> = withContext(Dispatchers.IO) {
        val cleanRecipient = toEmail.trim()
        if (cleanRecipient.isEmpty() || !cleanRecipient.contains("@")) {
            return@withContext AuthResult.Failure(
                AuthError("INVALID_EMAIL", "A valid recipient email address is required."),
            )
        }
        if (smtpHost.isBlank() || smtpLogin.isBlank() || smtpKey.isBlank() || senderEmail.isBlank()) {
            return@withContext AuthResult.Failure(
                AuthError("EMAIL_CONFIG_MISSING", "Email service is not configured. Check BREVO_SMTP settings in .env."),
            )
        }

        try {
            sendSmtpMessage(
                toEmail = cleanRecipient,
                code = code.trim(),
                recipientName = recipientName?.trim()?.takeIf { it.isNotEmpty() },
                isAdminSetup = isAdminSetup,
            )
            AuthResult.Success(Unit)
        } catch (e: SmtpProtocolException) {
            AuthResult.Failure(
                AuthError("EMAIL_SEND_FAILED", "Could not send verification email (${e.message})."),
            )
        } catch (e: IOException) {
            AuthResult.Failure(
                AuthError(AuthError.NETWORK, "Unable to reach email server. Check your internet connection and try again."),
            )
        } catch (e: Exception) {
            AuthResult.Failure(
                AuthError("EMAIL_SEND_FAILED", "Failed to send verification email. Please try again."),
            )
        }
    }

    private fun sendSmtpMessage(
        toEmail: String,
        code: String,
        recipientName: String?,
        isAdminSetup: Boolean,
    ) {
        val rawSocket = Socket()
        rawSocket.connect(InetSocketAddress(smtpHost, smtpPort), connectTimeoutMs)
        rawSocket.soTimeout = readTimeoutMs

        var activeSocket: Socket = rawSocket
        try {
            var reader = BufferedReader(InputStreamReader(activeSocket.getInputStream(), Charsets.UTF_8))
            var writer = BufferedWriter(OutputStreamWriter(activeSocket.getOutputStream(), Charsets.UTF_8))

            expectCode(reader, 220, "SMTP greeting")
            sendCommand(writer, "EHLO hibiscustruckpark.com")
            expectCode(reader, 250, "EHLO")

            // Upgrade connection to TLS via STARTTLS
            sendCommand(writer, "STARTTLS")
            expectCode(reader, 220, "STARTTLS")

            val sslSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val sslSocket = sslSocketFactory.createSocket(rawSocket, smtpHost, smtpPort, true) as SSLSocket
            sslSocket.soTimeout = readTimeoutMs
            sslSocket.startHandshake()
            activeSocket = sslSocket

            reader = BufferedReader(InputStreamReader(activeSocket.getInputStream(), Charsets.UTF_8))
            writer = BufferedWriter(OutputStreamWriter(activeSocket.getOutputStream(), Charsets.UTF_8))

            sendCommand(writer, "EHLO hibiscustruckpark.com")
            expectCode(reader, 250, "EHLO after STARTTLS")

            // Authenticate via AUTH LOGIN
            sendCommand(writer, "AUTH LOGIN")
            expectCode(reader, 334, "AUTH LOGIN")

            val encodedLogin = Base64.getEncoder().encodeToString(smtpLogin.toByteArray(Charsets.UTF_8))
            sendCommand(writer, encodedLogin)
            expectCode(reader, 334, "SMTP username")

            val encodedKey = Base64.getEncoder().encodeToString(smtpKey.toByteArray(Charsets.UTF_8))
            sendCommand(writer, encodedKey)
            expectCode(reader, 235, "SMTP authentication")

            // Envelope sender & recipient
            sendCommand(writer, "MAIL FROM:<${senderEmail.trim()}>")
            expectCode(reader, 250, "MAIL FROM")

            sendCommand(writer, "RCPT TO:<$toEmail>")
            expectCode(reader, 250, "RCPT TO")

            sendCommand(writer, "DATA")
            expectCode(reader, 354, "DATA")

            val mimePayload = buildMimeMessage(
                fromEmail = senderEmail.trim(),
                fromName = senderName,
                toEmail = toEmail,
                recipientName = recipientName,
                code = code,
                isAdminSetup = isAdminSetup,
            )
            writer.write(mimePayload)
            writer.write("\r\n.\r\n")
            writer.flush()
            expectCode(reader, 250, "Message body")

            runCatching {
                sendCommand(writer, "QUIT")
            }
        } finally {
            runCatching { activeSocket.close() }
            if (activeSocket !== rawSocket) {
                runCatching { rawSocket.close() }
            }
        }
    }

    private fun sendCommand(writer: BufferedWriter, command: String) {
        writer.write(command)
        writer.write("\r\n")
        writer.flush()
    }

    private fun expectCode(reader: BufferedReader, expectedCode: Int, stage: String) {
        val lines = mutableListOf<String>()
        while (true) {
            val line = reader.readLine()
                ?: throw SmtpProtocolException("Connection closed during $stage")
            lines += line
            // Multi-line SMTP responses have a hyphen at index 3 (e.g. "250-PIPELINING"); final line has space ("250 OK")
            if (line.length < 4 || line[3] != '-') {
                break
            }
        }
        val lastLine = lines.last()
        val statusCode = lastLine.take(3).toIntOrNull()
        if (statusCode != expectedCode) {
            throw SmtpProtocolException("$stage rejected with $lastLine")
        }
    }

    private fun buildMimeMessage(
        fromEmail: String,
        fromName: String,
        toEmail: String,
        recipientName: String?,
        code: String,
        isAdminSetup: Boolean,
    ): String {
        val boundary = "----=_Part_${UUID.randomUUID().toString().replace("-", "")}"
        val dateHeader = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
            .format(ZonedDateTime.now(ZoneOffset.UTC))
        val messageId = "<${UUID.randomUUID()}@hibiscustruckpark.com>"
        val greetingName = recipientName ?: if (isAdminSetup) "Administrator" else "there"

        val subject = if (isAdminSetup) {
            "$code is your Hibiscus TruckPark Admin verification code"
        } else {
            "$code is your Hibiscus TruckPark verification code"
        }

        val contextHeading = if (isAdminSetup) {
            "Admin Account Security Setup"
        } else {
            "Verify Your Email Address"
        }

        val contextCopy = if (isAdminSetup) {
            "Use the 6-digit verification code below to confirm your official administrator email address and complete your security setup."
        } else {
            "Use the 6-digit verification code below to verify your email address and submit your Hibiscus TruckPark account registration."
        }

        val plainText = buildString {
            append("Hello $greetingName,\r\n\r\n")
            append("$contextCopy\r\n\r\n")
            append("Your 6-digit verification code is: $code\r\n\r\n")
            append("This code expires in 15 minutes. If you did not request this code, you can safely ignore this email.\r\n\r\n")
            append("— Hibiscus TruckPark Security\r\n")
        }

        val htmlBody = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            </head>
            <body style="margin:0;padding:24px;background-color:#0B0F19;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#F8FAFC;">
              <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:480px;margin:0 auto;background-color:#111827;border:1px solid #1E293B;border-radius:20px;padding:32px;">
                <tr>
                  <td align="center">
                    <div style="font-size:12px;font-weight:700;letter-spacing:2px;color:#38BDF8;text-transform:uppercase;margin-bottom:8px;">
                      HIBISCUS TRUCKPARK
                    </div>
                    <h1 style="margin:0 0 12px 0;font-size:22px;font-weight:700;color:#FFFFFF;">
                      $contextHeading
                    </h1>
                    <p style="margin:0 0 24px 0;font-size:14px;line-height:22px;color:#94A3B8;">
                      Hello $greetingName,<br/>$contextCopy
                    </p>
                    <div style="background-color:#0F172A;border:1px solid #334155;border-radius:14px;padding:20px 24px;margin-bottom:24px;">
                      <div style="font-size:11px;font-weight:600;letter-spacing:1.5px;color:#64748B;text-transform:uppercase;margin-bottom:8px;">
                        6-DIGIT VERIFICATION CODE
                      </div>
                      <div style="font-size:32px;font-weight:800;letter-spacing:8px;color:#38BDF8;font-family:monospace;">
                        $code
                      </div>
                    </div>
                    <p style="margin:0;font-size:12px;line-height:18px;color:#64748B;">
                      This code is valid for <strong style="color:#CBD5E1;">15 minutes</strong>. Never share this verification code with anyone.
                    </p>
                  </td>
                </tr>
              </table>
            </body>
            </html>
        """.trimIndent().replace("\n", "\r\n")

        return buildString {
            append("Date: $dateHeader\r\n")
            append("Message-ID: $messageId\r\n")
            append("From: \"$fromName\" <$fromEmail>\r\n")
            append("To: <$toEmail>\r\n")
            append("Subject: $subject\r\n")
            append("MIME-Version: 1.0\r\n")
            append("Content-Type: multipart/alternative; boundary=\"$boundary\"\r\n")
            append("\r\n")
            append("--$boundary\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n")
            append("Content-Transfer-Encoding: 8bit\r\n")
            append("\r\n")
            append(dotStuff(plainText))
            append("\r\n")
            append("--$boundary\r\n")
            append("Content-Type: text/html; charset=UTF-8\r\n")
            append("Content-Transfer-Encoding: 8bit\r\n")
            append("\r\n")
            append(dotStuff(htmlBody))
            append("\r\n")
            append("--$boundary--")
        }
    }

    /** RFC 5321 section 4.5.2 transparency: lines starting with '.' must be doubled. */
    private fun dotStuff(text: String): String =
        text.split("\r\n").joinToString("\r\n") { line ->
            if (line.startsWith(".")) ".$line" else line
        }

    private class SmtpProtocolException(message: String) : Exception(message)
}
