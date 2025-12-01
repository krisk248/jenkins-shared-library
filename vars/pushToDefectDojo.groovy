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
    // URL encode the product name (simple replacement for common chars)
    def encodedProductName = productName.replaceAll(' ', '%20')
    def encodedEngagementName = engagementName.replaceAll(' ', '%20').replaceAll('/', '%2F')

    // First, get product ID
    echo "   Fetching product: ${productName}..."
    def productResponse = sh(
        script: """
            curl -s -w "\\nHTTP_STATUS:%{http_code}" -X GET "${baseUrl}/api/v2/products/?name=${encodedProductName}" \
                -H "Authorization: Token \${DD_API_KEY}" \
                -H "Content-Type: application/json"
        """,
        returnStdout: true
    ).trim()

    // Check HTTP status
    def productStatusMatch = productResponse =~ /HTTP_STATUS:(\d+)$/
    def productStatus = productStatusMatch ? productStatusMatch[0][1] : "unknown"
    def productBody = productResponse.replaceAll(/\nHTTP_STATUS:\d+$/, '')

    echo "   Product API Response Status: ${productStatus}"

    if (productStatus != "200") {
        echo "   ERROR: DefectDojo API returned status ${productStatus}"
        echo "   Response: ${productBody.take(500)}"
        return null
    }

    def productId = null
    try {
        def productJson = readJSON text: productBody
        if (productJson.results && productJson.results.size() > 0) {
            productId = productJson.results[0].id
            echo "   Found Product ID: ${productId}"
        } else {
            echo "   Product search returned 0 results"
        }
    } catch (Exception e) {
        echo "   Warning: Could not parse product response: ${e.message}"
        echo "   Raw response: ${productBody.take(300)}"
    }

    if (!productId) {
        echo "   Product '${productName}' not found in DefectDojo"
        return null
    }

    // Get engagement ID
    echo "   Fetching engagement: ${engagementName}..."
    def engResponse = sh(
        script: """
            curl -s -w "\\nHTTP_STATUS:%{http_code}" -X GET "${baseUrl}/api/v2/engagements/?product=${productId}&name=${encodedEngagementName}" \
                -H "Authorization: Token \${DD_API_KEY}" \
                -H "Content-Type: application/json"
        """,
        returnStdout: true
    ).trim()

    def engStatusMatch = engResponse =~ /HTTP_STATUS:(\d+)$/
    def engStatus = engStatusMatch ? engStatusMatch[0][1] : "unknown"
    def engBody = engResponse.replaceAll(/\nHTTP_STATUS:\d+$/, '')

    echo "   Engagement API Response Status: ${engStatus}"

    try {
        def engJson = readJSON text: engBody
        if (engJson.results && engJson.results.size() > 0) {
            echo "   Found Engagement ID: ${engJson.results[0].id}"
            return engJson.results[0].id
        } else {
            echo "   Engagement search returned 0 results"
        }
    } catch (Exception e) {
        echo "   Warning: Could not parse engagement response: ${e.message}"
        echo "   Raw response: ${engBody.take(300)}"
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
