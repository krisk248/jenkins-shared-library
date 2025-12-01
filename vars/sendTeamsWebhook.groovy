#!/usr/bin/env groovy

/**
 * Send Microsoft Teams notification via webhook
 *
 * Usage in Jenkinsfile:
 *   sendTeamsWebhook(
 *     webhookUrl: 'https://outlook.office.com/webhook/...',
 *     title: 'Build Report',
 *     message: 'Build completed successfully',
 *     status: 'SUCCESS',  // SUCCESS, FAILURE, WARNING
 *     facts: [
 *       [name: 'Project', value: 'ADXSIP'],
 *       [name: 'Build', value: '#15']
 *     ]
 *   )
 */
def call(Map config = [:]) {
    // Webhook URL - can be passed or set as environment variable
    def webhookUrl = config.webhookUrl ?: env.TEAMS_WEBHOOK_URL

    if (!webhookUrl) {
        echo "⚠️ Teams webhook URL not configured - skipping notification"
        return false
    }

    def title = config.title ?: "Jenkins Build #${env.BUILD_NUMBER}"
    def message = config.message ?: "Build completed"
    def status = config.status ?: 'SUCCESS'
    def facts = config.facts ?: []
    def buildUrl = config.buildUrl ?: env.BUILD_URL ?: ''

    // Color based on status
    def themeColor = ''
    switch(status) {
        case 'SUCCESS':
            themeColor = '00FF00'  // Green
            break
        case 'FAILURE':
            themeColor = 'FF0000'  // Red
            break
        case 'WARNING':
            themeColor = 'FFA500'  // Orange
            break
        default:
            themeColor = '0078D7'  // Blue
    }

    // Build facts array for Teams card
    def factsJson = facts.collect { fact ->
        """{"name": "${fact.name}", "value": "${fact.value}"}"""
    }.join(',')

    // Teams Adaptive Card payload
    def payload = """{
        "@type": "MessageCard",
        "@context": "http://schema.org/extensions",
        "themeColor": "${themeColor}",
        "summary": "${title}",
        "sections": [{
            "activityTitle": "${title}",
            "activitySubtitle": "${message}",
            "facts": [${factsJson}],
            "markdown": true
        }],
        "potentialAction": [{
            "@type": "OpenUri",
            "name": "View Build",
            "targets": [{
                "os": "default",
                "uri": "${buildUrl}"
            }]
        }]
    }"""

    echo "📢 Sending Teams notification..."

    try {
        def response = httpRequest(
            httpMode: 'POST',
            url: webhookUrl,
            contentType: 'APPLICATION_JSON',
            requestBody: payload,
            validResponseCodes: '200:299'
        )
        echo "✅ Teams notification sent successfully"
        return true
    } catch (Exception e) {
        echo "❌ Failed to send Teams notification: ${e.message}"
        return false
    }
}
