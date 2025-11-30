#!/usr/bin/env groovy

/**
 * Run SonarQube analysis
 *
 * Usage in Jenkinsfile:
 *   sonarScan(
 *     projectKey: 'my-project',
 *     projectName: 'My Project',
 *     sources: 'src',
 *     language: 'java'
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
    def exclusions = config.exclusions ?: '**/node_modules/**,**/dist/**,**/target/**'
    def sonarServer = config.sonarServer ?: 'SonarQube'

    echo "📊 Running SonarQube analysis..."
    echo "Project: ${projectName} (${projectKey})"
    echo "Language: ${language}"

    withSonarQubeEnv(sonarServer) {
        if (language == 'java' || language == 'maven') {
            // Maven project - use mvn sonar:sonar
            sh """#!/bin/bash
                export SDKMAN_DIR="\$HOME/.sdkman"
                source "\$SDKMAN_DIR/bin/sdkman-init.sh"

                mvn sonar:sonar \
                    -Dsonar.projectKey=${projectKey} \
                    -Dsonar.projectName="${projectName}"
            """
        } else {
            // Non-Maven project - use sonar-scanner
            sh """
                sonar-scanner \
                    -Dsonar.projectKey=${projectKey} \
                    -Dsonar.projectName="${projectName}" \
                    -Dsonar.sources=${sources} \
                    -Dsonar.language=${language} \
                    -Dsonar.exclusions=${exclusions}
            """
        }
    }

    echo "✅ SonarQube analysis completed"
    echo "View results at: http://sonarqube:9000/dashboard?id=${projectKey}"
}
