pipeline {
    agent any

    tools {
        // Change these to your Jenkins tool names
        jdk 'JDK25'
        maven 'Maven3'
    }

    options {
        timestamps()
        ansiColor('xterm')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    parameters {
        string(name: 'BROWSER', defaultValue: 'chrome', description: 'Browser from config override')
        string(name: 'BASE_URL', defaultValue: '', description: 'Optional URL override')
        string(name: 'RETRY_COUNT', defaultValue: '1', description: 'Retry count override')
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: 'Skip test execution')
    }

    environment {
        MAVEN_OPTS = '-Xmx1g'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                script {
                    def mvnCmd = "mvn -B clean test -Dsurefire.suiteXmlFiles=testng.xml -Dbrowser=${params.BROWSER} -Dretry.count=${params.RETRY_COUNT}"
                    if (params.BASE_URL?.trim()) {
                        mvnCmd += " -Durl=${params.BASE_URL}"
                    }
                    if (params.SKIP_TESTS) {
                        mvnCmd = "mvn -B clean install -DskipTests"
                    }
                    bat mvnCmd
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'target/**/*.log, target/**/*.html, target/**/*.json', allowEmptyArchive: true
            junit testResults: 'target/surefire-reports/*.xml, target/failsafe-reports/*.xml', allowEmptyResults: true
        }
        success {
            echo 'Build and test execution completed successfully.'
        }
        failure {
            echo 'Build failed. Check surefire/cucumber reports in archived artifacts.'
        }
    }
}