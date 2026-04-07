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
                        sh 'mvn -f blackdrongo-selenium/pom.xml -B clean test'
                    } else {
                        bat 'mvn -f blackdrongo-selenium/pom.xml -B clean test'
                    }
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'blackdrongo-selenium/target/**/*.log, blackdrongo-selenium/target/**/*.html, blackdrongo-selenium/target/**/*.json', allowEmptyArchive: true
            junit testResults: 'blackdrongo-selenium/target/surefire-reports/*.xml, blackdrongo-selenium/target/failsafe-reports/*.xml', allowEmptyResults: true
        }
    }
}
