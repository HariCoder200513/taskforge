// TaskForge CI/CD — runs on every push (GitHub webhook / multibranch scan).
// Stages: build -> test (Testcontainers/Postgres) -> docker build -> push to Docker Hub.
//
// Runs on the Jenkins node directly (`agent any`). The node must have JDK 21,
// Maven, Git and Docker installed and the `jenkins` user in the `docker` group
// — the EC2 bootstrap in infra/jenkins-ec2.yaml provisions exactly that.
//
// Required Jenkins credentials:
//   - dockerhub-creds : Username/Password = Docker Hub username + access token
// Set DOCKERHUB_REPO below to your Docker Hub repository.

pipeline {
    agent any

    options {
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
    }

    environment {
        // The node's default Maven runs on Java 17; force JDK 21 for the build.
        JAVA_HOME = '/usr/lib/jvm/java-21-amazon-corretto'
        PATH = "/usr/lib/jvm/java-21-amazon-corretto/bin:${PATH}"
        MAVEN_ARGS = '-B -ntp'
        DOCKERHUB_REPO = 'haridockersabre/taskforge-backend'
        IMAGE_TAG = "${env.GIT_COMMIT ? env.GIT_COMMIT.take(7) : env.BUILD_NUMBER}"
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
                    // Integration tests spin up Postgres via Testcontainers (needs Docker).
                    sh 'mvn $MAVEN_ARGS verify'
                }
            }
            post {
                always {
                    junit testResults: 'backend/target/surefire-reports/*.xml',
                          allowEmptyResults: true
                }
            }
        }

        stage('Docker Build') {
            steps {
                dir('backend') {
                    sh 'docker build -t $DOCKERHUB_REPO:$IMAGE_TAG .'
                }
            }
        }

        stage('Docker Push') {
            steps {
                withCredentials([usernamePassword(
                        credentialsId: 'dockerhub-creds',
                        usernameVariable: 'DH_USER',
                        passwordVariable: 'DH_PASS')]) {
                    sh '''
                        echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin
                        docker push $DOCKERHUB_REPO:$IMAGE_TAG
                    '''
                }
            }
        }

        stage('Tag latest (main only)') {
            when { branch 'main' }
            steps {
                withCredentials([usernamePassword(
                        credentialsId: 'dockerhub-creds',
                        usernameVariable: 'DH_USER',
                        passwordVariable: 'DH_PASS')]) {
                    sh '''
                        echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin
                        docker tag  $DOCKERHUB_REPO:$IMAGE_TAG $DOCKERHUB_REPO:latest
                        docker push $DOCKERHUB_REPO:latest
                    '''
                }
            }
        }
    }

    post {
        always {
            sh 'docker logout || true'
        }
        cleanup {
            cleanWs()
        }
    }
}
