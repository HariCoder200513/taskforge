// TaskForge CI/CD — runs on every push (GitHub webhook / multibranch scan).
// Stages: build -> test (Testcontainers/Postgres) -> docker build -> push to Docker Hub.
//
// Runs on the Jenkins node directly (`agent any`). The node must have JDK 21,
// Maven, Git and Docker installed and the `jenkins` user in the `docker` group
// — the EC2 bootstrap in infra/jenkins-ec2.yaml provisions exactly that.
//
// Required Jenkins credentials:
//   - dockerhub-creds    : Username/Password = Docker Hub username + access token
//   - taskforge-host-ssh : SSH Username with private key (username "ubuntu",
//                          key = the host's .pem) — used by the Deploy stage
// Set DOCKERHUB_REPO and DEPLOY_HOST below.
//
// The Deploy stage SSHes to the API host and does pull + restart. Runtime config
// (Neon creds etc.) lives ONLY on the host at /opt/taskforge/taskforge.env — never
// in this repo. See deploy/taskforge.env.example and docs/DEPLOYMENT.md.

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
        // API host for the Deploy stage. Public IP changes on stop/start — use an
        // Elastic IP (or a DNS name) to keep this stable.
        DEPLOY_HOST = '15.135.173.241'
        DEPLOY_USER = 'ubuntu'
        CONTAINER_NAME = 'taskforge-api'
        HOST_ENV_FILE = '/opt/taskforge/taskforge.env'
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

        stage('Deploy') {
            when { branch 'main' }
            steps {
                // SSH to the host and roll the container to the new image. Runtime
                // config comes from $HOST_ENV_FILE on the host (secrets stay there).
                withCredentials([sshUserPrivateKey(
                        credentialsId: 'taskforge-host-ssh',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USER')]) {
                    sh '''
                        ssh -i "$SSH_KEY" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
                            "$SSH_USER@$DEPLOY_HOST" \
                            "sudo docker pull $DOCKERHUB_REPO:$IMAGE_TAG \
                             && sudo docker rm -f $CONTAINER_NAME 2>/dev/null || true; \
                             sudo docker run -d --name $CONTAINER_NAME --restart unless-stopped \
                                 -p 80:8080 --env-file $HOST_ENV_FILE \
                                 $DOCKERHUB_REPO:$IMAGE_TAG \
                             && sudo docker image prune -f"
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
