pipeline {
    agent any

    tools {
        maven 'Maven 3.9.14'
        // add jdk 'JDK25' only after configuring it in Global Tool Configuration
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
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
                    if (isUnix()) {
                        sh 'mvn -B clean test -Dsurefire.suiteXmlFiles=testng.xml'
                    } else {
                        bat 'mvn -B clean test -Dsurefire.suiteXmlFiles=testng.xml'
                    }
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'target/**/*.log, target/**/*.html, target/**/*.json', allowEmptyArchive: true
            junit testResults: 'target/surefire-reports/*.xml, target/failsafe-reports/*.xml', allowEmptyResults: true
        }
    }
}
