#!/usr/bin/env groovy

/**
 * Send email notification
 *
 * Usage in Jenkinsfile:
 *   sendNotification(
 *     status: currentBuild.currentResult,
 *     recipients: 'devops@ttsme.com'
 *   )
 */
def call(Map config = [:]) {
    def status = config.status ?: currentBuild.currentResult
    def recipients = config.recipients ?: env.DEVOPS_EMAIL ?: 'devops@ttsme.com'
    def subject = config.subject ?: "${env.JOB_NAME} - Build #${env.BUILD_NUMBER} - ${status}"

    // Determine status icon and color
    def statusIcon = ''
    def statusColor = ''

    switch(status) {
        case 'SUCCESS':
            statusIcon = '✅'
            statusColor = 'green'
            break
        case 'FAILURE':
            statusIcon = '❌'
            statusColor = 'red'
            break
        case 'UNSTABLE':
            statusIcon = '⚠️'
            statusColor = 'yellow'
            break
        default:
            statusIcon = 'ℹ️'
            statusColor = 'gray'
    }

    // Build email body
    def body = """
        <html>
        <body style="font-family: Arial, sans-serif;">
            <h2 style="color: ${statusColor};">${statusIcon} Build ${status}</h2>

            <table style="border-collapse: collapse; width: 100%;">
                <tr>
                    <td style="padding: 8px; font-weight: bold;">Project:</td>
                    <td style="padding: 8px;">${env.JOB_NAME}</td>
                </tr>
                <tr>
                    <td style="padding: 8px; font-weight: bold;">Build Number:</td>
                    <td style="padding: 8px;">#${env.BUILD_NUMBER}</td>
                </tr>
                <tr>
                    <td style="padding: 8px; font-weight: bold;">Status:</td>
                    <td style="padding: 8px; color: ${statusColor};">${status}</td>
                </tr>
                <tr>
                    <td style="padding: 8px; font-weight: bold;">Duration:</td>
                    <td style="padding: 8px;">${currentBuild.durationString}</td>
                </tr>
                <tr>
                    <td style="padding: 8px; font-weight: bold;">Build URL:</td>
                    <td style="padding: 8px;"><a href="${env.BUILD_URL}">${env.BUILD_URL}</a></td>
                </tr>
            </table>

            <br>
            <p><a href="${env.BUILD_URL}console">View Console Output</a></p>

            <hr>
            <p style="color: #666; font-size: 12px;">
                This is an automated message from Jenkins CI/CD System<br>
                TTS DevOps Team
            </p>
        </body>
        </html>
    """

    emailext(
        subject: subject,
        body: body,
        to: recipients,
        mimeType: 'text/html'
    )

    echo "📧 Notification sent to: ${recipients}"
}
