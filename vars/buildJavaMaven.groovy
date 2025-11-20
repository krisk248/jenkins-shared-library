#!/usr/bin/env groovy

/**
 * Build Java/Maven project
 * Supports building in permanent directory (with git pull) or workspace
 *
 * Usage in Jenkinsfile:
 *   buildJavaMaven(
 *     buildDir: '/tts/ttsbuild/ADXSIP/tts-uae-adx-sip-serverside',
 *     mavenGoals: 'clean install',
 *     javaVersion: '17',
 *     skipTests: true
 *   )
 */
def call(Map config = [:]) {
    // Default values
    def buildDir = config.buildDir ?: ''  // If empty, build in current directory
    def mavenGoals = config.mavenGoals ?: 'clean package'
    def javaVersion = config.javaVersion ?: '17'
    def skipTests = config.skipTests ?: false
    def mavenOpts = config.mavenOpts ?: '-Xmx2048m'
    def gitPull = config.gitPull != false  // Pull by default if buildDir specified

    echo "🔨 Building Java/Maven project with Java ${javaVersion}"
    echo "Maven goals: ${mavenGoals}"

    if (buildDir) {
        echo "Build directory: ${buildDir}"

        // Build in permanent directory using cd (no @tmp pollution)
        sh """
            cd ${buildDir}

            # Update code if in permanent directory
            ${gitPull ? 'echo "📥 Updating code from Git..." && git pull' : ''}

            # Setup Java environment and build
            export SDKMAN_DIR="/home/jenkins-agent/.sdkman"
            source "\$SDKMAN_DIR/bin/sdkman-init.sh"
            sdk use java ${javaVersion}

            # Verify Java version
            java -version

            # Run Maven build
            export MAVEN_OPTS="${mavenOpts}"
            mvn ${mavenGoals} ${skipTests ? '-DskipTests' : ''}
        """
    } else {
        // Build in current workspace (code already checked out)
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
    }

    echo "✅ Maven build completed successfully"
}
