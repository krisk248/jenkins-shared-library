# TTS Jenkins Shared Library

Centralized Jenkins Shared Library for TTS projects. Provides reusable pipeline functions for building, testing, scanning, and deploying applications.

**Last Updated:** December 2025

---

## Quick Start

```groovy
@Library('jenkins-shared-library') _

pipeline {
    agent { label 'build-agent' }

    stages {
        stage('Build') {
            steps {
                buildAngular(
                    buildDir: '/tts/ttsbuild/PROJECT/frontend',
                    baseHref: '/APP-NAME/'
                )
            }
        }

        stage('SonarQube') {
            steps {
                sonarScan(
                    projectKey: 'PROJECT-Frontend',
                    projectName: 'PROJECT Frontend',
                    language: 'ts'
                )
            }
        }
    }

    post {
        always {
            sendBuildNotification(
                project: 'PROJECT',
                component: 'Frontend',
                email: 'developer@ttsme.com',
                teams: true
            )
        }
    }
}
```

---

## Available Functions

### 1. `buildAngular`

Build Angular projects with automatic Node.js detection.

```groovy
buildAngular(
    buildDir: '/tts/ttsbuild/ADXSIP/frontend',
    baseHref: '/TTS-CAP/',           // Auto-generates ng build command
    nodeVersion: '20',                // Optional - uses system Node first
    installCommand: 'npm ci',
    updateCode: false                 // Skip git pull
)
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `buildDir` | workspace | Build directory path |
| `baseHref` | '' | Angular base href (auto-generates build command) |
| `buildCommand` | 'npm run build' | Custom build command |
| `nodeVersion` | '20' | Node version (fallback if system Node unavailable) |
| `installCommand` | 'npm ci' | Dependency install command |
| `updateCode` | true | Run git pull before build |
| `distDir` | 'dist' | Output directory |

**Node.js Detection:** Uses system Node first, falls back to nvm if needed.

---

### 2. `buildJavaMaven`

Build Java/Maven projects with configurable Java version.

```groovy
buildJavaMaven(
    buildDir: '/tts/ttsbuild/ADXSIP/backend',
    mavenGoals: 'clean install',
    javaVersion: '17',
    skipTests: true,
    gitPull: false
)
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `buildDir` | workspace | Build directory path |
| `mavenGoals` | 'clean package' | Maven goals |
| `javaVersion` | '17' | Java version (8, 11, 17) |
| `skipTests` | false | Skip tests |
| `mavenOpts` | '-Xmx2048m' | JVM options |
| `gitPull` | true | Run git pull before build |

---

### 3. `securityScan`

Run security scanning with Trivy, Semgrep, and TruffleHog. Generates PDF/HTML reports.

```groovy
def result = securityScan(
    configFile: 'adxsip-backend.yaml',
    buildNumber: env.BUILD_NUMBER
)

// Access results
env.SECURITY_HIGH = result.high
env.SECURITY_MEDIUM = result.medium
env.SECURITY_LOW = result.low
env.RISK_LEVEL = result.riskLevel
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `configFile` | required | YAML config file name |
| `buildNumber` | env.BUILD_NUMBER | Build number for report naming |

**Reports saved to:** `/tts/securityreports/{PROJECT}/{BUILD_NUMBER}/`

---

### 4. `sonarScan`

Run SonarQube code quality analysis with proper badge support.

```groovy
def sonarResult = sonarScan(
    projectKey: 'ADXSIP-Backend',
    projectName: 'ADXSIP Backend',
    language: 'java',                 // or 'ts' for TypeScript
    sources: 'src'
)

env.SONARQUBE_URL = sonarResult.dashboardUrl
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `projectKey` | required | SonarQube project key |
| `projectName` | projectKey | Display name |
| `language` | 'java' | Language: java, ts, js, py |
| `sources` | 'src' | Source directories |
| `credentialId` | 'sonarqube-token' | Jenkins credential ID |
| `sonarServer` | 'SonarQube' | Jenkins SonarQube server name |

**Badge Support:** Uses `withSonarQubeEnv` + `withCredentials` for proper Jenkins badge integration.

---

### 5. `sendBuildNotification`

Send professional HTML email and Teams Adaptive Card notifications.

```groovy
sendBuildNotification(
    project: 'ADXSIP',
    component: 'Backend',
    email: 'developer@ttsme.com',
    teams: true,
    branch: 'main',
    sonarQubeUrl: env.SONARQUBE_URL,
    deployPath: '/tts/output/app.war',
    securityHigh: env.SECURITY_HIGH,
    securityMedium: env.SECURITY_MEDIUM,
    securityLow: env.SECURITY_LOW,
    riskLevel: env.RISK_LEVEL,
    riskScore: env.RISK_SCORE,
    securityStatus: env.SECURITY_STATUS,
    attachments: 'security-reports/*.pdf,security-reports/*.html'
)
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `project` | required | Project name |
| `component` | required | Component (Frontend/Backend) |
| `email` | required | Recipient email |
| `teams` | false | Send Teams notification |
| `teamsWebhook` | env.TEAMS_WEBHOOK_URL | Teams webhook URL |
| `sonarQubeUrl` | '' | SonarQube dashboard URL |
| `attachments` | '' | Files to attach to email |
| `securityHigh/Medium/Low` | '0' | Security finding counts |

**Features:**
- Professional HTML email with TTS DevSecOps branding
- Security scan results with colored indicators
- PDF/HTML report attachments
- Teams Adaptive Card with action buttons

---

### 6. `deployToLocalDir`

Deploy artifacts to local directory.

```groovy
deployToLocalDir(
    source: "${BUILD_DIR}/target/app.war",
    destination: '/tts/outputttsbuild/PROJECT'
)
```

---

### 7. `cleanupOldReports`

Clean up old security reports.

```groovy
cleanupOldReports(
    reportDir: '/tts/securityreports/PROJECT',
    retentionDays: 30
)
```

---

## Example Pipelines

### Frontend (Angular)

```groovy
@Library('jenkins-shared-library') _

pipeline {
    agent { label 'build-agent' }

    environment {
        PROJECT_NAME = 'ADXSIP'
        COMPONENT = 'Frontend'
        BUILD_DIR = '/tts/ttsbuild/ADXSIP/tts-uae-adx-sip-clientside'
        OUTPUT_DIR = '/tts/outputttsbuild/ADXSIP/tts-uae-adx-sip-clientside'
        REPORT_DIR = '/tts/securityreports/ADXSIP'
        GIT_BRANCH = 'main'
        BASE_HREF = '/TTS-CAP/'
        DEVELOPER_EMAIL = 'developer@ttsme.com'
    }

    stages {
        stage('Git Pull') {
            steps {
                sh "cd ${BUILD_DIR} && git checkout ${GIT_BRANCH} && git pull"
            }
        }

        stage('Security Scan') {
            steps {
                script {
                    def result = securityScan(configFile: 'adxsip-frontend.yaml', buildNumber: env.BUILD_NUMBER)
                    env.SECURITY_HIGH = result.high?.toString() ?: '0'
                    env.SECURITY_MEDIUM = result.medium?.toString() ?: '0'
                    env.SECURITY_LOW = result.low?.toString() ?: '0'
                }
            }
        }

        stage('SonarQube') {
            steps {
                dir(BUILD_DIR) {
                    script {
                        def sonarResult = sonarScan(projectKey: 'ADXSIP-Frontend', projectName: 'ADXSIP Frontend', language: 'ts')
                        env.SONARQUBE_URL = sonarResult.dashboardUrl
                    }
                }
            }
        }

        stage('Build') {
            steps {
                buildAngular(buildDir: BUILD_DIR, baseHref: BASE_HREF, updateCode: false)
            }
        }

        stage('Deploy') {
            steps {
                sh "mkdir -p ${OUTPUT_DIR} && rm -rf ${OUTPUT_DIR}/* && cp -r ${BUILD_DIR}/dist/* ${OUTPUT_DIR}/"
            }
        }
    }

    post {
        always {
            sendBuildNotification(
                project: PROJECT_NAME, component: COMPONENT, email: DEVELOPER_EMAIL, teams: true,
                branch: GIT_BRANCH, sonarQubeUrl: env.SONARQUBE_URL, deployPath: OUTPUT_DIR,
                securityHigh: env.SECURITY_HIGH, securityMedium: env.SECURITY_MEDIUM, securityLow: env.SECURITY_LOW
            )
        }
    }
}
```

### Backend (Java/Maven)

```groovy
@Library('jenkins-shared-library') _

pipeline {
    agent { label 'build-agent' }

    environment {
        PROJECT_NAME = 'ADXSIP'
        COMPONENT = 'Backend'
        BUILD_DIR = '/tts/ttsbuild/ADXSIP/tts-uae-adx-sip-serverside'
        OUTPUT_DIR = '/tts/outputttsbuild/ADXSIP/tts-uae-adx-sip-serverside'
        GIT_BRANCH = 'master'
        JAVA_VERSION = '17'
        DEVELOPER_EMAIL = 'developer@ttsme.com'
    }

    stages {
        stage('Git Pull') {
            steps {
                sh "cd ${BUILD_DIR} && git checkout ${GIT_BRANCH} && git pull"
            }
        }

        stage('Security Scan') {
            steps {
                script {
                    def result = securityScan(configFile: 'adxsip-backend.yaml', buildNumber: env.BUILD_NUMBER)
                    env.SECURITY_HIGH = result.high?.toString() ?: '0'
                }
            }
        }

        stage('SonarQube') {
            steps {
                dir(BUILD_DIR) {
                    script {
                        def sonarResult = sonarScan(projectKey: 'ADXSIP-Backend', projectName: 'ADXSIP Backend', language: 'java')
                        env.SONARQUBE_URL = sonarResult.dashboardUrl
                    }
                }
            }
        }

        stage('Build') {
            steps {
                buildJavaMaven(buildDir: BUILD_DIR, mavenGoals: 'clean install', javaVersion: JAVA_VERSION, gitPull: false)
            }
        }

        stage('Deploy') {
            steps {
                deployToLocalDir(source: "${BUILD_DIR}/target/ADXSIP.war", destination: OUTPUT_DIR)
            }
        }
    }

    post {
        always {
            sendBuildNotification(
                project: PROJECT_NAME, component: COMPONENT, email: DEVELOPER_EMAIL, teams: true,
                branch: GIT_BRANCH, sonarQubeUrl: env.SONARQUBE_URL, deployPath: "${OUTPUT_DIR}/ADXSIP.war",
                securityHigh: env.SECURITY_HIGH, securityMedium: env.SECURITY_MEDIUM, securityLow: env.SECURITY_LOW
            )
        }
    }
}
```

---

## Folder Structure

```
jenkins-shared-library/
├── vars/                              # Pipeline functions
│   ├── buildAngular.groovy            # Angular build with baseHref
│   ├── buildJavaMaven.groovy          # Java/Maven build
│   ├── securityScan.groovy            # Security scanning
│   ├── sonarScan.groovy               # SonarQube analysis
│   ├── sendBuildNotification.groovy   # Email + Teams notifications
│   ├── deployToLocalDir.groovy        # Local deployment
│   └── cleanupOldReports.groovy       # Report cleanup
├── resources/
│   └── templates/
│       ├── email-build-report.html    # HTML email template
│       └── teams-adaptive-card.json   # Teams Adaptive Card template
├── examples/                          # Example Jenkinsfiles
└── README.md
```

---

## Jenkins Configuration

### Required Credentials

| Credential ID | Type | Description |
|---------------|------|-------------|
| `sonarqube-token` | Secret text | SonarQube authentication token |
| `github-pat` | Username/Password | GitHub access token |

### Required Plugins

- SonarQube Scanner
- Email Extension
- Credentials Binding

### Library Configuration (Jenkins > Manage > System)

```
Name: jenkins-shared-library
Default version: main
Load implicitly: Yes
Repository URL: https://github.com/YOUR_ORG/jenkins-shared-library.git
Credentials: github-pat
```

---

## Troubleshooting

### SonarQube "Not authorized" Error
1. Verify token in Jenkins credentials (`sonarqube-token`)
2. Test token: `curl -u YOUR_TOKEN: http://SONAR_HOST/api/authentication/validate`
3. Regenerate token in SonarQube if expired

### Node.js "version not installed" Error
- Script now auto-detects system Node first
- Falls back to nvm only if system Node unavailable

### Email Not Received
1. Check Jenkins SMTP configuration (Manage Jenkins > System)
2. Verify recipient email address
3. Check spam folder

---

## Support

**TTS DevOps Team** | December 2025
