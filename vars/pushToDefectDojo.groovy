#!/usr/bin/env groovy

/**
 * Push security scan results to DefectDojo
 *
 * Supports importing:
 * - Trivy JSON results
 * - Semgrep JSON results
 * - TruffleHog JSON results
 *
 * Usage in Jenkinsfile:
 *   pushToDefectDojo(
 *     productName: 'ADXSIP',
 *     engagementName: 'CI/CD Security Scans',
 *     reportDir: '/tts/securityreports/ADXSIP',
 *     buildNumber: env.BUILD_NUMBER
 *   )
 */
def call(Map config = [:]) {
    // DefectDojo configuration
    def defectDojoUrl = config.defectDojoUrl ?: 'http://192.168.1.133:8080'
    def credentialId = config.credentialId ?: 'defectdojo-api-key'

    // Product and Engagement
    def productName = config.productName ?: 'ADXSIP'
    def engagementName = config.engagementName ?: 'CI/CD Security Scans'

    // Report location
    def reportDir = config.reportDir ?: '/tts/securityreports/ADXSIP'
    def buildNumber = config.buildNumber ?: env.BUILD_NUMBER ?: '1'

    // Build-specific report directory (raw JSON files are in /raw subfolder)
    def buildReportDir = "${reportDir}/${buildNumber}/raw"

    echo "════════════════════════════════════════════════════════════"
    echo "🛡️ Push to DefectDojo"
    echo "────────────────────────────────────────────────────────────"
    echo "   URL         : ${defectDojoUrl}"
    echo "   Product     : ${productName}"
    echo "   Engagement  : ${engagementName}"
    echo "   Report Dir  : ${buildReportDir}"
    echo "════════════════════════════════════════════════════════════"

    def importResults = [:]

    withCredentials([string(credentialsId: credentialId, variable: 'DD_API_KEY')]) {
        // Get or create engagement ID
        def engagementId = getOrCreateEngagement(defectDojoUrl, productName, engagementName)

        if (!engagementId) {
            echo "❌ Could not find or create engagement. Skipping DefectDojo import."
            return [success: false, error: 'Engagement not found']
        }

        echo "   Engagement ID: ${engagementId}"

        // Import Trivy results
        def trivyFile = "${buildReportDir}/trivy.json"
        if (fileExists(trivyFile)) {
            echo "   Importing Trivy results..."
            importResults.trivy = importScan(defectDojoUrl, engagementId, trivyFile, 'Trivy Scan')
        } else {
            echo "   Trivy file not found: ${trivyFile}"
        }

        // Import Semgrep results
        def semgrepFile = "${buildReportDir}/semgrep.json"
        if (fileExists(semgrepFile)) {
            echo "   Importing Semgrep results..."
            importResults.semgrep = importScan(defectDojoUrl, engagementId, semgrepFile, 'Semgrep JSON Report')
        } else {
            echo "   Semgrep file not found: ${semgrepFile}"
        }

        // Import TruffleHog results
        def trufflehogFile = "${buildReportDir}/trufflehog.json"
        if (fileExists(trufflehogFile)) {
            echo "   Importing TruffleHog results..."
            importResults.trufflehog = importScan(defectDojoUrl, engagementId, trufflehogFile, 'Trufflehog Scan')
        } else {
            echo "   TruffleHog file not found: ${trufflehogFile}"
        }

        // List available files for debugging
        def files = sh(script: "ls -la ${buildReportDir}/ 2>/dev/null || echo 'Directory not found'", returnStdout: true).trim()
        echo "   Available files in ${buildReportDir}:"
        echo "   ${files}"
    }

    echo "────────────────────────────────────────────────────────────"
    echo "✅ DefectDojo Import: COMPLETED"
    echo "   Dashboard: ${defectDojoUrl}/product/${productName}"
    echo "════════════════════════════════════════════════════════════"

    return [success: true, results: importResults]
}

/**
 * Get engagement ID by name, or create if not exists
 */
def getOrCreateEngagement(String baseUrl, String productName, String engagementName) {
    // First, get product ID
    def productResponse = sh(
        script: """
            curl -s -X GET "${baseUrl}/api/v2/products/?name=${java.net.URLEncoder.encode(productName, 'UTF-8')}" \
                -H "Authorization: Token \${DD_API_KEY}" \
                -H "Content-Type: application/json"
        """,
        returnStdout: true
    ).trim()

    def productId = null
    try {
        def productJson = readJSON text: productResponse
        if (productJson.results && productJson.results.size() > 0) {
            productId = productJson.results[0].id
            echo "   Found Product ID: ${productId}"
        }
    } catch (Exception e) {
        echo "   Warning: Could not parse product response: ${e.message}"
    }

    if (!productId) {
        echo "   Product '${productName}' not found in DefectDojo"
        return null
    }

    // Get engagement ID
    def engResponse = sh(
        script: """
            curl -s -X GET "${baseUrl}/api/v2/engagements/?product=${productId}&name=${java.net.URLEncoder.encode(engagementName, 'UTF-8')}" \
                -H "Authorization: Token \${DD_API_KEY}" \
                -H "Content-Type: application/json"
        """,
        returnStdout: true
    ).trim()

    try {
        def engJson = readJSON text: engResponse
        if (engJson.results && engJson.results.size() > 0) {
            return engJson.results[0].id
        }
    } catch (Exception e) {
        echo "   Warning: Could not parse engagement response: ${e.message}"
    }

    echo "   Engagement '${engagementName}' not found"
    return null
}

/**
 * Import a scan file to DefectDojo
 */
def importScan(String baseUrl, def engagementId, String filePath, String scanType) {
    def result = [success: false]

    try {
        def response = sh(
            script: """
                curl -s -X POST "${baseUrl}/api/v2/import-scan/" \
                    -H "Authorization: Token \${DD_API_KEY}" \
                    -F "engagement=${engagementId}" \
                    -F "scan_type=${scanType}" \
                    -F "file=@${filePath}" \
                    -F "active=true" \
                    -F "verified=false" \
                    -F "close_old_findings=false" \
                    -F "push_to_jira=false"
            """,
            returnStdout: true
        ).trim()

        def responseJson = readJSON text: response
        if (responseJson.test_id) {
            echo "      Imported: ${scanType} (Test ID: ${responseJson.test_id})"
            result.success = true
            result.testId = responseJson.test_id
        } else {
            echo "      Warning: ${scanType} import may have issues: ${response}"
        }
    } catch (Exception e) {
        echo "      Error importing ${scanType}: ${e.message}"
        result.error = e.message
    }

    return result
}
