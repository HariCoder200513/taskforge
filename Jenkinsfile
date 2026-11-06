// Minimal CI for TaskForge — build + test on every push/PR.
// Scope (development order step 3): backend compile + integration tests
// (Testcontainers/PostgreSQL). Deployment stages (frontend build, npm audit,
// Netlify deploy, smoke tests) are added at step 14 (full Jenkins CI/CD).
//
// Agent prerequisite: the build runs inside a Maven+JDK21 container that needs
// access to a Docker daemon for Testcontainers. We mount the host Docker socket
// (Docker-outside-of-Docker) and use the host network so the JVM can reach the
// ephemeral Postgres container's published port. See docs/CI.md.

pipeline {
    agent {
        docker {
            image 'maven:3.9.9-eclipse-temurin-21'
            // --network host: JVM reaches Testcontainers' published ports on localhost
            // docker.sock: Testcontainers spawns sibling containers on the host daemon
            // named volume: cache the Maven repo between builds
            args '--network host ' +
                 '-v /var/run/docker.sock:/var/run/docker.sock ' +
                 '-v taskforge-m2:/root/.m2'
        }
    }

    options {
        timeout(time: 20, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
    }

    environment {
        MAVEN_ARGS = '-B -ntp'
    }

    stages {
        stage('Build') {
            steps {
                dir('backend') {
                    sh 'mvn $MAVEN_ARGS clean compile'
                }
            }
        }

        stage('Test') {
            steps {
                dir('backend') {
                    // Runs the Testcontainers migration/integration tests.
                    sh 'mvn $MAVEN_ARGS verify'
                }
            }
        }
    }

    post {
        always {
            junit testResults: 'backend/target/surefire-reports/*.xml',
                  allowEmptyResults: true
        }
        cleanup {
            cleanWs()
        }
    }
}
