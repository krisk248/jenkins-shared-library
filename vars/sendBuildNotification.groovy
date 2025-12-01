#!/usr/bin/env groovy

/**
 * Send build notification via Email and Teams
 * Automatically generates email body and Teams card
 *
 * Usage in Jenkinsfile:
 *   sendBuildNotification(
 *     projectName: 'ADXSIP',
 *     component: 'Backend',
 *     recipients: 'dev@ttsme.com, devsecops@ttsme.com',
 *     teamsWebhookUrl: env.TEAMS_WEBHOOK_URL,
 *     securityStatus: env.SECURITY_STATUS,
 *     deployedTo: '/tts/outputttsbuild/ADXSIP',
 *     attachments: 'security-reports/*.pdf,security-reports/*.html'
 *   )
 */
def call(Map config = [:]) {
    def projectName = config.projectName ?: env.PROJECT_NAME ?: 'Unknown'
    def component = config.component ?: ''
    def fullName = component ? "${projectName} ${component}" : projectName

    def recipients = config.recipients ?: config.to ?: ''
    def teamsWebhookUrl = config.teamsWebhookUrl ?: env.TEAMS_WEBHOOK_URL ?: ''
    def securityStatus = config.securityStatus ?: env.SECURITY_STATUS ?: 'N/A'
    def deployedTo = config.deployedTo ?: ''
    def attachments = config.attachments ?: ''
    def branch = config.branch ?: env.GIT_BRANCH ?: 'master'

    def buildNumber = env.BUILD_NUMBER ?: '0'
    def buildUrl = env.BUILD_URL ?: ''
    def buildResult = currentBuild.currentResult ?: 'SUCCESS'

    // Determine status color and emoji
    def statusColor = buildResult == 'SUCCESS' ? '#2e7d32' : '#c62828'
    def statusEmoji = buildResult == 'SUCCESS' ? '✅' : '❌'

    echo "📨 Sending build notifications for ${fullName} #${buildNumber}..."

    // Generate email body
    def emailBody = """
        <html>
        <body style="font-family: Arial, sans-serif;">
        <h2 style="color: ${statusColor};">${statusEmoji} ${fullName} - Build #${buildNumber}</h2>
        <table border="1" cellpadding="10" cellspacing="0" style="border-collapse: collapse;">
            <tr style="background-color: #f5f5f5;"><td><strong>Build Status</strong></td><td style="color: ${statusColor};">${buildResult}</td></tr>
            <tr><td><strong>Branch</strong></td><td>${branch}</td></tr>
            <tr style="background-color: #f5f5f5;"><td><strong>Security Status</strong></td><td>${securityStatus}</td></tr>
            ${deployedTo ? "<tr><td><strong>Deployed To</strong></td><td>${deployedTo}</td></tr>" : ''}
        </table>
        <hr/>
        <h3>📊 Security Reports</h3>
        <p>PDF and HTML reports attached (if available).</p>
        <hr/>
        <p><strong>🔗 Jenkins:</strong> <a href="${buildUrl}">${buildUrl}</a></p>
        <p style="color: #666; font-size: 12px;"><em>Automated message from Jenkins CI/CD</em></p>
        </body>
        </html>
    """

    def emailSubject = "[Jenkins] ${fullName} Build #${buildNumber} - ${buildResult}"

    // Send Email
    if (recipients) {
        try {
            emailext(
                subject: emailSubject,
                body: emailBody,
                mimeType: 'text/html',
                to: recipients,
                attachmentsPattern: attachments,
                attachLog: false
            )
            echo "✅ Email sent to: ${recipients}"
        } catch (Exception e) {
            echo "⚠️ Email failed: ${e.message}"
        }
    } else {
        echo "⚠️ No email recipients configured - skipping email"
    }

    // Send Teams notification
    if (teamsWebhookUrl) {
        def teamsStatus = buildResult == 'SUCCESS' ? 'SUCCESS' : 'FAILURE'
        def facts = [
            [name: 'Project', value: fullName],
            [name: 'Branch', value: branch],
            [name: 'Security', value: securityStatus],
            [name: 'Build', value: "#${buildNumber}"]
        ]
        if (deployedTo) {
            facts.add([name: 'Deployed To', value: deployedTo])
        }

        try {
            def themeColor = buildResult == 'SUCCESS' ? '00FF00' : 'FF0000'
            def factsJson = facts.collect { fact ->
                """{"name": "${fact.name}", "value": "${fact.value}"}"""
            }.join(',')

            def payload = """{
                "@type": "MessageCard",
                "@context": "http://schema.org/extensions",
                "themeColor": "${themeColor}",
                "summary": "${emailSubject}",
                "sections": [{
                    "activityTitle": "${statusEmoji} ${fullName} Build #${buildNumber}",
                    "activitySubtitle": "Build ${buildResult}",
                    "facts": [${factsJson}],
                    "markdown": true
                }],
                "potentialAction": [{
                    "@type": "OpenUri",
                    "name": "View Build",
                    "targets": [{"os": "default", "uri": "${buildUrl}"}]
                }]
            }"""

            httpRequest(
                httpMode: 'POST',
                url: teamsWebhookUrl,
                contentType: 'APPLICATION_JSON',
                requestBody: payload,
                validResponseCodes: '200:299'
            )
            echo "✅ Teams notification sent"
        } catch (Exception e) {
            echo "⚠️ Teams notification failed: ${e.message}"
        }
    } else {
        echo "⚠️ No Teams webhook URL configured - skipping Teams"
    }

    echo "📨 Notifications complete"
}
