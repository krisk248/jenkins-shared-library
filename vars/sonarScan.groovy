#!/usr/bin/env groovy

/**
 * Run SonarQube analysis - Optimized for both Java and TypeScript/Angular
 *
 * Features:
 * - 5 minute timeout protection (prevents stuck builds)
 * - Memory limits for scanner (prevents OOM)
 * - Optimized for TypeScript/Angular (skips node_modules properly)
 * - Auto-generates sonar-project.properties in workspace (not in source)
 *
 * Usage for Java/Maven:
 *   sonarScan(
 *     projectKey: 'my-backend',
 *     projectName: 'My Backend',
 *     language: 'java'
 *   )
 *
 * Usage for TypeScript/Angular:
 *   sonarScan(
 *     projectKey: 'my-frontend',
 *     projectName: 'My Frontend',
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

    // SonarQube URL - use IP for build agent (not Docker hostname)
    def sonarUrl = config.sonarUrl ?: 'http://192.168.1.136:9000'

    // Configurable limits
    def timeoutMinutes = config.timeout ?: 5
    def maxMemory = config.maxMemory ?: '1g'
    def nodeMaxMemory = config.nodeMaxMemory ?: 2048

    // Exclusions
    def exclusions = config.exclusions ?: '**/node_modules/**,**/dist/**,**/target/**,**/build/**,**/.angular/**'
    def testExclusions = config.testExclusions ?: '**/*.spec.ts,**/*.test.ts,**/test/**,**/tests/**'

    echo "Running SonarQube analysis..."
    echo "Project: ${projectName} (${projectKey})"
    echo "Language: ${language}"
    echo "SonarQube URL: ${sonarUrl}"
    echo "Timeout: ${timeoutMinutes} minutes"

    // Wrap everything in timeout
    timeout(time: timeoutMinutes, unit: 'MINUTES') {
        withSonarQubeEnv(sonarServer) {
            if (language == 'java' || language == 'maven') {
                // Maven project - use mvn sonar:sonar (already optimized)
                echo "Using Maven SonarQube plugin..."
                sh """#!/bin/bash
                    export SDKMAN_DIR="\$HOME/.sdkman"
                    source "\$SDKMAN_DIR/bin/sdkman-init.sh"

                    mvn -B sonar:sonar \
                        -Dsonar.projectKey=${projectKey} \
                        -Dsonar.projectName="${projectName}" \
                        -Dsonar.host.url=${sonarUrl}
                """
            } else if (language == 'ts' || language == 'typescript' || language == 'angular' || language == 'js' || language == 'javascript') {
                // TypeScript/Angular/JavaScript - use optimized sonar-scanner
                echo "Using optimized sonar-scanner for ${language}..."

                // Run scanner with memory limits - pass all config via command line
                // No sonar-project.properties file needed in source code!
                sh """#!/bin/bash
                    echo "Starting sonar-scanner with memory limit ${maxMemory}..."

                    # Set memory limits
                    export SONAR_SCANNER_OPTS="-Xmx${maxMemory}"
                    export NODE_OPTIONS="--max-old-space-size=${nodeMaxMemory}"

                    # Run scanner with all parameters on command line
                    sonar-scanner \
                        -Dsonar.host.url=${sonarUrl} \
                        -Dsonar.projectKey=${projectKey} \
                        -Dsonar.projectName="${projectName}" \
                        -Dsonar.sources=${sources} \
                        -Dsonar.sourceEncoding=UTF-8 \
                        -Dsonar.exclusions="${exclusions}" \
                        -Dsonar.test.exclusions="${testExclusions}" \
                        -Dsonar.coverage.exclusions="${testExclusions}" \
                        -Dsonar.cpd.exclusions="**/node_modules/**,**/*.min.js,**/*.bundle.js" \
                        -Dsonar.javascript.node.maxspace=${nodeMaxMemory} \
                        -Dsonar.typescript.node.maxspace=${nodeMaxMemory} \
                        -Dsonar.cpd.js.minimumtokens=100 \
                        -Dsonar.cpd.ts.minimumtokens=100
                """
            } else {
                // Generic scanner for other languages
                echo "Using generic sonar-scanner..."
                sh """#!/bin/bash
                    export SONAR_SCANNER_OPTS="-Xmx${maxMemory}"

                    sonar-scanner \
                        -Dsonar.host.url=${sonarUrl} \
                        -Dsonar.projectKey=${projectKey} \
                        -Dsonar.projectName="${projectName}" \
                        -Dsonar.sources=${sources} \
                        -Dsonar.exclusions=${exclusions}
                """
            }
        }
    }

    echo "SonarQube analysis completed"
    echo "View results at: ${sonarUrl}/dashboard?id=${projectKey}"
}
