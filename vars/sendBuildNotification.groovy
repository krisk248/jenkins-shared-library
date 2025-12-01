#!/usr/bin/env groovy

/**
 * Send professional build notifications via Email and Microsoft Teams
 *
 * Features:
 * - Professional HTML email with TTS DevSecOps branding
 * - Modern Teams Adaptive Card
 * - Security scan results integration
 * - SonarQube dashboard link
 * - PDF/HTML report attachments
 *
 * Usage in Jenkinsfile:
 *   sendBuildNotification(
 *     project: 'ADXSIP',
 *     component: 'Frontend',
 *     email: 'developer@ttsme.com, devsecops@ttsme.com',
 *     teams: true,
 *     sonarQubeUrl: 'http://192.168.1.136:9000/dashboard?id=ADXSIP-Frontend',
 *     securityHigh: 1,
 *     securityMedium: 12,
 *     securityLow: 0,
 *     riskLevel: 'LOW',
 *     riskScore: '1.4',
 *     deployPath: '/tts/outputttsbuild/ADXSIP/...',
 *     attachments: 'security-reports/*.pdf,security-reports/*.html'
 *   )
 */
def call(Map config = [:]) {
    // Project info
    def project = config.project ?: config.projectName ?: env.PROJECT_NAME ?: 'Unknown'
    def component = config.component ?: ''
    def fullName = component ? "${project} ${component}" : project

    // Recipients
    def emailRecipients = config.email ?: config.recipients ?: config.to ?: ''
    def sendTeams = config.teams ?: false
    def teamsWebhookUrl = config.teamsWebhookUrl ?: env.TEAMS_WEBHOOK_URL ?: ''

    // Build info
    def buildNumber = env.BUILD_NUMBER ?: '0'
    def buildUrl = env.BUILD_URL ?: ''
    def buildResult = currentBuild.currentResult ?: 'SUCCESS'
    def buildDuration = currentBuild.durationString?.replace(' and counting', '') ?: 'N/A'
    def buildTimestamp = new Date().format('MMM dd, yyyy HH:mm')

    // Git info
    def gitBranch = config.branch ?: env.GIT_BRANCH ?: 'main'

    // Security scan results (can be passed or read from env)
    def securityHigh = config.securityHigh ?: env.SECURITY_HIGH ?: '0'
    def securityMedium = config.securityMedium ?: env.SECURITY_MEDIUM ?: '0'
    def securityLow = config.securityLow ?: env.SECURITY_LOW ?: '0'
    def riskLevel = config.riskLevel ?: env.RISK_LEVEL ?: 'N/A'
    def riskScore = config.riskScore ?: env.RISK_SCORE ?: 'N/A'
    def securityStatus = config.securityStatus ?: env.SECURITY_STATUS ?: 'PASSED'

    // Deployment info
    def deployPath = config.deployPath ?: config.deployedTo ?: env.OUTPUT_DIR ?: 'N/A'

    // SonarQube
    def sonarQubeUrl = config.sonarQubeUrl ?: config.sonarUrl ?: ''

    // Attachments
    def attachments = config.attachments ?: ''

    // Status styling
    def isSuccess = (buildResult == 'SUCCESS')
    def statusEmoji = isSuccess ? '&#x2705;' : '&#x274C;'
    def statusBgColor = isSuccess ? '#4caf50' : '#f44336'
    def statusText = isSuccess ? 'All thresholds passed' : 'Build failed - check logs'
    def riskColor = riskLevel == 'LOW' ? '#4caf50' : (riskLevel == 'MEDIUM' ? '#ff9800' : '#f44336')

    echo "════════════════════════════════════════════════════════════"
    echo "📨 Sending Build Notifications"
    echo "────────────────────────────────────────────────────────────"
    echo "   Project  : ${fullName}"
    echo "   Build    : #${buildNumber} - ${buildResult}"
    echo "   Email    : ${emailRecipients ?: 'Not configured'}"
    echo "   Teams    : ${sendTeams ? 'Yes' : 'No'}"
    echo "════════════════════════════════════════════════════════════"

    // =========================================
    // SEND EMAIL
    // =========================================
    if (emailRecipients) {
        try {
            def emailSubject = "[Jenkins] ${fullName} Build #${buildNumber} - ${buildResult}"

            // Build HTML email body
            def emailBody = """
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"></head>
<body style="margin:0;padding:0;font-family:'Segoe UI',Tahoma,Geneva,Verdana,sans-serif;background-color:#f5f5f5;">
<table role="presentation" style="width:100%;border-collapse:collapse;">
<tr><td align="center" style="padding:40px 0;">
<table role="presentation" style="width:600px;border-collapse:collapse;background-color:#ffffff;border-radius:8px;box-shadow:0 2px 8px rgba(0,0,0,0.1);">

<!-- Header -->
<tr><td style="background:linear-gradient(135deg,#1a237e 0%,#0d47a1 100%);padding:30px;border-radius:8px 8px 0 0;">
<table role="presentation" style="width:100%;"><tr>
<td><h1 style="margin:0;color:#ffffff;font-size:24px;font-weight:600;">TTS DevSecOps</h1>
<p style="margin:5px 0 0 0;color:#90caf9;font-size:14px;">Build Report</p></td>
<td align="right"><span style="background-color:${statusBgColor};color:#ffffff;padding:8px 16px;border-radius:20px;font-size:14px;font-weight:600;">
${statusEmoji} ${buildResult}</span></td>
</tr></table></td></tr>

<!-- Project Title -->
<tr><td style="padding:30px 30px 20px 30px;border-bottom:1px solid #e0e0e0;">
<h2 style="margin:0;color:#1a237e;font-size:22px;">${fullName}</h2>
<p style="margin:8px 0 0 0;color:#666666;font-size:14px;">Build #${buildNumber} | ${buildTimestamp}</p>
</td></tr>

<!-- Build Information -->
<tr><td style="padding:25px 30px;">
<h3 style="margin:0 0 15px 0;color:#333333;font-size:16px;border-left:4px solid #1a237e;padding-left:12px;">Build Information</h3>
<table role="presentation" style="width:100%;border-collapse:collapse;background-color:#fafafa;border-radius:6px;">
<tr><td style="padding:12px 15px;border-bottom:1px solid #eeeeee;width:140px;color:#666666;font-size:13px;">Branch</td>
<td style="padding:12px 15px;border-bottom:1px solid #eeeeee;color:#333333;font-size:13px;font-weight:500;">${gitBranch}</td></tr>
<tr><td style="padding:12px 15px;border-bottom:1px solid #eeeeee;color:#666666;font-size:13px;">Duration</td>
<td style="padding:12px 15px;border-bottom:1px solid #eeeeee;color:#333333;font-size:13px;font-weight:500;">${buildDuration}</td></tr>
<tr><td style="padding:12px 15px;color:#666666;font-size:13px;">Deployed To</td>
<td style="padding:12px 15px;color:#333333;font-size:13px;font-weight:500;">${deployPath}</td></tr>
</table></td></tr>

<!-- Security Scan Section -->
<tr><td style="padding:0 30px 25px 30px;">
<h3 style="margin:0 0 15px 0;color:#333333;font-size:16px;border-left:4px solid #ff5722;padding-left:12px;">Security Scan Results</h3>
<table role="presentation" style="width:100%;border-collapse:collapse;background-color:#fff8e1;border-radius:6px;border:1px solid #ffe082;">
<tr><td style="padding:20px;"><table role="presentation" style="width:100%;"><tr>
<td style="text-align:center;padding:0 10px;">
<div style="background-color:#f44336;color:#ffffff;width:50px;height:50px;border-radius:50%;line-height:50px;font-size:18px;font-weight:bold;margin:0 auto;">${securityHigh}</div>
<p style="margin:8px 0 0 0;color:#666666;font-size:12px;">HIGH</p></td>
<td style="text-align:center;padding:0 10px;">
<div style="background-color:#ff9800;color:#ffffff;width:50px;height:50px;border-radius:50%;line-height:50px;font-size:18px;font-weight:bold;margin:0 auto;">${securityMedium}</div>
<p style="margin:8px 0 0 0;color:#666666;font-size:12px;">MEDIUM</p></td>
<td style="text-align:center;padding:0 10px;">
<div style="background-color:#4caf50;color:#ffffff;width:50px;height:50px;border-radius:50%;line-height:50px;font-size:18px;font-weight:bold;margin:0 auto;">${securityLow}</div>
<p style="margin:8px 0 0 0;color:#666666;font-size:12px;">LOW</p></td>
<td style="text-align:left;padding-left:30px;">
<p style="margin:0;color:#333333;font-size:14px;">Risk Level</p>
<p style="margin:5px 0 0 0;color:${riskColor};font-size:20px;font-weight:bold;">${riskLevel} (${riskScore})</p>
<p style="margin:8px 0 0 0;color:#4caf50;font-size:13px;">${statusText}</p></td>
</tr></table></td></tr></table></td></tr>

<!-- SonarQube Section -->
${sonarQubeUrl ? """
<tr><td style="padding:0 30px 25px 30px;">
<h3 style="margin:0 0 15px 0;color:#333333;font-size:16px;border-left:4px solid #2196f3;padding-left:12px;">Code Quality (SonarQube)</h3>
<table role="presentation" style="width:100%;border-collapse:collapse;background-color:#e3f2fd;border-radius:6px;border:1px solid #90caf9;">
<tr><td style="padding:20px;">
<a href="${sonarQubeUrl}" style="display:inline-block;background-color:#1976d2;color:#ffffff;padding:12px 24px;text-decoration:none;border-radius:6px;font-size:14px;font-weight:500;">View SonarQube Dashboard</a>
</td></tr></table></td></tr>
""" : ''}

<!-- Attachments Note -->
${attachments ? """
<tr><td style="padding:0 30px 25px 30px;">
<h3 style="margin:0 0 15px 0;color:#333333;font-size:16px;border-left:4px solid #9c27b0;padding-left:12px;">Attachments</h3>
<p style="margin:0;color:#666666;font-size:13px;background-color:#f3e5f5;padding:15px;border-radius:6px;">
Security scan reports (PDF and HTML) are attached to this email.</p></td></tr>
""" : ''}

<!-- Action Buttons -->
<tr><td style="padding:0 30px 30px 30px;"><table role="presentation" style="width:100%;"><tr><td align="center">
<a href="${buildUrl}" style="display:inline-block;background-color:#1a237e;color:#ffffff;padding:12px 24px;text-decoration:none;border-radius:6px;font-size:14px;font-weight:500;margin:0 8px;">View Build</a>
<a href="${buildUrl}console" style="display:inline-block;background-color:#455a64;color:#ffffff;padding:12px 24px;text-decoration:none;border-radius:6px;font-size:14px;font-weight:500;margin:0 8px;">View Console</a>
</td></tr></table></td></tr>

<!-- Footer -->
<tr><td style="background-color:#f5f5f5;padding:20px 30px;border-radius:0 0 8px 8px;border-top:1px solid #e0e0e0;">
<p style="margin:0;color:#999999;font-size:12px;text-align:center;">
TTS DevSecOps Team | Automated CI/CD Report</p></td></tr>

</table></td></tr></table>
</body></html>
"""

            emailext(
                subject: emailSubject,
                body: emailBody,
                mimeType: 'text/html',
                to: emailRecipients,
                attachmentsPattern: attachments,
                attachLog: false
            )
            echo "   ✅ Email sent to: ${emailRecipients}"
        } catch (Exception e) {
            echo "   ⚠️ Email failed: ${e.message}"
        }
    }

    // =========================================
    // SEND TEAMS NOTIFICATION
    // =========================================
    if (sendTeams && teamsWebhookUrl) {
        try {
            def teamsStatusEmoji = isSuccess ? '✅' : '❌'
            def teamsHeaderStyle = isSuccess ? 'good' : 'attention'
            def securityStatusColor = securityStatus == 'PASSED' ? 'Good' : 'Attention'

            // Build Adaptive Card payload
            def payload = """{
    "type": "message",
    "attachments": [{
        "contentType": "application/vnd.microsoft.card.adaptive",
        "content": {
            "\$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
            "type": "AdaptiveCard",
            "version": "1.4",
            "body": [
                {
                    "type": "Container",
                    "style": "${teamsHeaderStyle}",
                    "bleed": true,
                    "items": [{
                        "type": "ColumnSet",
                        "columns": [
                            {"type": "Column", "width": "auto", "items": [{"type": "TextBlock", "text": "${teamsStatusEmoji}", "size": "ExtraLarge", "weight": "Bolder"}], "verticalContentAlignment": "Center"},
                            {"type": "Column", "width": "stretch", "items": [
                                {"type": "TextBlock", "text": "${fullName}", "weight": "Bolder", "size": "Large"},
                                {"type": "TextBlock", "text": "Build #${buildNumber} - ${buildResult}", "spacing": "None", "isSubtle": true}
                            ]}
                        ]
                    }],
                    "padding": "Default"
                },
                {
                    "type": "Container",
                    "items": [{"type": "FactSet", "facts": [
                        {"title": "Branch", "value": "${gitBranch}"},
                        {"title": "Duration", "value": "${buildDuration}"},
                        {"title": "Timestamp", "value": "${buildTimestamp}"}
                    ]}],
                    "separator": true,
                    "spacing": "Medium"
                },
                {
                    "type": "Container",
                    "items": [
                        {"type": "TextBlock", "text": "Security Scan", "weight": "Bolder", "size": "Medium"},
                        {"type": "ColumnSet", "columns": [
                            {"type": "Column", "width": "auto", "items": [
                                {"type": "TextBlock", "text": "${securityHigh}", "size": "ExtraLarge", "weight": "Bolder", "color": "Attention", "horizontalAlignment": "Center"},
                                {"type": "TextBlock", "text": "HIGH", "size": "Small", "horizontalAlignment": "Center", "isSubtle": true}
                            ]},
                            {"type": "Column", "width": "auto", "items": [
                                {"type": "TextBlock", "text": "${securityMedium}", "size": "ExtraLarge", "weight": "Bolder", "color": "Warning", "horizontalAlignment": "Center"},
                                {"type": "TextBlock", "text": "MEDIUM", "size": "Small", "horizontalAlignment": "Center", "isSubtle": true}
                            ]},
                            {"type": "Column", "width": "auto", "items": [
                                {"type": "TextBlock", "text": "${securityLow}", "size": "ExtraLarge", "weight": "Bolder", "color": "Good", "horizontalAlignment": "Center"},
                                {"type": "TextBlock", "text": "LOW", "size": "Small", "horizontalAlignment": "Center", "isSubtle": true}
                            ]},
                            {"type": "Column", "width": "stretch", "items": [
                                {"type": "TextBlock", "text": "Risk: ${riskLevel}", "weight": "Bolder"},
                                {"type": "TextBlock", "text": "Score: ${riskScore}", "isSubtle": true, "spacing": "None"}
                            ], "verticalContentAlignment": "Center"}
                        ]},
                        {"type": "TextBlock", "text": "${securityStatus == 'PASSED' ? 'All thresholds passed' : 'Thresholds exceeded'}", "color": "${securityStatusColor}", "weight": "Bolder", "spacing": "Small"}
                    ],
                    "separator": true,
                    "spacing": "Medium"
                },
                {
                    "type": "Container",
                    "items": [{"type": "FactSet", "facts": [{"title": "Deployed To", "value": "${deployPath}"}]}],
                    "separator": true,
                    "spacing": "Medium"
                }
            ],
            "actions": [
                {"type": "Action.OpenUrl", "title": "View Build", "url": "${buildUrl}", "style": "positive"},
                ${sonarQubeUrl ? '{"type": "Action.OpenUrl", "title": "SonarQube", "url": "' + sonarQubeUrl + '"},' : ''}
                {"type": "Action.OpenUrl", "title": "Console Log", "url": "${buildUrl}console"}
            ],
            "msteams": {"width": "Full"}
        }
    }]
}"""

            httpRequest(
                httpMode: 'POST',
                url: teamsWebhookUrl,
                contentType: 'APPLICATION_JSON',
                requestBody: payload,
                validResponseCodes: '200:299'
            )
            echo "   ✅ Teams notification sent"
        } catch (Exception e) {
            echo "   ⚠️ Teams notification failed: ${e.message}"
        }
    }

    echo "────────────────────────────────────────────────────────────"
    echo "📨 Notifications: COMPLETED"
    echo "════════════════════════════════════════════════════════════"
}
