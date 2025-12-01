#!/usr/bin/env groovy

/**
 * Send email with optional attachments
 *
 * Usage in Jenkinsfile:
 *   sendEmail(
 *     to: 'developer@ttsme.com, devsecops@ttsme.com',
 *     subject: 'Build Report',
 *     body: 'Build completed successfully',
 *     attachments: 'reports/*.pdf,reports/*.html'
 *   )
 */
def call(Map config = [:]) {
    // Required
    def to = config.to ?: error("'to' email address is required")
    def subject = config.subject ?: "[Jenkins] Build #${env.BUILD_NUMBER}"

    // Optional
    def body = config.body ?: "Build ${env.BUILD_NUMBER} completed."
    def mimeType = config.mimeType ?: 'text/html'
    def attachments = config.attachments ?: ''
    def attachLog = config.attachLog ?: false
    def replyTo = config.replyTo ?: ''

    echo "📧 Sending email to: ${to}"

    try {
        emailext(
            subject: subject,
            body: body,
            mimeType: mimeType,
            to: to,
            attachmentsPattern: attachments,
            attachLog: attachLog,
            replyTo: replyTo
        )
        echo "✅ Email sent successfully"
        return true
    } catch (Exception e) {
        echo "❌ Failed to send email: ${e.message}"
        return false
    }
}
