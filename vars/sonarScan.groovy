#!/usr/bin/env groovy

/**
 * Run SonarQube analysis - Optimized for both Java and TypeScript/Angular
 *
 * Features:
 * - 5 minute timeout protection (prevents stuck builds)
 * - Memory limits for scanner (prevents OOM)
 * - Optimized for TypeScript/Angular (skips node_modules properly)
 * - Jenkins SonarQube badge integration
 * - Explicit credential handling via withCredentials
 *
 * Usage:
 *   sonarScan(
 *     projectKey: 'ADXSIP-Frontend',
 *     projectName: 'ADXSIP Frontend',
 *     sources: 'src',
 *     language: 'ts'
 *   )
 */
def call(Map config = [:]) {
    // Required parameters
    if (!config.projectKey) {
        error "projectKey is required for SonarQube scan"
    }

    def projectKey = config.projectKey
    def projectName = config.projectName ?: config.projectKey
    def sources = config.sources ?: 'src'
    def language = config.language ?: 'java'
    def sonarServer = config.sonarServer ?: 'SonarQube'
    def credentialId = config.credentialId ?: 'sonarqube-token'

    // SonarQube URL
    def sonarUrl = config.sonarUrl ?: 'http://192.168.1.136:9000'

    // Configurable limits
    def timeoutMinutes = config.timeout ?: 5
    def maxMemory = config.maxMemory ?: '1g'
    def nodeMaxMemory = config.nodeMaxMemory ?: 2048

    // Exclusions
    def exclusions = config.exclusions ?: '**/node_modules/**,**/dist/**,**/target/**,**/build/**,**/.angular/**'
    def testExclusions = config.testExclusions ?: '**/*.spec.ts,**/*.test.ts,**/test/**,**/tests/**'

    // Summary log (clean output)
    echo "════════════════════════════════════════════════════════════"
    echo "📊 SonarQube Analysis: ${projectName}"
    echo "────────────────────────────────────────────────────────────"
    echo "   Project Key : ${projectKey}"
    echo "   Language    : ${language}"
    echo "   Sources     : ${sources}"
    echo "   Server      : ${sonarUrl}"
    echo "════════════════════════════════════════════════════════════"

    def scanSuccess = false
    def dashboardUrl = "${sonarUrl}/dashboard?id=${projectKey}"

    // Wrap in timeout
    timeout(time: timeoutMinutes, unit: 'MINUTES') {
        // Use withSonarQubeEnv for badge + withCredentials for token
        withSonarQubeEnv(sonarServer) {
            withCredentials([string(credentialsId: credentialId, variable: 'SONAR_TOKEN')]) {
                if (language == 'java' || language == 'maven') {
                    sh """#!/bin/bash
                        export SDKMAN_DIR="\$HOME/.sdkman"
                        source "\$SDKMAN_DIR/bin/sdkman-init.sh"

                        mvn -B -q sonar:sonar \
                            -Dsonar.projectKey=${projectKey} \
                            -Dsonar.projectName="${projectName}" \
                            -Dsonar.host.url=${sonarUrl} \
                            -Dsonar.token=\${SONAR_TOKEN}
                    """
                } else if (language in ['ts', 'typescript', 'angular', 'js', 'javascript']) {
                    sh """#!/bin/bash
                        export SONAR_SCANNER_OPTS="-Xmx${maxMemory}"
                        export NODE_OPTIONS="--max-old-space-size=${nodeMaxMemory}"

                        sonar-scanner \
                            -Dsonar.host.url=${sonarUrl} \
                            -Dsonar.token=\${SONAR_TOKEN} \
                            -Dsonar.projectKey=${projectKey} \
                            -Dsonar.projectName="${projectName}" \
                            -Dsonar.sources=${sources} \
                            -Dsonar.sourceEncoding=UTF-8 \
                            -Dsonar.exclusions="${exclusions}" \
                            -Dsonar.test.exclusions="${testExclusions}" \
                            -Dsonar.coverage.exclusions="${testExclusions}" \
                            -Dsonar.cpd.exclusions="**/node_modules/**,**/*.min.js,**/*.bundle.js" \
                            -Dsonar.javascript.node.maxspace=${nodeMaxMemory} \
                            -Dsonar.typescript.node.maxspace=${nodeMaxMemory}
                    """
                } else {
                    sh """#!/bin/bash
                        export SONAR_SCANNER_OPTS="-Xmx${maxMemory}"

                        sonar-scanner \
                            -Dsonar.host.url=${sonarUrl} \
                            -Dsonar.token=\${SONAR_TOKEN} \
                            -Dsonar.projectKey=${projectKey} \
                            -Dsonar.projectName="${projectName}" \
                            -Dsonar.sources=${sources} \
                            -Dsonar.exclusions=${exclusions}
                    """
                }
            }
        }
        scanSuccess = true
    }

    // Summary result
    echo "────────────────────────────────────────────────────────────"
    if (scanSuccess) {
        echo "✅ SonarQube: PASSED"
    } else {
        echo "❌ SonarQube: FAILED"
    }
    echo "   Dashboard: ${dashboardUrl}"
    echo "════════════════════════════════════════════════════════════"

    // Return useful data for notifications
    return [
        success: scanSuccess,
        projectKey: projectKey,
        dashboardUrl: dashboardUrl
    ]
}
