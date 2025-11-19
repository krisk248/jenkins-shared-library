# TTS Jenkins Shared Library

This is the centralized Jenkins Shared Library for TTS projects. It provides reusable pipeline functions for building, testing, scanning, and deploying applications.

## 📚 Available Functions

### 1. `buildJavaMaven`
Build Java/Maven projects with configurable Java version.

**Usage:**
```groovy
buildJavaMaven(
    mavenGoals: 'clean package',
    javaVersion: '17',
    skipTests: false,
    mavenOpts: '-Xmx2048m'
)
```

**Parameters:**
- `mavenGoals` (default: 'clean package') - Maven goals to execute
- `javaVersion` (default: '17') - Java version to use (8 or 17)
- `skipTests` (default: false) - Skip running tests
- `mavenOpts` (default: '-Xmx2048m') - Maven JVM options

---

### 2. `buildAngular`
Build Angular projects with configurable Node.js version.

**Usage:**
```groovy
buildAngular(
    nodeVersion: '20',
    buildCommand: 'npm run build:prod',
    installCommand: 'npm ci',
    distDir: 'dist'
)
```

**Parameters:**
- `nodeVersion` (default: '20') - Node.js version to use
- `buildCommand` (default: 'npm run build') - Build command
- `installCommand` (default: 'npm ci') - Install command
- `distDir` (default: 'dist') - Output directory

---

### 3. `securityScan`
Run comprehensive security scanning with Semgrep, Trivy, and TruffleHog.

**Usage:**
```groovy
securityScan(
    enableSemgrep: true,
    enableTrivy: true,
    enableTruffleHog: true,
    outputDir: 'security-reports'
)
```

**Parameters:**
- `enableSemgrep` (default: true) - Run Semgrep SAST scan
- `enableTrivy` (default: true) - Run Trivy dependency scan
- `enableTruffleHog` (default: true) - Run TruffleHog secret scan
- `outputDir` (default: 'security-reports') - Output directory for reports

---

### 4. `sonarScan`
Run SonarQube code quality analysis.

**Usage:**
```groovy
sonarScan(
    projectKey: 'my-project',
    projectName: 'My Project',
    sources: 'src',
    language: 'java'
)
```

**Parameters:**
- `projectKey` (required) - SonarQube project key
- `projectName` (default: projectKey) - Display name
- `sources` (default: 'src') - Source directories
- `language` (default: 'java') - Project language

---

### 5. `deployToNetworkShare`
Deploy artifacts to network share.

**Usage:**
```groovy
deployToNetworkShare(
    source: 'target/*.war',
    destination: '/path/to/deploy',
    credentialsId: 'network-share-credentials'
)
```

**Parameters:**
- `source` (required) - Source files to deploy
- `destination` (required) - Destination path
- `credentialsId` (default: 'network-share-credentials') - Credentials ID

---

### 6. `sendNotification`
Send email notification with build status.

**Usage:**
```groovy
sendNotification(
    status: currentBuild.currentResult,
    recipients: 'devops@ttsme.com'
)
```

**Parameters:**
- `status` (default: currentBuild.currentResult) - Build status
- `recipients` (default: DEVOPS_EMAIL) - Email recipients

---

### 7. `standardPipeline`
Complete pipeline template with all stages.

**Usage:**
```groovy
@Library('jenkins-shared-library') _

standardPipeline(
    buildType: 'maven',  // or 'angular'
    projectKey: 'my-project',
    deployPath: '/path/to/deploy'
)
```

**Parameters:**
- `buildType` (required) - 'maven' or 'angular'
- `projectKey` (default: JOB_NAME) - SonarQube project key
- `deployPath` (optional) - Deployment path
- `agentLabel` (default: 'build-agent') - Jenkins agent label

---

## 🚀 Using in Jenkinsfile

### Simple Jenkinsfile (Using standardPipeline)

```groovy
@Library('jenkins-shared-library') _

standardPipeline(
    buildType: 'maven',
    projectKey: 'tts-backend',
    javaVersion: '17',
    deployPath: '/tts/outputttsbuild/backend'
)
```

### Custom Jenkinsfile (Using individual functions)

```groovy
@Library('jenkins-shared-library') _

pipeline {
    agent { label 'build-agent' }

    stages {
        stage('Build') {
            steps {
                script {
                    buildJavaMaven(
                        mavenGoals: 'clean package',
                        javaVersion: '17'
                    )
                }
            }
        }

        stage('Security') {
            steps {
                script {
                    securityScan()
                }
            }
        }

        stage('Quality') {
            steps {
                script {
                    sonarScan(
                        projectKey: 'my-project'
                    )
                }
            }
        }
    }

    post {
        always {
            sendNotification()
        }
    }
}
```

---

## 📦 Configuration in Jenkins

This library is automatically loaded in Jenkins via JCasC configuration:

```yaml
unclassified:
  globalLibraries:
    libraries:
      - name: "jenkins-shared-library"
        defaultVersion: "main"
        implicit: true
        allowVersionOverride: true
        retriever:
          modernSCM:
            scm:
              git:
                remote: "https://github.com/YOUR_ORG/jenkins-shared-library.git"
                credentialsId: "github-pat"
```

---

## 🔧 Development

### Folder Structure

```
jenkins-shared-library/
├── vars/                          # Pipeline functions
│   ├── buildJavaMaven.groovy
│   ├── buildAngular.groovy
│   ├── securityScan.groovy
│   ├── sonarScan.groovy
│   ├── deployToNetworkShare.groovy
│   ├── sendNotification.groovy
│   └── standardPipeline.groovy
├── resources/                     # Static resources
└── README.md                      # This file
```

### Adding New Functions

1. Create new `.groovy` file in `vars/` directory
2. Define the function with `def call(Map config = [:])` signature
3. Add documentation in this README
4. Commit and push to repository

---

## 📞 Support

**Issues or questions?**
Contact TTS DevOps Team

**Built with ❤️ by TTS DevOps Team**
