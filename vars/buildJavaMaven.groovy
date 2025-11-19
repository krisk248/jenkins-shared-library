#!/usr/bin/env groovy

/**
 * Build Java/Maven project
 *
 * Usage in Jenkinsfile:
 *   buildJavaMaven(
 *     mavenGoals: 'clean package',
 *     javaVersion: '17',
 *     skipTests: false
 *   )
 */
def call(Map config = [:]) {
    // Default values
    def mavenGoals = config.mavenGoals ?: 'clean package'
    def javaVersion = config.javaVersion ?: '17'
    def skipTests = config.skipTests ?: false
    def mavenOpts = config.mavenOpts ?: '-Xmx2048m'

    echo "🔨 Building Java/Maven project with Java ${javaVersion}"
    echo "Maven goals: ${mavenGoals}"

    // Set Java version using SDKMAN
    sh """
        export SDKMAN_DIR="/home/jenkins-agent/.sdkman"
        source "\$SDKMAN_DIR/bin/sdkman-init.sh"
        sdk use java ${javaVersion}

        # Verify Java version
        java -version

        # Run Maven build
        export MAVEN_OPTS="${mavenOpts}"
        mvn ${mavenGoals} ${skipTests ? '-DskipTests' : ''}
    """

    echo "✅ Maven build completed successfully"
}
