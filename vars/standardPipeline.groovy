#!/usr/bin/env groovy

/**
 * Standard TTS Build Pipeline
 *
 * This is a complete pipeline template that includes:
 * - Git checkout
 * - Build (Java/Maven or Angular)
 * - Security scanning
 * - SonarQube analysis
 * - Deployment
 * - Email notification
 *
 * Usage in Jenkinsfile:
 *   @Library('jenkins-shared-library') _
 *
 *   standardPipeline(
 *     buildType: 'maven',  // or 'angular'
 *     projectKey: 'my-project',
 *     deployPath: '/path/to/deploy'
 *   )
 */
def call(Map config = [:]) {
    // Required parameters
    if (!config.buildType) {
        error "buildType is required (maven or angular)"
    }

    def buildType = config.buildType
    def projectKey = config.projectKey ?: env.JOB_NAME.replaceAll('/', '-')
    def projectName = config.projectName ?: env.JOB_NAME

    pipeline {
        agent { label config.agentLabel ?: 'build-agent' }

        options {
            timestamps()
            timeout(time: 1, unit: 'HOURS')
            buildDiscarder(logRotator(numToKeepStr: '30'))
        }

        stages {
            stage('Checkout') {
                steps {
                    echo "📥 Checking out source code..."
                    checkout scm
                }
            }

            stage('Build') {
                steps {
                    script {
                        if (buildType == 'maven') {
                            buildJavaMaven(
                                mavenGoals: config.mavenGoals ?: 'clean package',
                                javaVersion: config.javaVersion ?: '17',
                                skipTests: config.skipTests ?: false
                            )
                        } else if (buildType == 'angular') {
                            buildAngular(
                                nodeVersion: config.nodeVersion ?: '20',
                                buildCommand: config.buildCommand ?: 'npm run build',
                                installCommand: config.installCommand ?: 'npm ci'
                            )
                        } else {
                            error "Unknown buildType: ${buildType}"
                        }
                    }
                }
            }

            stage('Security Scan') {
                steps {
                    script {
                        securityScan(
                            enableSemgrep: config.enableSemgrep != false,
                            enableTrivy: config.enableTrivy != false,
                            enableTruffleHog: config.enableTruffleHog != false
                        )
                    }
                }
            }

            stage('SonarQube Analysis') {
                when {
                    expression { config.skipSonar != true }
                }
                steps {
                    script {
                        sonarScan(
                            projectKey: projectKey,
                            projectName: projectName,
                            sources: config.sources ?: 'src',
                            language: buildType == 'maven' ? 'java' : 'ts'
                        )
                    }
                }
            }

            stage('Deploy') {
                when {
                    expression { config.deployPath != null }
                }
                steps {
                    script {
                        deployToNetworkShare(
                            source: config.deploySource ?: 'target/*.war',
                            destination: config.deployPath
                        )
                    }
                }
            }
        }

        post {
            always {
                script {
                    sendNotification(
                        status: currentBuild.currentResult,
                        recipients: config.recipients ?: env.DEVOPS_EMAIL
                    )
                }
            }
        }
    }
}
