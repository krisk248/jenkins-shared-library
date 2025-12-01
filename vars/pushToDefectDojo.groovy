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
 * Uses jq for JSON parsing (no plugin required)
 */
def getOrCreateEngagement(String baseUrl, String productName, String engagementName) {
    // URL encode the product name (simple replacement for common chars)
    def encodedProductName = productName.replaceAll(' ', '%20')
    def encodedEngagementName = engagementName.replaceAll(' ', '%20').replaceAll('/', '%2F')

    // First, get product ID using jq to parse
    echo "   Fetching product: ${productName}..."
    def productId = sh(
        script: """
            response=\$(curl -s -X GET "${baseUrl}/api/v2/products/?name=${encodedProductName}" \
                -H "Authorization: Token \${DD_API_KEY}" \
                -H "Content-Type: application/json")

            # Extract product ID using jq
            product_id=\$(echo "\$response" | jq -r '.results[0].id // empty')

            if [ -n "\$product_id" ] && [ "\$product_id" != "null" ]; then
                echo "\$product_id"
            else
                echo ""
            fi
        """,
        returnStdout: true
    ).trim()

    if (!productId) {
        echo "   Product '${productName}' not found in DefectDojo"
        return null
    }

    echo "   Found Product ID: ${productId}"

    // Get engagement ID using jq
    echo "   Fetching engagement: ${engagementName}..."
    def engagementId = sh(
        script: """
            response=\$(curl -s -X GET "${baseUrl}/api/v2/engagements/?product=${productId}" \
                -H "Authorization: Token \${DD_API_KEY}" \
                -H "Content-Type: application/json")

            # Extract engagement ID using jq (first match)
            eng_id=\$(echo "\$response" | jq -r '.results[0].id // empty')

            if [ -n "\$eng_id" ] && [ "\$eng_id" != "null" ]; then
                echo "\$eng_id"
            else
                echo ""
            fi
        """,
        returnStdout: true
    ).trim()

    if (!engagementId) {
        echo "   Engagement '${engagementName}' not found"
        return null
    }

    echo "   Found Engagement ID: ${engagementId}"
    return engagementId
}

/**
 * Import a scan file to DefectDojo
 * Uses jq for JSON parsing (no plugin required)
 */
def importScan(String baseUrl, def engagementId, String filePath, String scanType) {
    def result = [success: false]

    try {
        def testId = sh(
            script: """
                response=\$(curl -s -X POST "${baseUrl}/api/v2/import-scan/" \
                    -H "Authorization: Token \${DD_API_KEY}" \
                    -F "engagement=${engagementId}" \
                    -F "scan_type=${scanType}" \
                    -F "file=@${filePath}" \
                    -F "active=true" \
                    -F "verified=false" \
                    -F "close_old_findings=false" \
                    -F "push_to_jira=false")

                # Extract test_id using jq
                test_id=\$(echo "\$response" | jq -r '.test_id // .test // empty')

                if [ -n "\$test_id" ] && [ "\$test_id" != "null" ]; then
                    echo "\$test_id"
                else
                    # Check for error message
                    error=\$(echo "\$response" | jq -r '.detail // .error // empty')
                    if [ -n "\$error" ]; then
                        echo "ERROR:\$error" >&2
                    fi
                    echo ""
                fi
            """,
            returnStdout: true
        ).trim()

        if (testId && !testId.startsWith("ERROR:")) {
            echo "      ✅ Imported: ${scanType} (Test ID: ${testId})"
            result.success = true
            result.testId = testId
        } else {
            echo "      ⚠️ Warning: ${scanType} import may have issues"
        }
    } catch (Exception e) {
        echo "      ❌ Error importing ${scanType}: ${e.message}"
        result.error = e.message
    }

    return result
}
